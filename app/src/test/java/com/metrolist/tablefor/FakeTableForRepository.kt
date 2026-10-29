package com.metrolist.tablefor

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

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
    override val currentUserId: String = "uid_test",
) : TableForRepository {

    val profileStream = FakeStream<UserProfile?>()
    val availabilityStream = FakeStream<Map<String, Boolean>>()
    val chatStream = FakeStream<List<ChatMessage>>()

    /** Every repository call, in order, e.g. "saveProfile" or "getChatMessagesFlow(group_101)". */
    val calls = mutableListOf<String>()

    val savedProfiles = mutableListOf<UserProfile>()
    val availabilityWrites = mutableListOf<Triple<Int, String, Boolean>>()
    val sentMessages = mutableListOf<String>()
    val ratingSubmissions = mutableListOf<List<MemberRating>>()

    override suspend fun signInAnonymously() {
        calls += "signInAnonymously"
    }

    override fun getChatMessagesFlow(groupId: String): Flow<List<ChatMessage>> {
        calls += "getChatMessagesFlow($groupId)"
        return chatStream.flow
    }

    override suspend fun sendMessage(groupId: String, text: String, senderName: String) {
        calls += "sendMessage"
        sentMessages += text
    }

    override suspend fun updateAvailability(dayIndex: Int, timeSlot: String, isAvailable: Boolean) {
        calls += "updateAvailability"
        availabilityWrites += Triple(dayIndex, timeSlot, isAvailable)
    }

    override fun getAvailabilityFlow(userId: String): Flow<Map<String, Boolean>> {
        calls += "getAvailabilityFlow($userId)"
        return availabilityStream.flow
    }

    override suspend fun saveProfile(profile: UserProfile) {
        calls += "saveProfile"
        savedProfiles += profile
    }

    override fun getUserProfileFlow(userId: String): Flow<UserProfile?> {
        calls += "getUserProfileFlow($userId)"
        return profileStream.flow
    }

    override suspend fun submitRatings(groupId: String, ratings: List<MemberRating>) {
        calls += "submitRatings"
        ratingSubmissions += ratings
    }
}
