package com.metrolist.tablefor

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

private data class DemoAccount(
    val email: String,
    val passwordHash: String,
    val profile: UserProfile
)

class FirebaseRepository(
    private val context: android.content.Context? = null,
    private val firestore: FirebaseFirestore? = try { FirebaseFirestore.getInstance() } catch (_: Throwable) { null },
    private val auth: FirebaseAuth? = try { FirebaseAuth.getInstance() } catch (_: Throwable) { null }
) {
    private val demoAccountsDb: MutableMap<String, DemoAccount> = mutableMapOf(
        "alex@example.com" to DemoAccount(
            email = "alex@example.com",
            passwordHash = "password123",
            profile = UserProfile(
                id = "user_1",
                name = "Alex Chen",
                bio = "New in town, love exploring food spots!",
                location = "Downtown / Financial District",
                diets = listOf("Vegetarian"),
                cuisinePreferences = listOf("Italian", "Japanese", "Mexican"),
                budget = "$$ (Moderate)",
                trustRating = 4.8f,
                totalRatings = 12
            )
        )
    )

    init {
        loadStoredDemoAccounts()
    }

    private fun getApplicationContext(): android.content.Context? {
        if (context != null) return context
        return try {
            com.google.firebase.FirebaseApp.getInstance().applicationContext
        } catch (_: Throwable) {
            null
        }
    }

    private fun getPrefs(): android.content.SharedPreferences? {
        return getApplicationContext()?.getSharedPreferences("tablefor_demo_users", android.content.Context.MODE_PRIVATE)
    }

    private fun loadStoredDemoAccounts() {
        val prefs = getPrefs() ?: return
        val all = prefs.all
        for ((email, value) in all) {
            if (value is String) {
                try {
                    val parts = value.split("|||")
                    if (parts.size >= 8) {
                        val pwd = parts[0]
                        val id = parts[1]
                        val name = parts[2]
                        val bio = parts[3]
                        val loc = parts[4]
                        val budget = parts[5]
                        val rating = parts[6].toFloatOrNull() ?: 5.0f
                        val count = parts[7].toIntOrNull() ?: 0
                        val diets = if (parts.size >= 9 && parts[8].isNotBlank()) parts[8].split(",") else emptyList()
                        val cuisines = if (parts.size >= 10 && parts[9].isNotBlank()) parts[9].split(",") else listOf("Italian", "Japanese")
                        val profile = UserProfile(id, name, bio, loc, diets, cuisines, budget, rating, count)
                        demoAccountsDb[email] = DemoAccount(email, pwd, profile)
                    }
                } catch (_: Exception) {}
            }
        }
    }

    private fun saveDemoAccountToPrefs(account: DemoAccount) {
        val prefs = getPrefs() ?: return
        val p = account.profile
        val dietsJoined = p.diets.joinToString(",")
        val cuisinesJoined = p.cuisinePreferences.joinToString(",")
        val serialized = "${account.passwordHash}|||${p.id}|||${p.name}|||${p.bio}|||${p.location}|||${p.budget}|||${p.trustRating}|||${p.totalRatings}|||${dietsJoined}|||${cuisinesJoined}"
        prefs.edit().putString(account.email, serialized).apply()
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
        val a = auth
        if (a != null) {
            try {
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
            } catch (e: Exception) {
                if (e is com.google.firebase.auth.FirebaseAuthUserCollisionException ||
                    e.message?.contains("already in use", ignoreCase = true) == true ||
                    e.message?.contains("already exists", ignoreCase = true) == true) {
                    throw IllegalStateException("An account with this email address already exists. Please sign in instead.")
                }
                if (isApiKeyError(e)) {
                    return signUpDemo(cleanEmail, password, cleanName)
                }
                throw e
            }
        }
        return signUpDemo(cleanEmail, password, cleanName)
    }

    private fun signUpDemo(cleanEmail: String, password: String, cleanName: String): UserProfile {
        if (demoAccountsDb.containsKey(cleanEmail)) {
            throw IllegalStateException("An account with this email address already exists. Please sign in instead.")
        }
        val dummyUid = "user_" + Math.abs(cleanEmail.hashCode())
        val profile = UserProfile(
            id = dummyUid,
            name = cleanName.ifEmpty { cleanEmail.substringBefore("@").replaceFirstChar { it.uppercase() } },
            bio = "TableFor member",
            location = "Downtown",
            diets = emptyList(),
            cuisinePreferences = listOf("Italian", "Japanese"),
            budget = "$$ (Moderate)",
            trustRating = 5.0f,
            totalRatings = 0
        )
        val account = DemoAccount(cleanEmail, password, profile)
        demoAccountsDb[cleanEmail] = account
        saveDemoAccountToPrefs(account)
        return profile
    }

    suspend fun signIn(email: String, password: String): UserProfile {
        val cleanEmail = sanitizeEmail(email)
        val a = auth
        if (a != null) {
            try {
                val authResult = a.signInWithEmailAndPassword(cleanEmail, password).await()
                val user = authResult.user ?: throw IllegalStateException("Sign in failed")

                val fs = firestore
                if (fs != null) {
                    val userDoc = fs.collection("users").document(user.uid).get().await()
                    if (!userDoc.exists()) {
                        throw IllegalStateException("User account does not exist in the database. Please sign up first.")
                    }
                    val firestoreUser = userDoc.toObject(FirestoreUserProfile::class.java)
                    if (firestoreUser != null) {
                        return firestoreUser.toUserProfile()
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
            } catch (e: Exception) {
                if (isApiKeyError(e)) {
                    return signInDemo(cleanEmail, password)
                }
                throw e
            }
        }
        return signInDemo(cleanEmail, password)
    }

    private fun signInDemo(cleanEmail: String, password: String): UserProfile {
        val account = demoAccountsDb[cleanEmail]
            ?: throw IllegalStateException("User account does not exist in the database. Please sign up first.")

        if (account.passwordHash != password) {
            throw IllegalStateException("Invalid email or password.")
        }
        return account.profile
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

    private fun isApiKeyError(e: Exception): Boolean {
        val msg = e.message ?: ""
        return msg.contains("API key not valid", ignoreCase = true) ||
               msg.contains("API_KEY_NOT_VALID", ignoreCase = true) ||
               msg.contains("invalid API key", ignoreCase = true)
    }

    fun signOut() {
        auth?.signOut()
    }

    // --- USERS ---
    fun getUserProfile(userId: String = currentUserId): Flow<UserProfile?> = callbackFlow {
        val fs = firestore
        if (fs == null) {
            val localProfile = demoAccountsDb.values.find { it.profile.id == userId }?.profile
            trySend(localProfile)
            awaitClose { }
            return@callbackFlow
        }
        val docRef = fs.collection("users").document(userId)
        val subscription = docRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                val localProfile = demoAccountsDb.values.find { it.profile.id == userId }?.profile
                trySend(localProfile)
                return@addSnapshotListener
            }
            if (snapshot != null && snapshot.exists()) {
                val firestoreUser = snapshot.toObject(FirestoreUserProfile::class.java)
                trySend(firestoreUser?.toUserProfile())
            } else {
                val localProfile = demoAccountsDb.values.find { it.profile.id == userId }?.profile
                trySend(localProfile)
            }
        }
        awaitClose { subscription.remove() }
    }

    suspend fun saveUserProfile(profile: UserProfile) {
        val fs = firestore
        if (fs != null) {
            try {
                val docRef = fs.collection("users").document(profile.id)
                val firestoreUser = FirestoreUserProfile.fromUserProfile(profile)
                docRef.set(firestoreUser).await()
            } catch (_: Exception) {}
        }
        val entry = demoAccountsDb.entries.find { it.value.profile.id == profile.id }
        if (entry != null) {
            val updatedAccount = entry.value.copy(profile = profile)
            demoAccountsDb[entry.key] = updatedAccount
            saveDemoAccountToPrefs(updatedAccount)
        }
    }

    // --- SCHEDULES ---
    fun getAvailability(userId: String = currentUserId): Flow<List<AvailabilityCell>> = callbackFlow {
        val fs = firestore
        if (fs == null) {
            trySend(emptyList())
            awaitClose { }
            return@callbackFlow
        }
        val docRef = fs.collection("schedules").document(userId)
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
        val fs = firestore ?: return
        val docRef = fs.collection("schedules").document(userId)
        val firestoreCells = cells.map { FirestoreAvailabilityCell.fromAvailabilityCell(it) }
        val schedule = FirestoreSchedule(
            userId = userId,
            cells = firestoreCells
        )
        docRef.set(schedule).await()
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
