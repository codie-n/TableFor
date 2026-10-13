package com.metrolist.tablefor

import kotlinx.serialization.Serializable

@Serializable
data class UserProfile(
    val id: String = "user_1",
    val name: String = "Alex Chen",
    val bio: String = "New in town, love exploring food spots!",
    val location: String = "Downtown / Financial District",
    val diets: List<String> = listOf("Vegetarian"),
    val cuisinePreferences: List<String> = listOf("Italian", "Japanese", "Mexican"),
    val budget: String = "$$ (Moderate)",
    val trustRating: Float = 4.8f,
    val totalRatings: Int = 12
)

@Serializable
data class AvailabilityCell(
    val dayIndex: Int, // 0 to 6 (representing upcoming 7 days)
    val dayLabel: String, // e.g., "Mon, Oct 13"
    val timeSlot: String, // e.g., "6:30 PM"
    val isAvailable: Boolean
)

@Serializable
data class DinnerGroup(
    val groupId: String = "group_101",
    val restaurantName: String = "Trattoria Bella",
    val cuisine: String = "Italian",
    val neighborhood: String = "Downtown",
    val timeString: String = "Thursday, 7:00 PM",
    val members: List<GroupMember> = listOf(
        GroupMember("1", "Alex Chen", "You", 4.8f, "Vegetarian"),
        GroupMember("2", "Sarah Jenkins", "Student @ NYU", 4.9f, "None"),
        GroupMember("3", "Marcus Vance", "Software Engineer", 4.7f, "Vegan"),
        GroupMember("4", "Elena Rostova", "Designer", 5.0f, "Gluten-Free")
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
    val isMe: Boolean
)

data class MemberRating(
    val memberId: String,
    val memberName: String,
    val punctuality: Int = 5,
    val friendliness: Int = 5,
    val reliability: Int = 5,
    val notes: String = ""
)
