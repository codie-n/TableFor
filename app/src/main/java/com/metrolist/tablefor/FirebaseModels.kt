package com.metrolist.tablefor

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp

/**
 * Firestore data model for user profiles.
 * Stored at: /users/{userId}
 */
data class FirestoreUserProfile(
    @DocumentId val id: String = "",
    val name: String = "",
    val bio: String = "",
    val location: String = "",
    val diets: List<String> = emptyList(),
    val cuisinePreferences: List<String> = emptyList(),
    val budget: String = "",
    val trustRating: Double = 5.0,
    val totalRatings: Int = 0,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toUserProfile(): UserProfile {
        return UserProfile(
            id = id,
            name = name,
            bio = bio,
            location = location,
            diets = diets,
            cuisinePreferences = cuisinePreferences,
            budget = budget,
            trustRating = trustRating.toFloat(),
            totalRatings = totalRatings
        )
    }

    companion object {
        fun fromUserProfile(user: UserProfile): FirestoreUserProfile {
            return FirestoreUserProfile(
                id = user.id,
                name = user.name,
                bio = user.bio,
                location = user.location,
                diets = user.diets,
                cuisinePreferences = user.cuisinePreferences,
                budget = user.budget,
                trustRating = user.trustRating.toDouble(),
                totalRatings = user.totalRatings
            )
        }
    }
}

/**
 * Firestore data model for user schedule / weekly availability.
 * Stored at: /schedules/{userId}
 */
data class FirestoreSchedule(
    @DocumentId val userId: String = "",
    val cells: List<FirestoreAvailabilityCell> = emptyList(),
    @ServerTimestamp val updatedAt: Timestamp? = null
)

data class FirestoreAvailabilityCell(
    val dayIndex: Int = 0,
    val dayLabel: String = "",
    val timeSlot: String = "",
    val isAvailable: Boolean = false
) {
    fun toAvailabilityCell(): AvailabilityCell {
        return AvailabilityCell(
            dayIndex = dayIndex,
            dayLabel = dayLabel,
            timeSlot = timeSlot,
            isAvailable = isAvailable
        )
    }

    companion object {
        fun fromAvailabilityCell(cell: AvailabilityCell): FirestoreAvailabilityCell {
            return FirestoreAvailabilityCell(
                dayIndex = cell.dayIndex,
                dayLabel = cell.dayLabel,
                timeSlot = cell.timeSlot,
                isAvailable = cell.isAvailable
            )
        }
    }
}

/**
 * Firestore data model for matched dinner groups.
 * Stored at: /dinner_groups/{groupId}
 */
data class FirestoreDinnerGroup(
    @DocumentId val groupId: String = "",
    val restaurantName: String = "",
    val cuisine: String = "",
    val neighborhood: String = "",
    val address: String = "",
    val timeString: String = "",
    val status: String = "SCHEDULED", // SCHEDULED, COMPLETED, CANCELLED
    val memberIds: List<String> = emptyList(),
    val members: List<FirestoreGroupMember> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null
) {
    fun toDinnerGroup(): DinnerGroup {
        return DinnerGroup(
            groupId = groupId,
            restaurantName = restaurantName,
            cuisine = cuisine,
            neighborhood = neighborhood,
            address = address,
            timeString = timeString,
            members = members.map { it.toGroupMember() }
        )
    }

    companion object {
        fun fromDinnerGroup(group: DinnerGroup): FirestoreDinnerGroup {
            return FirestoreDinnerGroup(
                groupId = group.groupId,
                restaurantName = group.restaurantName,
                cuisine = group.cuisine,
                neighborhood = group.neighborhood,
                address = group.address,
                timeString = group.timeString,
                memberIds = group.members.map { it.id },
                members = group.members.map { FirestoreGroupMember.fromGroupMember(it) }
            )
        }
    }
}

data class FirestoreGroupMember(
    val id: String = "",
    val name: String = "",
    val role: String = "",
    val rating: Double = 5.0,
    val diet: String = ""
) {
    fun toGroupMember(): GroupMember {
        return GroupMember(
            id = id,
            name = name,
            role = role,
            rating = rating.toFloat(),
            diet = diet
        )
    }

    companion object {
        fun fromGroupMember(member: GroupMember): FirestoreGroupMember {
            return FirestoreGroupMember(
                id = member.id,
                name = member.name,
                role = member.role,
                rating = member.rating.toDouble(),
                diet = member.diet
            )
        }
    }
}

/**
 * Firestore data model for group chat messages.
 * Stored at: /dinner_groups/{groupId}/messages/{messageId}
 */
data class FirestoreChatMessage(
    @DocumentId val id: String = "",
    val groupId: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val text: String = "",
    val timestamp: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
) {
    fun toChatMessage(currentUserId: String): ChatMessage {
        return ChatMessage(
            id = id,
            senderName = senderName,
            text = text,
            timestamp = timestamp,
            isMe = (senderId == currentUserId),
            isRead = true
        )
    }

    companion object {
        fun fromChatMessage(groupId: String, senderId: String, msg: ChatMessage): FirestoreChatMessage {
            return FirestoreChatMessage(
                id = msg.id,
                groupId = groupId,
                senderId = senderId,
                senderName = msg.senderName,
                text = msg.text,
                timestamp = msg.timestamp
            )
        }
    }
}

/**
 * Firestore data model for post-dinner member ratings.
 * Stored at: /dinner_groups/{groupId}/ratings/{ratingId}
 */
data class FirestoreMemberRating(
    @DocumentId val ratingId: String = "",
    val groupId: String = "",
    val raterUserId: String = "",
    val targetUserId: String = "",
    val memberName: String = "",
    val punctuality: Int = 5,
    val friendliness: Int = 5,
    val reliability: Int = 5,
    val notes: String = "",
    @ServerTimestamp val submittedAt: Timestamp? = null
) {
    fun toMemberRating(): MemberRating {
        return MemberRating(
            memberId = targetUserId,
            memberName = memberName,
            punctuality = punctuality,
            friendliness = friendliness,
            reliability = reliability,
            notes = notes
        )
    }

    companion object {
        fun fromMemberRating(groupId: String, raterId: String, rating: MemberRating): FirestoreMemberRating {
            return FirestoreMemberRating(
                ratingId = "${groupId}_${raterId}_${rating.memberId}",
                groupId = groupId,
                raterUserId = raterId,
                targetUserId = rating.memberId,
                memberName = rating.memberName,
                punctuality = rating.punctuality,
                friendliness = rating.friendliness,
                reliability = rating.reliability,
                notes = rating.notes
            )
        }
    }
}
