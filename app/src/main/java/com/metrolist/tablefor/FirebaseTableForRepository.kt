package com.metrolist.tablefor

import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.util.Locale

/** One rating per group, rater and target, so submitting again overwrites instead of duplicating. */
internal fun ratingDocumentId(groupId: String, raterId: String, targetId: String): String =
    "${groupId}_${raterId}_${targetId}"

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

    override val currentUserId: String
        get() = auth?.currentUser?.uid ?: "user_1"

    override val isFirebaseAvailable: Boolean
        get() = db != null && auth != null

    // --- Authentication ---
    override suspend fun signInAnonymously() {
        try {
            val a = auth ?: return
            if (a.currentUser == null) {
                a.signInAnonymously().await()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // --- Realtime Chat ---
    override fun getChatMessagesFlow(groupId: String): Flow<List<ChatMessage>> = callbackFlow {
        val firestore = db
        if (firestore == null) {
            close()
            return@callbackFlow
        }

        val listenerRegistration = firestore.collection("groups")
            .document(groupId)
            .collection("messages")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }

                val messages = snapshot?.documents?.mapNotNull { doc ->
                    val senderId = doc.getString("senderId") ?: ""
                    val senderName = doc.getString("senderName") ?: "Unknown"
                    val text = doc.getString("text") ?: ""
                    val timeDate = doc.getTimestamp("timestamp")?.toDate()
                    val formattedTime: String = if (timeDate != null) {
                        SimpleDateFormat("h:mm a", Locale.getDefault()).format(timeDate)
                    } else ""

                    ChatMessage(
                        id = doc.id,
                        senderName = senderName,
                        text = text,
                        timestamp = formattedTime,
                        isMe = senderId == currentUserId
                    )
                } ?: emptyList()

                trySend(messages)
            }

        awaitClose { listenerRegistration.remove() }
    }

    override suspend fun sendMessage(groupId: String, text: String, senderName: String) {
        val firestore = db ?: return
        try {
            val messageData = mapOf(
                "senderId" to currentUserId,
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
    override suspend fun updateAvailability(slotKey: String, isAvailable: Boolean, today: LocalDate) {
        val firestore = db ?: return
        try {
            val userAvailRef = firestore.collection("availability").document(currentUserId)

            firestore.runTransaction { transaction ->
                val snapshot = transaction.get(userAvailRef)
                @Suppress("UNCHECKED_CAST")
                val savedMap = (snapshot.get("slots") as? Map<String, Boolean>) ?: emptyMap()
                val newMap = AvailabilityKeys.prune(savedMap, today) + (slotKey to isAvailable)
                transaction.set(userAvailRef, mapOf("slots" to newMap))
            }.await()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun getAvailabilityFlow(userId: String): Flow<Map<String, Boolean>> = callbackFlow {
        val firestore = db
        if (firestore == null) {
            close()
            return@callbackFlow
        }

        val listener = firestore.collection("availability").document(userId)
            .addSnapshotListener { snapshot, _ ->
                @Suppress("UNCHECKED_CAST")
                val slots = snapshot?.get("slots") as? Map<String, Boolean> ?: emptyMap()
                trySend(slots)
            }
        awaitClose { listener.remove() }
    }

    // --- User Profile ---
    override suspend fun saveProfile(profile: UserProfile) {
        val firestore = db ?: return
        try {
            val profileData = mapOf(
                "id" to currentUserId,
                "name" to profile.name,
                "bio" to profile.bio,
                "location" to profile.location,
                "diets" to profile.diets,
                "cuisinePreferences" to profile.cuisinePreferences,
                "budget" to profile.budget,
                "trustRating" to profile.trustRating,
                "totalRatings" to profile.totalRatings
            )
            firestore.collection("users")
                .document(currentUserId)
                .set(profileData)
                .await()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun getUserProfileFlow(userId: String): Flow<UserProfile?> = callbackFlow {
        val firestore = db
        if (firestore == null) {
            close()
            return@callbackFlow
        }

        val listener = firestore.collection("users").document(userId)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null && snapshot.exists()) {
                    @Suppress("UNCHECKED_CAST")
                    val profile = UserProfile(
                        id = userId,
                        name = snapshot.getString("name") ?: "Alex Chen",
                        bio = snapshot.getString("bio") ?: "",
                        location = snapshot.getString("location") ?: "",
                        diets = (snapshot.get("diets") as? List<String>) ?: emptyList(),
                        cuisinePreferences = (snapshot.get("cuisinePreferences") as? List<String>) ?: emptyList(),
                        budget = snapshot.getString("budget") ?: "$$ (Moderate)",
                        trustRating = snapshot.getDouble("trustRating")?.toFloat() ?: 4.8f,
                        totalRatings = snapshot.getLong("totalRatings")?.toInt() ?: 12
                    )
                    trySend(profile)
                } else {
                    trySend(null)
                }
            }
        awaitClose { listener.remove() }
    }

    // --- Member Ratings ---
    override suspend fun submitRatings(groupId: String, ratings: List<MemberRating>) {
        val firestore = db ?: return
        val raterId = currentUserId
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
}
