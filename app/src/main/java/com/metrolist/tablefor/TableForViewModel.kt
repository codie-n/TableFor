package com.metrolist.tablefor

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

class TableForViewModel(
    private val firebaseRepository: FirebaseRepository? = FirebaseRepository()
) : ViewModel() {

    var profile by mutableStateOf(UserProfile())
        private set

    var availabilityGrid by mutableStateOf<List<AvailabilityCell>>(emptyList())
        private set

    var dinnerGroup by mutableStateOf<DinnerGroup?>(DinnerGroup())
        private set

    var chatMessages by mutableStateOf<List<ChatMessage>>(
        listOf(
            ChatMessage("1", "Sarah Jenkins", "Hey everyone! Excited for dinner on Thursday at Trattoria Bella!", "6:45 PM", false),
            ChatMessage("2", "Marcus Vance", "Me too! Are we all good with walking there together?", "6:50 PM", false),
            ChatMessage("3", "Alex Chen", "Sounds great! See you all there.", "6:52 PM", true)
        )
    )
        private set

    var memberRatings by mutableStateOf<List<MemberRating>>(emptyList())
        private set

    init {
        generateUpcomingWeekGrid()
        initRatings()
        seedFirebaseData()
        listenToFirebaseData()
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
                cell.copy(isAvailable = !cell.isAvailable)
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
        val newMsg = ChatMessage(
            id = System.currentTimeMillis().toString(),
            senderName = profile.name,
            text = text,
            timestamp = SimpleDateFormat("h:mm a", Locale.getDefault()).format(java.util.Date()),
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
