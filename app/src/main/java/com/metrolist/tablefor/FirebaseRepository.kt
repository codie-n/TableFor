package com.metrolist.tablefor

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class FirebaseRepository(
    private val context: android.content.Context? = null,
    private val firestore: FirebaseFirestore? = try { FirebaseFirestore.getInstance() } catch (_: Throwable) { null },
    private val auth: FirebaseAuth? = try { FirebaseAuth.getInstance() } catch (_: Throwable) { null }
) {
    private fun getApplicationContext(): android.content.Context? {
        if (context != null) return context
        return try {
            com.google.firebase.FirebaseApp.getInstance().applicationContext
        } catch (_: Throwable) {
            null
        }
    }

    val currentUser: FirebaseUser?
        get() = try { auth?.currentUser } catch (_: Throwable) { null }

    val currentUserId: String
        get() = try { auth?.currentUser?.uid ?: "" } catch (_: Throwable) { "" }

    val isLoggedIn: Boolean
        get() = try { auth?.currentUser != null } catch (_: Throwable) { false }

    fun getAuthState(): Flow<FirebaseUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            trySend(firebaseAuth.currentUser)
        }
        val a = auth
        if (a != null) {
            a.addAuthStateListener(listener)
            awaitClose { a.removeAuthStateListener(listener) }
        } else {
            trySend(null)
            awaitClose { }
        }
    }

    suspend fun signUp(email: String, password: String, name: String): UserProfile {
        val cleanEmail = sanitizeEmail(email)
        val cleanName = sanitizeName(name)
        val a = auth ?: throw IllegalStateException("Firebase Authentication is not available.")

        val authResult = a.createUserWithEmailAndPassword(cleanEmail, password).await()
        val user = authResult.user ?: throw IllegalStateException("User creation failed")

        val initialProfile = UserProfile(
            id = user.uid,
            name = cleanName.ifEmpty { user.displayName ?: cleanEmail.substringBefore("@") },
            bio = "New TableFor member",
            location = "Downtown",
            diets = emptyList(),
            cuisinePreferences = listOf("Italian", "Japanese"),
            budget = "$$ (Moderate)",
            trustRating = 5.0f,
            totalRatings = 0
        )
        try {
            saveUserProfile(initialProfile)
        } catch (_: Exception) {}
        return initialProfile
    }

    suspend fun signIn(email: String, password: String): UserProfile {
        val cleanEmail = sanitizeEmail(email)
        val a = auth ?: throw IllegalStateException("Firebase Authentication is not available.")

        val authResult = a.signInWithEmailAndPassword(cleanEmail, password).await()
        val user = authResult.user ?: throw IllegalStateException("Sign in failed")

        val fs = firestore
        if (fs != null) {
            val userDoc = fs.collection("users").document(user.uid).get().await()
            if (userDoc.exists()) {
                val firestoreUser = userDoc.toObject(FirestoreUserProfile::class.java)
                if (firestoreUser != null) {
                    return firestoreUser.toUserProfile()
                }
            }
        }

        val profile = UserProfile(
            id = user.uid,
            name = user.displayName ?: cleanEmail.substringBefore("@").replaceFirstChar { it.uppercase() },
            bio = "TableFor member",
            location = "Downtown",
            diets = emptyList(),
            cuisinePreferences = listOf("Italian", "Japanese"),
            budget = "$$ (Moderate)",
            trustRating = 5.0f,
            totalRatings = 0
        )
        try {
            saveUserProfile(profile)
        } catch (_: Exception) {}
        return profile
    }

    private fun sanitizeEmail(rawEmail: String): String {
        return rawEmail.trim()
            .lowercase(java.util.Locale.ROOT)
            .replace(Regex("[\\r\\n\\t\\v]"), "")
    }

    private fun sanitizeName(rawName: String): String {
        return rawName.trim()
            .replace(Regex("[\\r\\n\\t\\v]"), " ")
            .replace(Regex(" {2,}"), " ")
    }

    fun signOut() {
        auth?.signOut()
    }

    // --- USERS ---
    fun getUserProfile(userId: String = currentUserId): Flow<UserProfile?> = callbackFlow {
        val targetId = userId.ifEmpty { currentUserId }
        val fs = firestore
        if (fs == null) {
            trySend(null)
            awaitClose { }
            return@callbackFlow
        }
        val docRef = fs.collection("users").document(targetId)
        val subscription = docRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            if (snapshot != null && snapshot.exists()) {
                val firestoreUser = snapshot.toObject(FirestoreUserProfile::class.java)
                val userProfile = firestoreUser?.toUserProfile()
                if (userProfile != null) {
                    trySend(userProfile)
                } else {
                    trySend(null)
                }
            } else {
                trySend(null)
            }
        }
        awaitClose { subscription.remove() }
    }

    suspend fun saveUserProfile(profile: UserProfile) {
        val fs = firestore ?: return
        try {
            val docRef = fs.collection("users").document(profile.id)
            val firestoreUser = FirestoreUserProfile.fromUserProfile(profile)
            docRef.set(firestoreUser).await()
        } catch (_: Exception) {}
    }

    // --- SCHEDULES ---
    private fun getSchedulePrefs(): android.content.SharedPreferences? {
        return getApplicationContext()?.getSharedPreferences("tablefor_user_schedules", android.content.Context.MODE_PRIVATE)
    }

    private fun deduplicateCells(cells: List<AvailabilityCell>): List<AvailabilityCell> {
        if (cells.isEmpty()) return emptyList()
        val map = mutableMapOf<Pair<String, String>, AvailabilityCell>()
        for (cell in cells) {
            val key = Pair(cell.dayLabel.ifEmpty { cell.dayIndex.toString() }, cell.timeSlot)
            val existing = map[key]
            if (existing == null || cell.isAvailable) {
                map[key] = cell
            }
        }
        return map.values.toList()
    }

    private fun saveLocalSchedule(userId: String, cells: List<AvailabilityCell>) {
        val prefs = getSchedulePrefs() ?: return
        if (userId.isBlank()) return
        val cleanCells = deduplicateCells(cells)
        val serialized = cleanCells.joinToString(";") { cell ->
            "${cell.dayIndex}|||${cell.dayLabel}|||${cell.timeSlot}|||${cell.isAvailable}"
        }
        prefs.edit().putString(userId, serialized).apply()
    }

    private fun getLocalSchedule(userId: String): List<AvailabilityCell> {
        val prefs = getSchedulePrefs() ?: return emptyList()
        if (userId.isBlank()) return emptyList()
        val serialized = prefs.getString(userId, null) ?: return emptyList()
        return try {
            val parsed = serialized.split(";").mapNotNull { item ->
                val parts = item.split("|||")
                if (parts.size >= 4) {
                    AvailabilityCell(
                        dayIndex = parts[0].toIntOrNull() ?: 0,
                        dayLabel = parts[1],
                        timeSlot = parts[2],
                        isAvailable = parts[3].toBoolean()
                    )
                } else null
            }
            deduplicateCells(parsed)
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun getAvailability(userId: String = currentUserId): Flow<List<AvailabilityCell>> = callbackFlow {
        val targetId = userId.ifEmpty { currentUserId }

        val initialLocalCells = getLocalSchedule(targetId)
        if (initialLocalCells.isNotEmpty()) {
            trySend(initialLocalCells)
        }

        val fs = firestore
        if (fs == null) {
            awaitClose { }
            return@callbackFlow
        }
        val docRef = fs.collection("schedules").document(targetId)
        val subscription = docRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                val localCells = getLocalSchedule(targetId)
                if (localCells.isNotEmpty()) {
                    trySend(localCells)
                }
                return@addSnapshotListener
            }
            if (snapshot != null && snapshot.exists()) {
                val schedule = snapshot.toObject(FirestoreSchedule::class.java)
                val cells = schedule?.cells?.map { it.toAvailabilityCell() } ?: emptyList()
                if (cells.isNotEmpty()) {
                    saveLocalSchedule(targetId, cells)
                    trySend(cells)
                } else {
                    val localCells = getLocalSchedule(targetId)
                    if (localCells.isNotEmpty()) {
                        trySend(localCells)
                    }
                }
            } else {
                val localCells = getLocalSchedule(targetId)
                if (localCells.isNotEmpty()) {
                    trySend(localCells)
                }
            }
        }
        awaitClose { subscription.remove() }
    }

    suspend fun saveAvailability(userId: String = currentUserId, cells: List<AvailabilityCell>) {
        val targetId = userId.ifEmpty { currentUserId }
        saveLocalSchedule(targetId, cells)

        val fs = firestore ?: return
        try {
            val docRef = fs.collection("schedules").document(targetId)
            val firestoreCells = cells.map { FirestoreAvailabilityCell.fromAvailabilityCell(it) }
            val schedule = FirestoreSchedule(
                userId = targetId,
                cells = firestoreCells
            )
            docRef.set(schedule).await()
        } catch (_: Exception) {}
    }

    // --- DINNER GROUPS ---
    fun getDinnerGroup(groupId: String): Flow<DinnerGroup?> = callbackFlow {
        val fs = firestore
        if (fs == null) {
            trySend(null)
            awaitClose { }
            return@callbackFlow
        }
        val docRef = fs.collection("dinner_groups").document(groupId)
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
        val fs = firestore ?: return
        val docRef = fs.collection("dinner_groups").document(group.groupId)
        val firestoreGroup = FirestoreDinnerGroup.fromDinnerGroup(group)
        docRef.set(firestoreGroup).await()
    }

    // --- CHAT MESSAGES ---
    fun getChatMessages(groupId: String, activeUserId: String = currentUserId): Flow<List<ChatMessage>> = callbackFlow {
        val fs = firestore
        if (fs == null) {
            trySend(emptyList())
            awaitClose { }
            return@callbackFlow
        }
        val messagesRef = fs.collection("dinner_groups")
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
        val fs = firestore ?: return
        val messageRef = fs.collection("dinner_groups")
            .document(groupId)
            .collection("messages")
            .document(message.id)

        val firestoreMsg = FirestoreChatMessage.fromChatMessage(groupId, senderId, message)
        messageRef.set(firestoreMsg).await()
    }

    // --- POST-DINNER RATINGS ---
    suspend fun submitRating(groupId: String, raterId: String = currentUserId, rating: MemberRating) {
        val fs = firestore ?: return
        val ratingRef = fs.collection("dinner_groups")
            .document(groupId)
            .collection("ratings")
            .document("${groupId}_${raterId}_${rating.memberId}")

        val firestoreRating = FirestoreMemberRating.fromMemberRating(groupId, raterId, rating)
        ratingRef.set(firestoreRating).await()
    }

    fun getGroupRatings(groupId: String): Flow<List<MemberRating>> = callbackFlow {
        val fs = firestore
        if (fs == null) {
            trySend(emptyList())
            awaitClose { }
            return@callbackFlow
        }
        val ratingsRef = fs.collection("dinner_groups")
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
