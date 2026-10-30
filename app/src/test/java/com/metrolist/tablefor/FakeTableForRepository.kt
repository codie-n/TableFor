package com.metrolist.tablefor

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import java.time.LocalDate

/** A listener stream the test drives by hand: emit values, or fail it like a denied Firestore listener. */
class FakeStream<T> {
    private val channel = Channel<T>(Channel.UNLIMITED)
    val flow: Flow<T> = channel.receiveAsFlow()

    fun emit(value: T) {
        channel.trySend(value)
    }

    fun fail(error: Throwable = RuntimeException("PERMISSION_DENIED: Missing or insufficient permissions.")) {
        channel.close(error)
    }
}

/** In-memory repository that records every call the ViewModel makes. */
class FakeTableForRepository(
    override val isFirebaseAvailable: Boolean = true,
) : TableForRepository {

    val profileStream = FakeStream<UserProfile?>()
    val availabilityStream = FakeStream<Map<String, Boolean>>()
    val chatStream = FakeStream<List<ChatMessage>>()

    /** Every repository call, in order, e.g. "saveProfile" or "getChatMessagesFlow(group_101)". */
    val calls = mutableListOf<String>()

    val savedProfiles = mutableListOf<UserProfile>()
    val availabilityWrites = mutableListOf<Triple<String, Boolean, LocalDate>>()
    val sentMessages = mutableListOf<String>()
    val ratingSubmissions = mutableListOf<List<MemberRating>>()

    /** When set, submitRatings waits for it, so a test can look at the "saving" state. */
    var submitGate: CompletableDeferred<Unit>? = null
    /** When set, submitRatings throws it, like a failed Firestore batch. */
    var submitError: Throwable? = null

    /** What signIn returns: the user ID, or null for a failed sign-in. */
    var signInResult: String? = "uid_test"
    /** When set, signIn throws it. */
    var signInError: Throwable? = null

    override suspend fun signIn(): String? {
        calls += "signIn"
        signInError?.let { throw it }
        return signInResult
    }

    override fun getChatMessagesFlow(groupId: String): Flow<List<ChatMessage>> {
        calls += "getChatMessagesFlow($groupId)"
        return chatStream.flow
    }

    override suspend fun sendMessage(groupId: String, text: String, senderName: String) {
        calls += "sendMessage"
        sentMessages += text
    }

    override suspend fun updateAvailability(slotKey: String, isAvailable: Boolean, today: LocalDate) {
        calls += "updateAvailability"
        availabilityWrites += Triple(slotKey, isAvailable, today)
    }

    override fun getAvailabilityFlow(userId: String): Flow<Map<String, Boolean>> {
        calls += "getAvailabilityFlow($userId)"
        return availabilityStream.flow
    }

    /** When set, saveProfile waits for it. */
    var saveProfileGate: CompletableDeferred<Unit>? = null
    /** When set, saveProfile throws it. */
    var saveProfileError: Throwable? = null

    override suspend fun saveProfile(profile: UserProfile) {
        calls += "saveProfile"
        saveProfileGate?.await()
        saveProfileError?.let { throw it }
        savedProfiles += profile
    }

    override fun getUserProfileFlow(userId: String): Flow<UserProfile?> {
        calls += "getUserProfileFlow($userId)"
        return profileStream.flow
    }

    override suspend fun submitRatings(groupId: String, ratings: List<MemberRating>) {
        calls += "submitRatings"
        submitGate?.await()
        submitError?.let { throw it }
        ratingSubmissions += ratings
    }
}
