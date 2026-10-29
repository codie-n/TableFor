package com.metrolist.tablefor

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class TableForViewModel(
    private val repository: TableForRepository = FirebaseTableForRepository(),
    private val clock: Clock = Clock.systemDefaultZone()
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

    var ratingsSubmitState by mutableStateOf(SaveState.Idle)
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

            // Observe Schedule Availability. The grid shows exactly what's saved:
            // old-format keys and past dates match no cell, so they're ignored.
            launch {
                repository.getAvailabilityFlow().collect { cloudSlots ->
                    availabilityGrid = availabilityGrid.map { cell ->
                        cell.copy(isAvailable = cloudSlots[cell.slotKey] == true)
                    }
                }
            }
        }
    }

    private fun generateUpcomingWeekGrid() {
        val today = LocalDate.now(clock)
        val dateFormat = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault())

        val cells = mutableListOf<AvailabilityCell>()
        for (dayIdx in 0..6) {
            val date = today.plusDays(dayIdx.toLong())
            val dayLabel = date.format(dateFormat)
            for ((time, timeSlot) in TIME_SLOTS) {
                val isAvail = (dayIdx == 3 || dayIdx == 4) && (timeSlot == "7:00 PM" || timeSlot == "8:00 PM")
                cells.add(AvailabilityCell(date = date, time = time, dayIndex = dayIdx, dayLabel = dayLabel, timeSlot = timeSlot, isAvailable = isAvail))
            }
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
        val target = availabilityGrid.find { it.dayIndex == dayIndex && it.timeSlot == timeSlot } ?: return
        val newStatus = !target.isAvailable
        availabilityGrid = availabilityGrid.map { cell ->
            if (cell == target) cell.copy(isAvailable = newStatus) else cell
        }
        viewModelScope.launch {
            repository.updateAvailability(target.slotKey, newStatus, LocalDate.now(clock))
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

    /** Slider moves change local state only; nothing is written until [submitRatings]. */
    fun updateRating(memberId: String, punctuality: Int, friendliness: Int, reliability: Int, notes: String) {
        memberRatings = memberRatings.map { rating ->
            if (rating.memberId == memberId) {
                rating.copy(punctuality = punctuality, friendliness = friendliness, reliability = reliability, notes = notes)
            } else {
                rating
            }
        }
        // A thank-you or error message no longer describes the edited ratings.
        if (ratingsSubmitState != SaveState.Saving) ratingsSubmitState = SaveState.Idle
    }

    /** Writes all ratings in one batch. Ignored while a submit is already in progress. */
    fun submitRatings() {
        if (ratingsSubmitState == SaveState.Saving) return
        ratingsSubmitState = SaveState.Saving
        val submitted = memberRatings
        val groupId = dinnerGroup?.groupId ?: "group_101"
        viewModelScope.launch {
            ratingsSubmitState = try {
                repository.submitRatings(groupId, submitted)
                // If a slider moved while saving, the newest values aren't saved yet.
                if (memberRatings == submitted) SaveState.Saved else SaveState.Idle
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Submitting ratings failed", e)
                SaveState.Failed
            }
        }
    }

    private companion object {
        const val TAG = "TableForViewModel"

        /** Dinner slots: start time and the label shown on the Schedule screen. */
        val TIME_SLOTS = listOf(
            LocalTime.of(17, 0) to "5:00 PM",
            LocalTime.of(17, 30) to "5:30 PM",
            LocalTime.of(18, 0) to "6:00 PM",
            LocalTime.of(18, 30) to "6:30 PM",
            LocalTime.of(19, 0) to "7:00 PM",
            LocalTime.of(19, 30) to "7:30 PM",
            LocalTime.of(20, 0) to "8:00 PM",
            LocalTime.of(20, 30) to "8:30 PM",
            LocalTime.of(21, 0) to "9:00 PM",
            LocalTime.of(21, 30) to "9:30 PM",
            LocalTime.of(22, 0) to "10:00 PM"
        )
    }
}

enum class SaveState { Idle, Saving, Saved, Failed }

