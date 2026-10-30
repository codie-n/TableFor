package com.metrolist.tablefor

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
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
    var profile by mutableStateOf(if (firebaseAvailable) UserProfile.blank(LOCAL_USER_ID) else UserProfile())
        private set

    /** False until the saved profile (or the sample one) is in [profile]. Saving waits for it. */
    var profileLoaded by mutableStateOf(!firebaseAvailable)
        private set

    /** State of the last Save on the Profile screen. */
    var profileSaveState by mutableStateOf(SaveState.Idle)
        private set

    /** You can chat once your saved profile has a name, so no message goes out without a sender. */
    val canChat: Boolean
        get() = profileLoaded && profile.name.isNotBlank()

    /** Your profile has loaded without a name: the chat asks you to add one on the Profile tab. */
    val chatNeedsName: Boolean
        get() = profileLoaded && profile.name.isBlank()

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

    /** Your ID: the Firebase user ID once signed in, [LOCAL_USER_ID] before that and in sample mode. */
    private var myUserId = LOCAL_USER_ID

    /** The last saved availability map received from the server. */
    private var savedSlots: Map<String, Boolean> = emptyMap()

    /** Count profile saves and rating submits, so only the latest one's result reaches the screen. */
    private var profileSaves = 0
    private var ratingSubmits = 0

    /** No Firebase or no signed-in user: everything stays on the device and nothing is read or written. */
    private val sampleMode: Boolean
        get() = syncStatus == SyncStatus.SampleData

    init {
        generateUpcomingWeekGrid()
        refreshRatingList()
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
            useSignedInUser(userId)
            syncStatus = SyncStatus.Synced

            // Each stream handles its own errors, so one failing never stops the others.
            syncStream("Profile", repository.getUserProfileFlow(userId)) { cloudProfile ->
                // No profile document yet: start blank, not with the sample profile.
                applyProfile(cloudProfile ?: UserProfile.blank(userId))
                profileLoaded = true
            }

            val groupId = dinnerGroup?.groupId ?: "group_101"
            syncStream("Chat", repository.getChatMessagesFlow(groupId)) { cloudMessages ->
                if (cloudMessages.isNotEmpty()) {
                    chatMessages = cloudMessages.map(::withSenderName)
                }
            }

            // The grid shows exactly what's saved: old-format keys and past
            // dates match no cell, so they're ignored.
            syncStream("Availability", repository.getAvailabilityFlow(userId)) { cloudSlots ->
                savedSlots = cloudSlots
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

    /** Gives your entry in the (still mock) group the signed-in user's ID. */
    private fun useSignedInUser(userId: String) {
        dinnerGroup = dinnerGroup?.let { group ->
            group.copy(members = group.members.map { member ->
                if (member.id == myUserId) member.copy(id = userId) else member
            })
        }
        myUserId = userId
        refreshRatingList()
    }

    /** Everyone in the group except you, keeping any scores already set. */
    private fun refreshRatingList() {
        val group = dinnerGroup ?: return
        val existing = memberRatings.associateBy { it.memberId }
        memberRatings = group.members.filter { it.id != myUserId }.map { member ->
            existing[member.id] ?: MemberRating(memberId = member.id, memberName = member.name)
        }
    }

    /**
     * Runs a background write that no screen waits for. A failure is logged and
     * shown in the sync strip; it never crashes the app.
     */
    private fun launchWrite(what: String, write: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                write()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "$what failed", e)
                syncStatus = SyncStatus.SyncError
            }
        }
    }

    /**
     * Runs a write that a screen reports on. Firestore queues a write on the phone
     * at once but finishes it only when the server confirms, which can't happen
     * offline. If the server hasn't confirmed within [SERVER_WAIT_MILLIS],
     * [onQueued] runs and the write carries on in the background.
     *
     * [onDone] gets the final outcome: null when the server confirmed, otherwise
     * the error. A rejection that arrives after [onQueued] also sets
     * [SyncStatus.SyncError], since the user may have left the screen by then.
     */
    private fun launchTrackedWrite(
        what: String,
        write: suspend () -> Unit,
        onQueued: () -> Unit,
        onDone: (error: Exception?) -> Unit
    ) {
        viewModelScope.launch {
            val outcome = async {
                try {
                    write()
                    null
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    e
                }
            }
            val confirmedInTime = withTimeoutOrNull(SERVER_WAIT_MILLIS) { outcome.join() } != null
            if (!confirmedInTime) onQueued()
            val error = outcome.await()
            if (error != null) {
                Log.w(TAG, "$what failed", error)
                if (!confirmedInTime) syncStatus = SyncStatus.SyncError
            }
            onDone(error)
        }
    }

    /** Save button: writes the whole profile and reports the result in [profileSaveState]. */
    fun saveProfile(newProfile: UserProfile) {
        if (!profileLoaded || profileSaveState == SaveState.Saving) return
        applyProfile(newProfile)
        if (sampleMode) {
            profileSaveState = SaveState.Saved
            return
        }
        profileSaveState = SaveState.Saving
        val save = ++profileSaves
        launchTrackedWrite(
            "Saving profile",
            write = { repository.saveProfile(newProfile) },
            onQueued = { if (save == profileSaves) profileSaveState = SaveState.SavedOnPhone },
            onDone = { error ->
                if (save == profileSaves) profileSaveState = if (error == null) SaveState.Saved else SaveState.Failed
            }
        )
    }

    /** Diet, cuisine and budget taps: saved right away, without the Save button's message. */
    fun updateProfile(newProfile: UserProfile) {
        if (!profileLoaded) return
        applyProfile(newProfile)
        if (sampleMode) return
        launchWrite("Saving preferences") { repository.saveProfile(newProfile) }
    }

    /** Shows [newProfile] locally, including your entry in the group. */
    private fun applyProfile(newProfile: UserProfile) {
        profile = newProfile
        dinnerGroup = dinnerGroup?.let { group ->
            val updatedMembers = group.members.map { member ->
                if (member.id == myUserId) {
                    member.copy(
                        name = newProfile.name.ifBlank { "You" },
                        rating = newProfile.trustRating.takeIf { newProfile.totalRatings > 0 },
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
        if (sampleMode) return
        // Old-format keys and past dates in the last saved map are deleted with this write.
        val staleKeys = AvailabilityKeys.staleKeys(savedSlots, LocalDate.now(clock))
        launchWrite("Saving availability") {
            repository.updateAvailability(target.slotKey, newStatus, staleKeys)
        }
    }

    fun sendMessage(text: String) {
        if (text.isBlank() || !canChat) return
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

        if (sampleMode) return
        val groupId = dinnerGroup?.groupId ?: "group_101"
        val senderName = profile.name
        launchWrite("Sending message") { repository.sendMessage(groupId, text, senderName) }
    }

    /** Others' messages with a blank or missing sender name show as [UNNAMED_SENDER]. */
    private fun withSenderName(message: ChatMessage): ChatMessage =
        if (!message.isMe && message.senderName.isBlank()) message.copy(senderName = UNNAMED_SENDER) else message

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
        if (sampleMode) {
            ratingsSubmitState = SaveState.Saved
            return
        }
        ratingsSubmitState = SaveState.Saving
        val submitted = memberRatings
        val groupId = dinnerGroup?.groupId ?: "group_101"
        val submit = ++ratingSubmits
        // If a slider moved after Submit, the newest values aren't saved yet: no message.
        launchTrackedWrite(
            "Submitting ratings",
            write = { repository.submitRatings(groupId, submitted) },
            onQueued = {
                if (submit == ratingSubmits) {
                    ratingsSubmitState = if (memberRatings == submitted) SaveState.SavedOnPhone else SaveState.Idle
                }
            },
            onDone = { error ->
                if (submit == ratingSubmits) {
                    ratingsSubmitState = when {
                        error != null -> SaveState.Failed
                        memberRatings == submitted -> SaveState.Saved
                        else -> SaveState.Idle
                    }
                }
            }
        )
    }

    private companion object {
        const val TAG = "TableForViewModel"

        /** How long Save and Submit wait for the server before saying "Saved on this phone". */
        const val SERVER_WAIT_MILLIS = 3_000L

        /** Shown for a chat sender with no name. */
        const val UNNAMED_SENDER = "Group member"

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

enum class SaveState {
    Idle,
    /** Waiting for the server, for a few seconds at most. */
    Saving,
    /** The server confirmed it. */
    Saved,
    /** Queued on the phone; Firestore sends it when the connection returns. */
    SavedOnPhone,
    Failed
}

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

