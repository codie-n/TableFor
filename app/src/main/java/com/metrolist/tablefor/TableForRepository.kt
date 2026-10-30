package com.metrolist.tablefor

import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * Backend access for the ViewModel. [FirebaseTableForRepository] is the real
 * implementation; tests use a fake.
 *
 * The `get...Flow` listener streams fail (throw) when the backend reports an
 * error, such as denied access; they never crash the app themselves.
 *
 * Nothing is read or written without a signed-in user: listener streams end
 * at once, and writes do nothing or throw.
 */
interface TableForRepository {
    val isFirebaseAvailable: Boolean

    /** Signs in if needed. Returns the user ID, or null if Firebase is unavailable or sign-in failed. */
    suspend fun signIn(): String?

    fun getChatMessagesFlow(groupId: String): Flow<List<ChatMessage>>
    suspend fun sendMessage(groupId: String, text: String, senderName: String)

    /**
     * Saves one slot under [slotKey] (see [AvailabilityKeys]) and drops old-format
     * keys and dates before [today] from the saved map.
     */
    suspend fun updateAvailability(slotKey: String, isAvailable: Boolean, today: LocalDate)
    /** Saved slots, keyed by [AvailabilityKeys]. */
    fun getAvailabilityFlow(userId: String): Flow<Map<String, Boolean>>

    /** Saves the editable profile fields, never trustRating or totalRatings. Throws if the write fails. */
    suspend fun saveProfile(profile: UserProfile)
    /** The saved profile, or null when the user has no profile document yet. */
    fun getUserProfileFlow(userId: String): Flow<UserProfile?>

    /** Writes all ratings in one batch, overwriting earlier ones. Throws if the write fails. */
    suspend fun submitRatings(groupId: String, ratings: List<MemberRating>)
}
