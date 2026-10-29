package com.metrolist.tablefor

import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * Backend access for the ViewModel. [FirebaseTableForRepository] is the real
 * implementation; tests use a fake.
 */
interface TableForRepository {
    val currentUserId: String
    val isFirebaseAvailable: Boolean

    suspend fun signInAnonymously()

    fun getChatMessagesFlow(groupId: String): Flow<List<ChatMessage>>
    suspend fun sendMessage(groupId: String, text: String, senderName: String)

    /**
     * Saves one slot under [slotKey] (see [AvailabilityKeys]) and drops old-format
     * keys and dates before [today] from the saved map.
     */
    suspend fun updateAvailability(slotKey: String, isAvailable: Boolean, today: LocalDate)
    /** Saved slots, keyed by [AvailabilityKeys]. */
    fun getAvailabilityFlow(userId: String = currentUserId): Flow<Map<String, Boolean>>

    suspend fun saveProfile(profile: UserProfile)
    fun getUserProfileFlow(userId: String = currentUserId): Flow<UserProfile?>

    /** Writes all ratings in one batch, overwriting earlier ones. Throws if the write fails. */
    suspend fun submitRatings(groupId: String, ratings: List<MemberRating>)
}
