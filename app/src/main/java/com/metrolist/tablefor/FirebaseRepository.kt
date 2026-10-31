package com.metrolist.tablefor

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class FirebaseRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {
    val currentUserId: String
        get() = auth.currentUser?.uid ?: "user_1"

    // --- USERS ---
    fun getUserProfile(userId: String = currentUserId): Flow<UserProfile?> = callbackFlow {
        val docRef = firestore.collection("users").document(userId)
        val subscription = docRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            if (snapshot != null && snapshot.exists()) {
                val firestoreUser = snapshot.toObject(FirestoreUserProfile::class.java)
                trySend(firestoreUser?.toUserProfile())
            } else {
                trySend(null)
            }
        }
        awaitClose { subscription.remove() }
    }

    suspend fun saveUserProfile(profile: UserProfile) {
        val docRef = firestore.collection("users").document(profile.id)
        val firestoreUser = FirestoreUserProfile.fromUserProfile(profile)
        docRef.set(firestoreUser).await()
    }

    // --- SCHEDULES ---
    fun getAvailability(userId: String = currentUserId): Flow<List<AvailabilityCell>> = callbackFlow {
        val docRef = firestore.collection("schedules").document(userId)
        val subscription = docRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            if (snapshot != null && snapshot.exists()) {
                val schedule = snapshot.toObject(FirestoreSchedule::class.java)
                val cells = schedule?.cells?.map { it.toAvailabilityCell() } ?: emptyList()
                trySend(cells)
            } else {
                trySend(emptyList())
            }
        }
        awaitClose { subscription.remove() }
    }

    suspend fun saveAvailability(userId: String = currentUserId, cells: List<AvailabilityCell>) {
        val docRef = firestore.collection("schedules").document(userId)
        val firestoreCells = cells.map { FirestoreAvailabilityCell.fromAvailabilityCell(it) }
        val schedule = FirestoreSchedule(
            userId = userId,
            cells = firestoreCells
        )
        docRef.set(schedule).await()
    }

    // --- DINNER GROUPS ---
    fun getDinnerGroup(groupId: String): Flow<DinnerGroup?> = callbackFlow {
        val docRef = firestore.collection("dinner_groups").document(groupId)
        val subscription = docRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            if (snapshot != null && snapshot.exists()) {
                val group = snapshot.toObject(FirestoreDinnerGroup::class.java)
                trySend(group?.toDinnerGroup())
            } else {
                trySend(null)
            }
        }
        awaitClose { subscription.remove() }
    }

    suspend fun saveDinnerGroup(group: DinnerGroup) {
        val docRef = firestore.collection("dinner_groups").document(group.groupId)
        val firestoreGroup = FirestoreDinnerGroup.fromDinnerGroup(group)
        docRef.set(firestoreGroup).await()
    }

    // --- CHAT MESSAGES ---
    fun getChatMessages(groupId: String, activeUserId: String = currentUserId): Flow<List<ChatMessage>> = callbackFlow {
        val messagesRef = firestore.collection("dinner_groups")
            .document(groupId)
            .collection("messages")
            .orderBy("timestamp", Query.Direction.ASCENDING)

        val subscription = messagesRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            if (snapshot != null) {
                val messages = snapshot.documents.mapNotNull { doc ->
                    doc.toObject(FirestoreChatMessage::class.java)?.toChatMessage(activeUserId)
                }
                trySend(messages)
            } else {
                trySend(emptyList())
            }
        }
        awaitClose { subscription.remove() }
    }

    suspend fun sendMessage(groupId: String, senderId: String = currentUserId, message: ChatMessage) {
        val messageRef = firestore.collection("dinner_groups")
            .document(groupId)
            .collection("messages")
            .document(message.id)

        val firestoreMsg = FirestoreChatMessage.fromChatMessage(groupId, senderId, message)
        messageRef.set(firestoreMsg).await()
    }

    // --- POST-DINNER RATINGS ---
    suspend fun submitRating(groupId: String, raterId: String = currentUserId, rating: MemberRating) {
        val ratingRef = firestore.collection("dinner_groups")
            .document(groupId)
            .collection("ratings")
            .document("${groupId}_${raterId}_${rating.memberId}")

        val firestoreRating = FirestoreMemberRating.fromMemberRating(groupId, raterId, rating)
        ratingRef.set(firestoreRating).await()
    }

    fun getGroupRatings(groupId: String): Flow<List<MemberRating>> = callbackFlow {
        val ratingsRef = firestore.collection("dinner_groups")
            .document(groupId)
            .collection("ratings")

        val subscription = ratingsRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            if (snapshot != null) {
                val ratings = snapshot.documents.mapNotNull { doc ->
                    doc.toObject(FirestoreMemberRating::class.java)?.toMemberRating()
                }
                trySend(ratings)
            } else {
                trySend(emptyList())
            }
        }
        awaitClose { subscription.remove() }
    }

    // --- INITIAL SEEDING ---
    suspend fun seedInitialData(
        profile: UserProfile,
        availabilityGrid: List<AvailabilityCell>,
        dinnerGroup: DinnerGroup?,
        chatMessages: List<ChatMessage>,
        memberRatings: List<MemberRating>
    ) {
        saveUserProfile(profile)
        if (availabilityGrid.isNotEmpty()) {
            saveAvailability(profile.id, availabilityGrid)
        }
        dinnerGroup?.let { group ->
            saveDinnerGroup(group)
            chatMessages.forEach { msg ->
                sendMessage(group.groupId, if (msg.isMe) profile.id else "2", msg)
            }
            memberRatings.forEach { rating ->
                submitRating(group.groupId, profile.id, rating)
            }
        }
    }
}
