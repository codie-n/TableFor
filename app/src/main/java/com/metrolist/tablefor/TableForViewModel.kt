package com.metrolist.tablefor

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class TableForViewModel @JvmOverloads constructor(
    application: Application? = null,
    private val firebaseRepository: FirebaseRepository? = FirebaseRepository(application?.applicationContext)
) : ViewModel() {

    var isLoggedIn by mutableStateOf(firebaseRepository?.isLoggedIn == true)
        private set

    var authError by mutableStateOf<String?>(null)
        private set

    var isAuthLoading by mutableStateOf(false)
        private set

    var currentUserId by mutableStateOf(firebaseRepository?.currentUserId ?: "")
        private set

    var profile by mutableStateOf(UserProfile())
        private set

    var availabilityGrid by mutableStateOf<List<AvailabilityCell>>(emptyList())
        private set

    var dinnerGroup by mutableStateOf<DinnerGroup?>(DinnerGroup())
        private set

    var chatMessages by mutableStateOf<List<ChatMessage>>(
        listOf(
            ChatMessage("1", "Sarah Jenkins", "Hey everyone! Excited for dinner on Friday at Tanaka's", "6:45 PM", false),
            ChatMessage("2", "Marcus Vance", "Me too! Are we all good with meeting in front of the place?", "6:50 PM", false),
            ChatMessage("3", "Alex Chen", "Sounds great! See you all there.", "6:52 PM", true)
        )
    )
        private set

    var memberRatings by mutableStateOf<List<MemberRating>>(emptyList())
        private set

    init {
        generateUpcomingWeekGrid()
        initRatings()
        observeAuthState()
    }

    private fun observeAuthState() {
        val repo = firebaseRepository ?: return
        viewModelScope.launch {
            repo.getAuthState().collect { user ->
                if (user != null) {
                    currentUserId = user.uid
                    isLoggedIn = true
                    listenToFirebaseData()
                } else {
                    currentUserId = ""
                    isLoggedIn = false
                }
            }
        }
    }

    fun signUp(email: String, password: String, confirmPassword: String, name: String) {
        authError = null
        if (email.isBlank() || password.isBlank() || name.isBlank()) {
            authError = "Please fill in all fields."
            return
        }
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()) {
            authError = "Please enter a valid email address."
            return
        }
        if (password.length < 6) {
            authError = "Password must be at least 6 characters long."
            return
        }
        if (password != confirmPassword) {
            authError = "Passwords do not match."
            return
        }

        val repo = firebaseRepository ?: return
        isAuthLoading = true
        viewModelScope.launch {
            try {
                val newProfile = repo.signUp(email, password, name)
                profile = newProfile
                currentUserId = newProfile.id
                isLoggedIn = true
                authError = null
                listenToFirebaseData()
            } catch (e: Exception) {
                authError = parseAuthException(e)
            } finally {
                isAuthLoading = false
            }
        }
    }

    fun signIn(email: String, password: String) {
        authError = null
        if (email.isBlank() || password.isBlank()) {
            authError = "Please enter both email and password."
            return
        }
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()) {
            authError = "Please enter a valid email address."
            return
        }

        val repo = firebaseRepository ?: return
        isAuthLoading = true
        viewModelScope.launch {
            try {
                val loggedInProfile = repo.signIn(email, password)
                profile = loggedInProfile
                currentUserId = loggedInProfile.id
                isLoggedIn = true
                authError = null
                listenToFirebaseData()
            } catch (e: Exception) {
                authError = parseAuthException(e)
            } finally {
                isAuthLoading = false
            }
        }
    }

    fun signOut() {
        firebaseRepository?.signOut()
        isLoggedIn = false
        currentUserId = ""
        authError = null
    }

    fun clearAuthError() {
        authError = null
    }

    private fun parseAuthException(e: Exception): String {
        val msg = e.localizedMessage ?: e.message ?: ""
        return when {
            msg.contains("does not exist in the database", ignoreCase = true) ->
                "User account does not exist in the database. Please sign up first."

            msg.contains("already exists", ignoreCase = true) ||
            msg.contains("already in use", ignoreCase = true) ||
            msg.contains("email-already-in-use", ignoreCase = true) ||
            e is com.google.firebase.auth.FirebaseAuthUserCollisionException ->
                "An account with this email address already exists. Please sign in instead."

            msg.contains("badly formatted", ignoreCase = true) ||
            msg.contains("invalid email", ignoreCase = true) ->
                "Please enter a valid email address."

            msg.contains("wrong-password", ignoreCase = true) ||
            msg.contains("INVALID_LOGIN_CREDENTIALS", ignoreCase = true) ||
            msg.contains("user-not-found", ignoreCase = true) ||
            msg.contains("Invalid email or password", ignoreCase = true) ||
            e is com.google.firebase.auth.FirebaseAuthInvalidCredentialsException ||
            e is com.google.firebase.auth.FirebaseAuthInvalidUserException ->
                "Invalid email or password."

            msg.contains("API key not valid", ignoreCase = true) || msg.contains("API_KEY_NOT_VALID", ignoreCase = true) ->
                "Invalid Firebase API key in app/google-services.json. Please update with a valid API key from Firebase Console."

            msg.contains("at least 6 characters", ignoreCase = true) || msg.contains("weak password", ignoreCase = true) ->
                "Password must be at least 6 characters long."

            else -> msg.ifBlank { "Invalid email or password." }
        }
    }

    private fun seedFirebaseData() {
        val repo = firebaseRepository ?: return
        viewModelScope.launch {
            try {
                repo.seedInitialData(
                    profile = profile,
                    availabilityGrid = availabilityGrid,
                    dinnerGroup = dinnerGroup,
                    chatMessages = chatMessages,
                    memberRatings = memberRatings
                )
            } catch (_: Exception) {}
        }
    }

    private fun listenToFirebaseData() {
        val repo = firebaseRepository ?: return
        viewModelScope.launch {
            repo.getUserProfile()
                .catch { /* Fallback to local profile */ }
                .collect { remoteProfile ->
                    if (remoteProfile != null) {
                        profile = remoteProfile
                    }
                }
        }

        viewModelScope.launch {
            repo.getAvailability()
                .catch { /* Fallback to local schedule */ }
                .collect { remoteCells ->
                    if (remoteCells.isNotEmpty()) {
                        availabilityGrid = remoteCells
                    }
                }
        }

        dinnerGroup?.groupId?.let { groupId ->
            viewModelScope.launch {
                repo.getDinnerGroup(groupId)
                    .catch { /* Fallback to local group */ }
                    .collect { remoteGroup ->
                        if (remoteGroup != null) {
                            dinnerGroup = remoteGroup
                        }
                    }
            }

            viewModelScope.launch {
                repo.getChatMessages(groupId)
                    .catch { /* Fallback to local messages */ }
                    .collect { remoteMessages ->
                        if (remoteMessages.isNotEmpty()) {
                            chatMessages = remoteMessages
                        }
                    }
            }
        }
    }

    private fun generateUpcomingWeekGrid() {
        val timeSlots = listOf("5:00 PM", "5:30 PM", "6:00 PM", "6:30 PM", "7:00 PM", "7:30 PM", "8:00 PM", "8:30 PM", "9:00 PM", "9:30 PM", "10:00 PM")
        val calendar = Calendar.getInstance()
        val dateFormat = SimpleDateFormat("EEE, MMM d", Locale.getDefault())
        
        val cells = mutableListOf<AvailabilityCell>()
        for (dayIdx in 0..6) {
            val dayLabel = dateFormat.format(calendar.time)
            for (timeSlot in timeSlots) {
                val isAvail = (dayIdx == 3 || dayIdx == 4) && (timeSlot == "7:00 PM" || timeSlot == "8:00 PM")
                cells.add(AvailabilityCell(dayIndex = dayIdx, dayLabel = dayLabel, timeSlot = timeSlot, isAvailable = isAvail))
            }
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }
        availabilityGrid = cells
    }

    private fun initRatings() {
        dinnerGroup?.let { group ->
            memberRatings = group.members.filter { it.id != "1" }.map { member ->
                MemberRating(memberId = member.id, memberName = member.name)
            }
        }
    }

    fun updateProfile(newProfile: UserProfile) {
        profile = newProfile
        dinnerGroup = dinnerGroup?.let { group ->
            val updatedMembers = group.members.map { member ->
                if (member.id == "1") {
                    member.copy(
                        name = newProfile.name,
                        rating = newProfile.trustRating,
                        diet = newProfile.diets.joinToString(", ").ifEmpty { "None" }
                    )
                } else {
                    member
                }
            }
            group.copy(members = updatedMembers)
        }

        firebaseRepository?.let { repo ->
            viewModelScope.launch {
                try {
                    repo.saveUserProfile(newProfile)
                } catch (_: Exception) {}
            }
        }
    }

    fun toggleAvailability(dayIndex: Int, timeSlot: String) {
        val updatedGrid = availabilityGrid.map { cell ->
            if (cell.dayIndex == dayIndex && cell.timeSlot == timeSlot) {
                val newStatus = !cell.isAvailable
                updatedStatus = newStatus
                cell.copy(isAvailable = newStatus)
            } else {
                cell
            }
        }
        availabilityGrid = updatedGrid

        firebaseRepository?.let { repo ->
            viewModelScope.launch {
                try {
                    repo.saveAvailability(cells = updatedGrid)
                } catch (_: Exception) {}
            }
        }
    }

    fun sendMessage(text: String) {
        if (text.isBlank()) return
        val currentTimestamp = SimpleDateFormat("h:mm a", Locale.getDefault()).format(java.util.Date())
        val newMsg = ChatMessage(
            id = System.currentTimeMillis().toString(),
            senderName = profile.name,
            text = text,
            timestamp = currentTimestamp,
            isMe = true,
            isRead = true
        )
        chatMessages = chatMessages + newMsg

        val groupId = dinnerGroup?.groupId ?: return
        firebaseRepository?.let { repo ->
            viewModelScope.launch {
                try {
                    repo.sendMessage(groupId = groupId, message = newMsg)
                } catch (_: Exception) {}
            }
        }
    }

    fun updateRating(memberId: String, punctuality: Int, friendliness: Int, reliability: Int, notes: String) {
        val updatedRatings = memberRatings.map { rating ->
            if (rating.memberId == memberId) {
                rating.copy(punctuality = punctuality, friendliness = friendliness, reliability = reliability, notes = notes)
            } else {
                rating
            }
        }
        memberRatings = updatedRatings

        val targetRating = updatedRatings.find { it.memberId == memberId } ?: return
        val groupId = dinnerGroup?.groupId ?: return
        firebaseRepository?.let { repo ->
            viewModelScope.launch {
                try {
                    repo.submitRating(groupId = groupId, rating = targetRating)
                } catch (_: Exception) {}
            }
        }
    }
}
