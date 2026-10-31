package com.metrolist.tablefor

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.LocalTime

/**
 * Your ID before sign-in and in sample mode. Local only: it's never written to
 * Firestore. After sign-in, your group entry takes the Firebase user ID.
 */
const val LOCAL_USER_ID = "local_user"

@Serializable
data class UserProfile(
    val id: String = LOCAL_USER_ID,
    val name: String = "Steve Brown",
    val bio: String = "Just moved here, would like to meet new people",
    val location: String = "Honolulu",
    val diets: List<String> = listOf("None"),
    val cuisinePreferences: List<String> = listOf("Italian", "Japanese", "Mexican"),
    val budget: String = "$$ (Moderate)",
    val trustRating: Float = 4.8f,
    val totalRatings: Int = 12
) {
    companion object {
        /** A signed-in user with no saved profile yet. The defaults above are sample data. */
        fun blank(id: String) = UserProfile(
            id = id,
            name = "",
            bio = "",
            location = "",
            diets = emptyList(),
            cuisinePreferences = emptyList(),
            budget = "",
            trustRating = 0f,
            totalRatings = 0
        )
    }
}

@Serializable
data class AvailabilityCell(
    @Contextual val date: LocalDate, // the real calendar date of this slot
    @Contextual val time: LocalTime, // slot start, local time
    val dayIndex: Int, // 0 to 6: position in the upcoming week, for the day picker
    val dayLabel: String, // e.g., "Mon, Oct 13"
    val timeSlot: String, // e.g., "6:30 PM"
    val isAvailable: Boolean
) {
    /** Key this slot is saved under, e.g. "2026-10-30_19:00". */
    val slotKey: String get() = AvailabilityKeys.key(date, time)
}

@Serializable
data class DinnerGroup(
    val groupId: String = "group_101",
    val restaurantName: String = "Tanaka Ramen & Izakaya",
    val cuisine: String = "Japanese",
    val neighborhood: String = "Ala Moana",
    val address: String = "1450 Ala Moana Blvd #2054, Honolulu, HI 96814",
    val timeString: String = "Friday, 7:00 PM",
    val members: List<GroupMember> = listOf(
        GroupMember(LOCAL_USER_ID, "Steve Brown", "You", 4.8f, "None"),
        GroupMember("2", "Sarah Jenkins", "Student @ UHM", 4.9f, "None"),
        GroupMember("3", "Marcus Vance", "Software Engineer", 4.7f, "Vegan"),
        GroupMember("4", "Elena Rostova", "Designer", 5.0f, "None")
    )
)

@Serializable
data class GroupMember(
    val id: String,
    val name: String,
    val role: String,
    val rating: Float,
    val diet: String
)

@Serializable
data class ChatMessage(
    val id: String,
    val senderName: String,
    val text: String,
    val timestamp: String,
    val isMe: Boolean,
    val isRead: Boolean = true
)

data class MemberRating(
    val memberId: String,
    val memberName: String,
    val punctuality: Int = 5,
    val friendliness: Int = 5,
    val reliability: Int = 5,
    val notes: String = ""
)
