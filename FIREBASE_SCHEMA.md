# TableFor - Firebase Database & Architecture Schema

This document defines the complete Firebase Cloud Firestore database structure, security rules, and integration architecture for the **TableFor** Android application.

---

## 1. Cloud Firestore Architecture Overview

The database is designed around Cloud Firestore with 4 main domain entities:
1. **Users (`users`)**: User profiles, preferences, dietary restrictions, location, and trust ratings.
2. **Schedules (`schedules`)**: Upcoming week availability grids per user.
3. **Dinner Groups (`dinner_groups`)**: Matched dining groups with restaurant info, meeting time, status, and members.
4. **Group Chat (`dinner_groups/{groupId}/messages`)**: Real-time group messaging subcollection.
5. **Member Ratings (`dinner_groups/{groupId}/ratings`)**: Post-dinner member feedback and trust metrics.

---

## 2. Collections & Subcollections Hierarchy

```
cloud_firestore
├── users (Collection)
│   └── {userId} (Document)
│
├── schedules (Collection)
│   └── {userId} (Document)
│
└── dinner_groups (Collection)
    └── {groupId} (Document)
        ├── messages (Subcollection)
        │   └── {messageId} (Document)
        │
        └── ratings (Subcollection)
            └── {ratingId} (Document)
```

---

## 3. Detailed Data Schemas

### 3.1. `users` Collection
- **Document Path**: `/users/{userId}`
- **Purpose**: Stores user profile details and preference parameters used by the matching engine.

| Field | Type | Description |
|---|---|---|
| `id` | String | Unique user ID (matches Firebase Auth UID) |
| `name` | String | Full name / Display name |
| `bio` | String | User bio / intro string |
| `location` | String | Preferred city neighborhood / district |
| `diets` | Array<String> | Dietary preferences (e.g., `["Vegetarian", "Gluten-Free"]`) |
| `cuisinePreferences` | Array<String> | Preferred cuisines (e.g., `["Italian", "Japanese"]`) |
| `budget` | String | Budget indicator (e.g., `"$$ (Moderate)"`) |
| `trustRating` | Number (Double) | Average trust score (1.0 to 5.0) |
| `totalRatings` | Number (Int) | Count of post-dinner ratings received |
| `createdAt` | Timestamp | Account creation timestamp |
| `updatedAt` | Timestamp | Profile last update timestamp |

#### Example Document (`/users/user_1`):
```json
{
  "id": "user_1",
  "name": "Alex Chen",
  "bio": "New in town, love exploring food spots!",
  "location": "Downtown / Financial District",
  "diets": ["Vegetarian"],
  "cuisinePreferences": ["Italian", "Japanese", "Mexican"],
  "budget": "$$ (Moderate)",
  "trustRating": 4.8,
  "totalRatings": 12,
  "createdAt": "2026-10-30T10:00:00Z",
  "updatedAt": "2026-10-30T12:30:00Z"
}
```

---

### 3.2. `schedules` Collection
- **Document Path**: `/schedules/{userId}`
- **Purpose**: Tracks user's available time slots across the upcoming 7 days.

| Field | Type | Description |
|---|---|---|
| `userId` | String | User ID owning this availability grid |
| `cells` | Array<Map> | Array of availability cells |
| └ `dayIndex` | Number (Int) | 0 to 6 representing upcoming days |
| └ `dayLabel` | String | Date string (e.g., `"Mon, Oct 13"`) |
| └ `timeSlot` | String | Time slot string (e.g., `"7:00 PM"`) |
| └ `isAvailable` | Boolean | True if user is available for dinner |
| `updatedAt` | Timestamp | Schedule last update timestamp |

#### Example Document (`/schedules/user_1`):
```json
{
  "userId": "user_1",
  "cells": [
    {
      "dayIndex": 3,
      "dayLabel": "Thu, Oct 16",
      "timeSlot": "7:00 PM",
      "isAvailable": true
    },
    {
      "dayIndex": 3,
      "dayLabel": "Thu, Oct 16",
      "timeSlot": "8:00 PM",
      "isAvailable": true
    }
  ],
  "updatedAt": "2026-10-30T14:15:00Z"
}
```

---

### 3.3. `dinner_groups` Collection
- **Document Path**: `/dinner_groups/{groupId}`
- **Purpose**: Holds details for a matched group dinner.

