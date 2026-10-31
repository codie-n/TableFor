package com.metrolist.tablefor

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.text.SimpleDateFormat
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

class TableForViewModel(
    private val repository: TableForRepository = FirebaseTableForRepository(),
    private val clock: Clock = PhoneClock
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

    /** The group as loaded. Your own entry in it is only a placeholder; [dinnerGroup] fills it in. */
    private var group by mutableStateOf<DinnerGroup?>(DinnerGroup())

    /** The group, with your entry always built from your current profile (see [yourEntry]). */
    val dinnerGroup: DinnerGroup?
        get() = group?.let { g ->
            g.copy(members = g.members.map { member -> if (member.id == myUserId) yourEntry(member) else member })
        }

    /** Sample messages in sample mode; otherwise only real ones, empty until the chat loads. */
    var chatMessages by mutableStateOf<List<ChatMessage>>(if (firebaseAvailable) emptyList() else SAMPLE_MESSAGES)
        private set

    /** False until the group chat has loaded, so an empty chat isn't announced too early. */
    var chatLoaded by mutableStateOf(!firebaseAvailable)
        private set

    /** The chat listener's latest messages. */
    private var listenedMessages: List<ChatMessage> = emptyList()

    /**
     * Your messages sent from this phone since the app started, by ID, with the delivery
     * their own write reported. Each shows even when the listener doesn't have it: not
     * reported yet, sent after the listener failed, or rejected and dropped by Firestore.
     */
    private val sentHere = LinkedHashMap<String, ChatMessage>()

    /** Messages that reached "Sent", by their write or the listener. They never go back to "Sending". */
    private val sentIds = HashSet<String>()

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

    /** The last saved availability map received from the server; null until the first one arrives. */
    private var savedSlots: Map<String, Boolean>? = null

    /** The first day of [availabilityGrid], which was today when the grid was built. */
    private var weekStart: LocalDate = LocalDate.now(clock)

    /** Rebuilds the week at local midnight while the app is in the foreground. */
    private var midnightTimer: Job? = null

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
                generateUpcomingWeekGrid() // now with the sample free slots
                profile = UserProfile()
                profileLoaded = true
                chatMessages = SAMPLE_MESSAGES
                chatLoaded = true
                return@launch
            }
            useSignedInUser(userId)
            syncStatus = SyncStatus.Synced

            // Each stream handles its own errors, so one failing never stops the others.
            syncStream("Profile", repository.getUserProfileFlow(userId)) { cloudProfile ->
                // No profile document yet: start blank, not with the sample profile.
                // A saved diet list holding "None" and real diets keeps the real ones.
                profile = cloudProfile?.let { it.copy(diets = Diets.cleaned(it.diets)) } ?: UserProfile.blank(userId)
                profileLoaded = true
            }

            val groupId = dinnerGroup?.groupId ?: "group_101"
            // Only real messages, even when there are none. A failure keeps what's shown.
            syncStream("Chat", repository.getChatMessagesFlow(groupId)) { cloudMessages ->
                listenedMessages = cloudMessages.map(::withSenderName)
                listenedMessages.filter { it.isMe && it.delivery == Delivery.Sent }.forEach { sentIds += it.id }
                showChat()
                chatLoaded = true
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

    /**
     * A new week from today. In sample mode it has the sample free slots; otherwise
     * every slot is busy until the saved ones arrive, so a signed-in user never sees
     * availability that isn't theirs.
     */
    private fun generateUpcomingWeekGrid() {
        weekStart = LocalDate.now(clock)
        val week = buildWeek(weekStart)
        availabilityGrid = if (sampleMode) week.map { it.copy(isAvailable = isSampleFreeSlot(it)) } else week
    }

    /** The sample free slots: days 3 and 4 at 7 and 8 PM. */
    private fun isSampleFreeSlot(cell: AvailabilityCell) =
        (cell.dayIndex == 3 || cell.dayIndex == 4) && (cell.timeSlot == "7:00 PM" || cell.timeSlot == "8:00 PM")

    /** Seven days of slots starting [today], all busy. */
    private fun buildWeek(today: LocalDate): List<AvailabilityCell> {
        val dateFormat = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault())
        val cells = mutableListOf<AvailabilityCell>()
        for (dayIdx in 0..6) {
            val date = today.plusDays(dayIdx.toLong())
            val dayLabel = date.format(dateFormat)
            for ((time, timeSlot) in TIME_SLOTS) {
                cells.add(AvailabilityCell(date = date, time = time, dayIndex = dayIdx, dayLabel = dayLabel, timeSlot = timeSlot, isAvailable = false))
            }
        }
        return cells
    }

    /** The app came to the foreground: catch up if the date changed, then watch for midnight. */
    fun onAppForeground() {
        refreshWeekIfDateChanged()
        startMidnightTimer()
    }

    /** The app went to the background: stop the midnight timer; [onAppForeground] catches up. */
    fun onAppBackground() {
        midnightTimer?.cancel()
        midnightTimer = null
    }

    /**
     * The phone's time or time zone was changed while the app is open: catch up
     * if the date changed, and restart the midnight timer for the new midnight.
     */
    fun onTimeChanged() {
        refreshWeekIfDateChanged()
        if (midnightTimer != null) startMidnightTimer()
    }

    /** (Re)starts the timer that rebuilds the week at local midnight. */
    private fun startMidnightTimer() {
        midnightTimer?.cancel()
        midnightTimer = viewModelScope.launch {
            while (true) {
                delay(millisUntilTomorrow())
                refreshWeekIfDateChanged()
            }
        }
    }

    private fun millisUntilTomorrow(): Long {
        val now = ZonedDateTime.now(clock)
        val tomorrow = now.toLocalDate().plusDays(1).atStartOfDay(now.zone)
        return Duration.between(now, tomorrow).toMillis().coerceAtLeast(1)
    }

    /**
     * If today isn't the first day of the grid any more, rebuilds the week from
     * today. Signed in, the slots come from the last saved map, and are all busy
     * until one arrives. In sample mode the days still in the week keep what they showed.
     */
    private fun refreshWeekIfDateChanged() {
        val today = LocalDate.now(clock)
        if (today == weekStart) return
        val shown = availabilityGrid.associate { it.slotKey to it.isAvailable }
        val saved = savedSlots
        availabilityGrid = buildWeek(today).map { cell ->
            cell.copy(
                isAvailable = when {
                    saved != null -> saved[cell.slotKey] == true
                    sampleMode -> shown[cell.slotKey] == true
                    else -> false
                }
            )
        }
        weekStart = today
    }

    /** Gives your entry in the (still mock) group the signed-in user's ID. */
    private fun useSignedInUser(userId: String) {
        group = group?.let { g ->
            g.copy(members = g.members.map { member ->
                if (member.id == myUserId) member.copy(id = userId) else member
            })
        }
        myUserId = userId
        refreshRatingList()
    }

    /**
     * Your group entry, from your current profile. While a signed-in user's
     * profile loads (or if it can't load), it's "You" with no score or badge;
     * after that, your name and your score, or the "New" badge with no ratings.
     */
    private fun yourEntry(placeholder: GroupMember): GroupMember =
        if (!profileLoaded) {
            placeholder.copy(name = "You", rating = null, diet = profileDiet(), isLoading = true)
        } else {
            placeholder.copy(
                name = profile.name.ifBlank { "You" },
                rating = profile.trustRating.takeIf { profile.totalRatings > 0 },
                diet = profileDiet(),
                isLoading = false
            )
        }

    private fun profileDiet() = profile.diets.joinToString(", ").ifEmpty { "None" }

    /** Everyone in the group except you, keeping any scores already set. */
    private fun refreshRatingList() {
        val group = group ?: return
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
        profile = newProfile
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
        profile = newProfile
        if (sampleMode) return
        launchWrite("Saving preferences") { repository.saveProfile(newProfile) }
    }

    fun toggleAvailability(dayIndex: Int, timeSlot: String) {
        val target = availabilityGrid.find { it.dayIndex == dayIndex && it.timeSlot == timeSlot } ?: return
        val newStatus = !target.isAvailable
        availabilityGrid = availabilityGrid.map { cell ->
            if (cell == target) cell.copy(isAvailable = newStatus) else cell
        }
        if (sampleMode) return
        // Old-format keys and past dates in the last saved map are deleted with this write.
        val staleKeys = AvailabilityKeys.staleKeys(savedSlots.orEmpty(), LocalDate.now(clock))
        launchWrite("Saving availability") {
            repository.updateAvailability(target.slotKey, newStatus, staleKeys)
        }
    }

    fun sendMessage(text: String) {
        if (text.isBlank() || !canChat) return
        val currentTimestamp = SimpleDateFormat("h:mm a", Locale.getDefault()).format(java.util.Date())
        val newMsg = ChatMessage(
            // Chosen before sending: the message on screen and its Firestore document share it.
            id = UUID.randomUUID().toString(),
            senderName = profile.name,
            text = text,
            timestamp = currentTimestamp,
            isMe = true,
            // In sample mode it never leaves the phone, so only the time shows.
            delivery = if (sampleMode) null else Delivery.Sending
        )
        if (sampleMode) {
            chatMessages = chatMessages + newMsg
            return
        }
        sentHere[newMsg.id] = newMsg
        showChat()

        val groupId = dinnerGroup?.groupId ?: "group_101"
        val senderName = profile.name
        viewModelScope.launch {
            // The write reports its own outcome, so delivery doesn't depend on the listener.
            // A rejection marks the message "Not sent" and leaves the sync strip alone.
            val outcome = try {
                repository.sendMessage(groupId, newMsg.id, text, senderName)
                Delivery.Sent
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Sending message failed", e)
                Delivery.NotSent
            }
            if (outcome == Delivery.Sent) sentIds += newMsg.id
            sentHere[newMsg.id] = newMsg.copy(delivery = outcome)
            showChat()
        }
    }

    /**
     * Shows the listener's messages, then your sends it doesn't have, in the order sent.
     * Each of yours shows the furthest delivery its write or the listener has reported.
     */
    private fun showChat() {
        val listed = listenedMessages.map { if (it.isMe) it.copy(delivery = deliveryOf(it)) else it }
        val listedIds = listed.mapTo(HashSet()) { it.id }
        val notListed = sentHere.values.filter { it.id !in listedIds }.map { it.copy(delivery = deliveryOf(it)) }
        chatMessages = listed + notListed
    }

    /** "Not sent" once the write is rejected; "Sent" once either source said so; otherwise as reported. */
    private fun deliveryOf(message: ChatMessage): Delivery? = when {
        sentHere[message.id]?.delivery == Delivery.NotSent -> Delivery.NotSent
        message.id in sentIds -> Delivery.Sent
        else -> message.delivery
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

        /** The chat in sample mode. */
        val SAMPLE_MESSAGES = listOf(
            ChatMessage("1", "Sarah Jenkins", "Hey everyone! Excited for dinner on Friday at Tanaka's", "6:45 PM", false),
            ChatMessage("2", "Marcus Vance", "Me too! Are we all good with meeting in front of the place?", "6:50 PM", false),
            ChatMessage("3", "Steve Brown", "Sounds great! See you all there.", "6:52 PM", true)
        )

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

