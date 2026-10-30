package com.metrolist.tablefor

import kotlinx.coroutines.flow.Flow

/**
 * Backend access for the ViewModel. [FirebaseTableForRepository] is the real
 * implementation; tests use a fake.
 *
 * The `get...Flow` listener streams fail (throw) when the backend reports an
 * error, such as denied access; they never crash the app themselves.
 *
 * Nothing is read or written without a signed-in user: listener streams end
 * at once, and writes do nothing or throw.
 *
 * Writes return when the server confirms them and throw if it rejects them.
 * Offline, Firestore queues writes on the phone and they wait for the connection.
 */
interface TableForRepository {
    val isFirebaseAvailable: Boolean

    /** Signs in if needed. Returns the user ID, or null if Firebase is unavailable or sign-in failed. */
    suspend fun signIn(): String?

    /** The group's messages, oldest first. Your own carry their [Delivery], reported again once the server has them. */
    fun getChatMessagesFlow(groupId: String): Flow<List<ChatMessage>>
    /**
     * Writes your message as the document [messageId], an ID chosen before sending, so the
     * message on screen and its document share it. Throws if the write fails.
     */
    suspend fun sendMessage(groupId: String, messageId: String, text: String, senderName: String)

    /**
     * Saves one slot under [slotKey] (see [AvailabilityKeys]) and deletes [staleKeys]
     * from the saved map, leaving the other slots as they are. Throws if the write fails.
     */
    suspend fun updateAvailability(slotKey: String, isAvailable: Boolean, staleKeys: Set<String>)
    /** Saved slots, keyed by [AvailabilityKeys]. */
    fun getAvailabilityFlow(userId: String): Flow<Map<String, Boolean>>

    /** Saves the editable profile fields, never trustRating or totalRatings. Throws if the write fails. */
    suspend fun saveProfile(profile: UserProfile)
    /** The saved profile, or null when the user has no profile document yet. */
    fun getUserProfileFlow(userId: String): Flow<UserProfile?>

    /** Writes all ratings in one batch, overwriting earlier ones. Throws if the write fails. */
    suspend fun submitRatings(groupId: String, ratings: List<MemberRating>)
}
