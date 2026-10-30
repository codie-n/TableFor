package com.metrolist.tablefor

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
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

    private val firebaseAvailable = repository.isFirebaseAvailable

    /** Sample data in sample mode; otherwise blank until the saved profile loads. */
    var profile by mutableStateOf(if (firebaseAvailable) UserProfile.blank("") else UserProfile())
        private set

    /** False until the saved profile (or the sample one) is in [profile]. Saving waits for it. */
    var profileLoaded by mutableStateOf(!firebaseAvailable)
        private set

    /** State of the last Save on the Profile screen. */
    var profileSaveState by mutableStateOf(SaveState.Idle)
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

    var syncStatus by mutableStateOf(
        if (firebaseAvailable) SyncStatus.Connecting else SyncStatus.SampleData
    )
        private set

    init {
        generateUpcomingWeekGrid()
        initRatings()
        initializeFirestoreSync()
    }

    private fun initializeFirestoreSync() {
        if (!firebaseAvailable) return // sample mode; syncStatus and profile already say so
        viewModelScope.launch {
            val userId = try {
                repository.signIn()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Sign-in failed; using sample data", e)
                null
            }
            if (userId == null) {
                syncStatus = SyncStatus.SampleData
                profile = UserProfile()
                profileLoaded = true
                return@launch
            }
            syncStatus = SyncStatus.Synced

            // Each stream handles its own errors, so one failing never stops the others.
            syncStream("Profile", repository.getUserProfileFlow()) { cloudProfile ->
                // No profile document yet: start blank, not with the sample profile.
                profile = cloudProfile ?: UserProfile.blank(userId)
                profileLoaded = true
            }

            val groupId = dinnerGroup?.groupId ?: "group_101"
            syncStream("Chat", repository.getChatMessagesFlow(groupId)) { cloudMessages ->
                if (cloudMessages.isNotEmpty()) {
                    chatMessages = cloudMessages
                }
            }

            // The grid shows exactly what's saved: old-format keys and past
            // dates match no cell, so they're ignored.
            syncStream("Availability", repository.getAvailabilityFlow()) { cloudSlots ->
                availabilityGrid = availabilityGrid.map { cell ->
                    cell.copy(isAvailable = cloudSlots[cell.slotKey] == true)
                }
            }
        }
    }

    /**
     * Collects one listener stream. If it fails, log it, keep the data already
     * shown and report [SyncStatus.SyncError]; the other streams keep running.
     */
    private fun <T> CoroutineScope.syncStream(name: String, stream: Flow<T>, onUpdate: (T) -> Unit) {
        launch {
            try {
                stream.collect { onUpdate(it) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "$name sync failed; keeping current data", e)
                syncStatus = SyncStatus.SyncError
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

    /** Save button: writes the whole profile and reports the result in [profileSaveState]. */
    fun saveProfile(newProfile: UserProfile) {
        if (!profileLoaded || profileSaveState == SaveState.Saving) return
        applyProfile(newProfile)
        profileSaveState = SaveState.Saving
        viewModelScope.launch {
            profileSaveState = try {
                repository.saveProfile(newProfile)
                SaveState.Saved
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Saving profile failed", e)
                SaveState.Failed
            }
        }
    }

    /** Diet, cuisine and budget taps: saved right away, without the Save button's message. */
    fun updateProfile(newProfile: UserProfile) {
        if (!profileLoaded) return
        applyProfile(newProfile)
        viewModelScope.launch {
            try {
                repository.saveProfile(newProfile)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Saving preferences failed", e)
            }
        }
    }

    /** Shows [newProfile] locally, including your entry in the group. */
    private fun applyProfile(newProfile: UserProfile) {
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

enum class SyncStatus {
    /** Signing in. */
    Connecting,
    /** Signed in, listeners running. */
    Synced,
    /** Firebase isn't set up or sign-in failed: the app shows sample data and saves nothing. */
    SampleData,
    /** A listener failed; the app keeps showing the data it already has. */
    SyncError
}

