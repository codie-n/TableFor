package com.metrolist.tablefor

import kotlinx.coroutines.flow.Flow

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

    suspend fun updateAvailability(dayIndex: Int, timeSlot: String, isAvailable: Boolean)
    fun getAvailabilityFlow(userId: String = currentUserId): Flow<Map<String, Boolean>>

    suspend fun saveProfile(profile: UserProfile)
    fun getUserProfileFlow(userId: String = currentUserId): Flow<UserProfile?>

    suspend fun submitRatings(groupId: String, ratings: List<MemberRating>)
}
