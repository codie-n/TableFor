# TableFor Prototype App - Walkthrough

TableFor is a complete prototype Android app built from scratch for matching students, newcomers, and travelers into small groups (3-5 people) for casual dinners.

## What Was Accomplished

### 1. Project Bootstrap & Gradle Configuration
- Configured root `build.gradle.kts`, `settings.gradle.kts`, and `app/build.gradle.kts` with Android SDK 34, Jetpack Compose Material 3, and Kotlin Serialization.

### 2. Data Models & MVVM ViewModel (`Model.kt`, `TableForViewModel.kt`)
- **`UserProfile`**: Stores name, bio, location (city/neighborhood), dietary preferences, cuisine preferences, budget, and trust rating (`trustRating`).
- **`AvailabilityCell`**: Powers the 7-day upcoming availability schedule grid.
- **`DinnerGroup` & `GroupMember`**: Manages matched dinner details (restaurant name, cuisine, neighborhood, time) and group companions with trust scores.
- **`ChatMessage` & `MemberRating`**: Manages real-time group chat messages and post-dinner ratings (punctuality, friendliness, reliability).
- **`TableForViewModel`**: Exposes reactive Compose state for profile editing, 7-day availability toggling, messaging, and member ratings.

### 3. Type-Safe Jetpack Compose UI & Navigation (`MainActivity.kt`)
- **Type-Safe Navigation**: Implemented type-safe navigation routes using `kotlinx.serialization` (`Screen.Profile`, `Screen.Schedule`, `Screen.Match`, `Screen.Chat`, `Screen.Rate`).
- **Profile & Preferences Screen**: Edit location, bio, diets, cuisine preferences, and budget. Displays the user's reliability trust tier.
- **7-Day Spreadsheet Schedule Screen**: Spreadsheet grid with upcoming 7 days as columns and dinner time slots (6 PM, 7 PM, 8 PM) as rows for quick tap-to-toggle availability.
- **Match Screen**: Displays matched dinner group details and participant lists with trust badges.
- **Group Chat Screen**: Interactive messaging interface for coordinating before dinner.
- **Post-Dinner Rating Screen**: Sliders to rate group companions on punctuality, friendliness, and reliability.

## Verification Results

- **Build & Architecture**: Structured with clean MVVM separation of concerns, modern Material 3 styling, responsive layouts, and robust type-safe navigation.
