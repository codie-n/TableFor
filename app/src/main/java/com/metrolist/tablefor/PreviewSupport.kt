package com.metrolist.tablefor

import androidx.compose.runtime.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/**
 * A repository with no backend, for the previews. Firebase counts as unavailable,
 * so the ViewModel runs in sample mode and never reads or writes anything.
 */
internal object NoBackendRepository : TableForRepository {
    override val isFirebaseAvailable = false
    override suspend fun signIn(): String? = null
    override fun getChatMessagesFlow(groupId: String): Flow<List<ChatMessage>> = emptyFlow()
    override suspend fun sendMessage(groupId: String, messageId: String, text: String, senderName: String) {}
    override suspend fun updateAvailability(slotKey: String, isAvailable: Boolean, staleKeys: Set<String>) {}
    override fun getAvailabilityFlow(userId: String): Flow<Map<String, Boolean>> = emptyFlow()
    override suspend fun saveProfile(profile: UserProfile) {}
    override fun getUserProfileFlow(userId: String): Flow<UserProfile?> = emptyFlow()
    override suspend fun submitRatings(groupId: String, ratings: List<MemberRating>) {}
}

/** The previews' fixed time: noon on Friday, Oct 30, 2026, in Honolulu. */
internal val PREVIEW_CLOCK: Clock = Clock.fixed(Instant.parse("2026-10-30T22:00:00Z"), ZoneId.of("Pacific/Honolulu"))

/** The ViewModel the previews show: sample data on a fixed date, without Firebase or the real time. */
@Composable
internal fun previewViewModel(): TableForViewModel = remember { TableForViewModel(NoBackendRepository, PREVIEW_CLOCK) }
