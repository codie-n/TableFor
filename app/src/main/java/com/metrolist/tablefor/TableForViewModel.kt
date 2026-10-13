package com.metrolist.tablefor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class TableForViewModel : ViewModel() {
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
    }

    private fun generateUpcomingWeekGrid() {
        val timeSlots = listOf("6:00 PM", "7:00 PM", "8:00 PM")
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
    }

    fun toggleAvailability(dayIndex: Int, timeSlot: String) {
        availabilityGrid = availabilityGrid.map { cell ->
            if (cell.dayIndex == dayIndex && cell.timeSlot == timeSlot) {
                cell.copy(isAvailable = !cell.isAvailable)
            } else {
                cell
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
            isMe = true
        )
        chatMessages = chatMessages + newMsg
    }

    fun updateRating(memberId: String, punctuality: Int, friendliness: Int, reliability: Int, notes: String) {
        memberRatings = memberRatings.map { rating ->
            if (rating.memberId == memberId) {
                rating.copy(punctuality = punctuality, friendliness = friendliness, reliability = reliability, notes = notes)
            } else {
                rating
            }
        }
    }
}