| Field | Type | Description |
|---|---|---|
| `groupId` | String | Unique group ID |
| `restaurantName` | String | Restaurant selected for the group |
| `cuisine` | String | Cuisine type of the restaurant |
| `neighborhood` | String | Neighborhood location |
| `address` | String | Full venue address |
| `timeString` | String | Human readable date & time (e.g., `"Thursday, 7:00 PM"`) |
| `status` | String | State: `"SCHEDULED"`, `"COMPLETED"`, or `"CANCELLED"` |
| `memberIds` | Array<String> | List of member user IDs (for fast security rule validation) |
| `members` | Array<Map> | Detailed member info objects |
| └ `id` | String | Member user ID |
| └ `name` | String | Member display name |
| └ `role` | String | Role / bio headline |
| └ `rating` | Number (Double) | Trust score rating |
| └ `diet` | String | Dietary restriction summary |
| `createdAt` | Timestamp | Group creation timestamp |

#### Example Document (`/dinner_groups/group_101`):
```json
{
  "groupId": "group_101",
  "restaurantName": "Trattoria Bella",
  "cuisine": "Italian",
  "neighborhood": "Downtown",
  "address": "142 5th Ave, New York, NY 10011",
  "timeString": "Thursday, 7:00 PM",
  "status": "SCHEDULED",
  "memberIds": ["1", "2", "3", "4"],
  "members": [
    { "id": "1", "name": "Alex Chen", "role": "You", "rating": 4.8, "diet": "Vegetarian" },
    { "id": "2", "name": "Sarah Jenkins", "role": "Student @ NYU", "rating": 4.9, "diet": "None" },
    { "id": "3", "name": "Marcus Vance", "role": "Software Engineer", "rating": 4.7, "diet": "Vegan" },
    { "id": "4", "name": "Elena Rostova", "role": "Designer", "rating": 5.0, "diet": "Gluten-Free" }
  ],
  "createdAt": "2026-10-28T09:00:00Z"
}
```

---

### 3.4. `messages` Subcollection
- **Document Path**: `/dinner_groups/{groupId}/messages/{messageId}`
- **Purpose**: Real-time group chat messages for dinner group coordination.

| Field | Type | Description |
|---|---|---|
| `id` | String | Message ID |
| `groupId` | String | Target dinner group ID |
| `senderId` | String | User ID of the sender |
| `senderName` | String | Sender name |
| `text` | String | Message content |
| `timestamp` | String | Formatted timestamp string (e.g. `"6:45 PM"`) |
| `createdAt` | Timestamp | Server timestamp for chronological sorting |

#### Example Document (`/dinner_groups/group_101/messages/msg_1`):
```json
{
  "id": "1",
  "groupId": "group_101",
  "senderId": "2",
  "senderName": "Sarah Jenkins",
  "text": "Hey everyone! Excited for dinner on Thursday at Trattoria Bella!",
  "timestamp": "6:45 PM",
  "createdAt": "2026-10-30T18:45:00Z"
}
```

---

### 3.5. `ratings` Subcollection
- **Document Path**: `/dinner_groups/{groupId}/ratings/{ratingId}`
- **Purpose**: Stores post-dinner feedback provided by members for fellow attendees.

| Field | Type | Description |
|---|---|---|
| `ratingId` | String | ID formatted as `{groupId}_{raterUserId}_{targetUserId}` |
| `groupId` | String | Associated dinner group ID |
| `raterUserId` | String | User ID of the reviewer |
| `targetUserId` | String | User ID of member being reviewed |
| `memberName` | String | Name of member being reviewed |
| `punctuality` | Number (Int) | Punctuality score (1 to 5) |
| `friendliness` | Number (Int) | Friendliness score (1 to 5) |
| `reliability` | Number (Int) | Reliability score (1 to 5) |
| `notes` | String | Optional comments or feedback notes |
| `submittedAt` | Timestamp | Rating submission timestamp |

#### Example Document (`/dinner_groups/group_101/ratings/group_101_1_2`):
```json
{
  "ratingId": "group_101_1_2",
  "groupId": "group_101",
  "raterUserId": "1",
  "targetUserId": "2",
  "memberName": "Sarah Jenkins",
  "punctuality": 5,
  "friendliness": 5,
  "reliability": 5,
  "notes": "Great conversation and on time!",
  "submittedAt": "2026-10-30T21:00:00Z"
}
```

---

## 4. Security Rules (`firestore.rules`)

The security rules enforce:
- Authenticated access to profiles.
- Profile and schedule modifications restricted to document owners.
- Group chat message creation and read permissions strictly restricted to members of that specific `dinner_group`.
- Post-dinner member ratings submission restricted to group participants.

---

## 5. Android Integration Files

- `FirebaseModels.kt`: Data classes with Firestore annotations (`@DocumentId`, `@ServerTimestamp`).
- `FirebaseRepository.kt`: Asynchronous data layer using Kotlin `Flow` and Coroutines for snapshot listeners and writes.
- `TableForViewModel.kt`: UI state management seamlessly connected to `FirebaseRepository`.
