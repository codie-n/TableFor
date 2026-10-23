package com.metrolist.tablefor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class TableForViewModel(
    private val repository: TableForRepository = TableForRepository()
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
        initializeFirestoreSync()
    }

    private fun initializeFirestoreSync() {
        viewModelScope.launch {
            repository.signInAnonymously()

            // Observe User Profile
            launch {
                repository.getUserProfileFlow().collect { cloudProfile ->
                    if (cloudProfile != null) {
                        profile = cloudProfile
                    }
                }
            }

            // Observe Realtime Group Chat Messages
            launch {
                val groupId = dinnerGroup?.groupId ?: "group_101"
                repository.getChatMessagesFlow(groupId).collect { cloudMessages ->
                    if (cloudMessages.isNotEmpty()) {
                        chatMessages = cloudMessages
                    }
                }
            }

            // Observe Schedule Availability
            launch {
                repository.getAvailabilityFlow().collect { cloudSlots ->
                    if (cloudSlots.isNotEmpty()) {
                        availabilityGrid = availabilityGrid.map { cell ->
                            val key = "${cell.dayIndex}_${cell.timeSlot}"
                            if (cloudSlots.containsKey(key)) {
                                cell.copy(isAvailable = cloudSlots[key] == true)
                            } else {
                                cell
                            }
                        }
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
        viewModelScope.launch {
            repository.saveProfile(newProfile)
        }
    }

    fun toggleAvailability(dayIndex: Int, timeSlot: String) {
        var updatedStatus = false
        availabilityGrid = availabilityGrid.map { cell ->
            if (cell.dayIndex == dayIndex && cell.timeSlot == timeSlot) {
                val newStatus = !cell.isAvailable
                updatedStatus = newStatus
                cell.copy(isAvailable = newStatus)
            } else {
                cell
            }
        }
        viewModelScope.launch {
            repository.updateAvailability(dayIndex, timeSlot, updatedStatus)
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

        viewModelScope.launch {
            val groupId = dinnerGroup?.groupId ?: "group_101"
            repository.sendMessage(groupId, text, profile.name)
        }
    }

    fun updateRating(memberId: String, punctuality: Int, friendliness: Int, reliability: Int, notes: String) {
        memberRatings = memberRatings.map { rating ->
            if (rating.memberId == memberId) {
                rating.copy(punctuality = punctuality, friendliness = friendliness, reliability = reliability, notes = notes)
            } else {
                rating
            }
        }
        viewModelScope.launch {
            val groupId = dinnerGroup?.groupId ?: "group_101"
            repository.submitRatings(groupId, memberRatings)
        }
    }
}
