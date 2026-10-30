package com.metrolist.tablefor

import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Locale

/** One rating per group, rater and target, so submitting again overwrites instead of duplicating. */
internal fun ratingDocumentId(groupId: String, raterId: String, targetId: String): String =
    "${groupId}_${raterId}_${targetId}"

/**
 * Fields written to `users/{uid}` by a profile save. Never trustRating or
 * totalRatings: those will be computed on the server (F10).
 */
internal fun profileToFirestore(userId: String, profile: UserProfile): Map<String, Any> = mapOf(
    "id" to userId,
    "name" to profile.name,
    "bio" to profile.bio,
    "location" to profile.location,
    "diets" to profile.diets,
    "cuisinePreferences" to profile.cuisinePreferences,
    "budget" to profile.budget
)

/** A profile read from a `users/{uid}` document's fields. Missing fields are blank, never sample data. */
internal fun profileFromFirestore(userId: String, data: Map<String, Any?>): UserProfile = UserProfile(
    id = userId,
    name = data["name"] as? String ?: "",
    bio = data["bio"] as? String ?: "",
    location = data["location"] as? String ?: "",
    diets = (data["diets"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
    cuisinePreferences = (data["cuisinePreferences"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
    budget = data["budget"] as? String ?: "",
    trustRating = (data["trustRating"] as? Number)?.toFloat() ?: 0f,
    totalRatings = (data["totalRatings"] as? Number)?.toInt() ?: 0
)

/**
 * Fields for the merge write to `availability/{uid}`: sets one slot and deletes
 * [staleKeys], leaving the other saved slots as they are.
 */
internal fun availabilityUpdate(slotKey: String, isAvailable: Boolean, staleKeys: Set<String>): Map<String, Any> =
    mapOf("slots" to staleKeys.associateWith<String, Any> { FieldValue.delete() } + (slotKey to isAvailable))

/** Your own message's delivery: while Firestore has a pending write for it, it's only on this phone. */
internal fun ownMessageDelivery(hasPendingWrites: Boolean): Delivery =
    if (hasPendingWrites) Delivery.Sending else Delivery.Sent

class FirebaseTableForRepository : TableForRepository {

    private val db: FirebaseFirestore? by lazy {
        try {
            FirebaseFirestore.getInstance()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private val auth: FirebaseAuth? by lazy {
        try {
            FirebaseAuth.getInstance()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /** The signed-in user, or null. Without one, nothing is read from or written to Firestore. */
    private val currentUserId: String?
        get() = auth?.currentUser?.uid

    /** Firestore and the user ID for a write; throws when Firebase is missing or nobody is signed in. */
    private fun requireSignedIn(): Pair<FirebaseFirestore, String> {
        val firestore = db ?: throw IllegalStateException("Firebase is not available")
        val uid = currentUserId ?: throw IllegalStateException("Not signed in")
        return firestore to uid
    }

    override val isFirebaseAvailable: Boolean
        get() = db != null && auth != null

    // --- Authentication ---
    override suspend fun signIn(): String? {
        val a = auth ?: return null
        if (db == null) return null
        return try {
            if (a.currentUser == null) {
                a.signInAnonymously().await()
            }
            a.currentUser?.uid
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Anonymous sign-in failed", e)
            null
        }
    }

    // --- Realtime Chat ---
    override fun getChatMessagesFlow(groupId: String): Flow<List<ChatMessage>> = callbackFlow {
        val firestore = db
        if (firestore == null || currentUserId == null) {
            close()
            return@callbackFlow
        }

        val listenerRegistration = firestore.collection("groups")
            .document(groupId)
            .collection("messages")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            // With metadata changes, a message is reported again once the server has it,
            // so its delivery moves from Sending to Sent.
            .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "Chat listener failed", error)
                    close(error)
                    return@addSnapshotListener
                }

                // Parsing errors (a field of the wrong type) end the stream instead of crashing.
                try {
                    val messages = snapshot?.documents?.mapNotNull { doc ->
                        val senderId = doc.getString("senderId") ?: ""
                        // A missing name reads as blank; the chat shows it as "Group member".
                        val senderName = doc.getString("senderName") ?: ""
                        val text = doc.getString("text") ?: ""
                        val timeDate = doc.getTimestamp("timestamp")?.toDate()
                        val formattedTime: String = if (timeDate != null) {
                            SimpleDateFormat("h:mm a", Locale.getDefault()).format(timeDate)
                        } else ""

                        val isMe = senderId == currentUserId
                        ChatMessage(
                            id = doc.id,
                            senderName = senderName,
                            text = text,
                            timestamp = formattedTime,
                            isMe = isMe,
                            delivery = if (isMe) ownMessageDelivery(doc.metadata.hasPendingWrites()) else null
                        )
                    } ?: emptyList()

                    trySend(messages)
                } catch (e: Exception) {
                    close(e)
                }
            }

        awaitClose { listenerRegistration.remove() }
    }

    override suspend fun sendMessage(groupId: String, text: String, senderName: String) {
        val firestore = db ?: return
        val uid = currentUserId ?: return
        try {
            val messageData = mapOf(
                "senderId" to uid,
                "senderName" to senderName,
                "text" to text,
                "timestamp" to Timestamp.now()
            )
            firestore.collection("groups")
                .document(groupId)
                .collection("messages")
                .add(messageData)
                .await()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // --- Schedule Availability ---
    override suspend fun updateAvailability(slotKey: String, isAvailable: Boolean, staleKeys: Set<String>) {
        val (firestore, uid) = requireSignedIn()
        // A merge write, not a transaction: a transaction needs the server, while
        // Firestore queues this write on the phone, so toggles work offline.
        firestore.collection("availability")
            .document(uid)
            .set(availabilityUpdate(slotKey, isAvailable, staleKeys), SetOptions.merge())
            .await()
    }

    override fun getAvailabilityFlow(userId: String): Flow<Map<String, Boolean>> = callbackFlow {
        val firestore = db
        if (firestore == null || currentUserId == null) {
            close()
            return@callbackFlow
        }

        val listener = firestore.collection("availability").document(userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "Availability listener failed", error)
                    close(error)
                    return@addSnapshotListener
                }
                try {
                    @Suppress("UNCHECKED_CAST")
                    val slots = snapshot?.get("slots") as? Map<String, Boolean> ?: emptyMap()
                    trySend(slots)
                } catch (e: Exception) {
                    close(e)
                }
            }
        awaitClose { listener.remove() }
    }

    // --- User Profile ---
    override suspend fun saveProfile(profile: UserProfile) {
        val (firestore, uid) = requireSignedIn()
        // merge() keeps fields this save doesn't write, such as server-computed ratings.
        firestore.collection("users")
            .document(uid)
            .set(profileToFirestore(uid, profile), SetOptions.merge())
            .await()
    }

    override fun getUserProfileFlow(userId: String): Flow<UserProfile?> = callbackFlow {
        val firestore = db
        if (firestore == null || currentUserId == null) {
            close()
            return@callbackFlow
        }

        val listener = firestore.collection("users").document(userId)
            .addSnapshotListener { snapshot, error ->
                // An error must end the stream, not look like "no profile yet".
                if (error != null) {
                    Log.w(TAG, "Profile listener failed", error)
                    close(error)
                    return@addSnapshotListener
                }
                try {
                    if (snapshot != null && snapshot.exists()) {
                        trySend(profileFromFirestore(userId, snapshot.data ?: emptyMap()))
                    } else {
                        trySend(null)
                    }
                } catch (e: Exception) {
                    close(e)
                }
            }
        awaitClose { listener.remove() }
    }

    // --- Member Ratings ---
    override suspend fun submitRatings(groupId: String, ratings: List<MemberRating>) {
        val (firestore, raterId) = requireSignedIn()
        val batch = firestore.batch()
        for (rating in ratings) {
            val ratingData = mapOf(
                "raterId" to raterId,
                "targetMemberId" to rating.memberId,
                "memberName" to rating.memberName,
                "groupId" to groupId,
                "punctuality" to rating.punctuality,
                "friendliness" to rating.friendliness,
                "reliability" to rating.reliability,
                "notes" to rating.notes,
                "timestamp" to Timestamp.now()
            )
            val ref = firestore.collection("ratings").document(ratingDocumentId(groupId, raterId, rating.memberId))
            batch.set(ref, ratingData)
        }
        batch.commit().await()
    }

    private companion object {
        const val TAG = "TableForRepository"
    }
}
