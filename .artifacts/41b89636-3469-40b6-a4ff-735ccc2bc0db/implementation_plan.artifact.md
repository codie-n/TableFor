# TableFor Prototype App - Implementation Plan

TableFor is an Android prototype app that matches students, newcomers, and travelers into small groups (3-5 people) for casual dinners based on location, availability, dietary preferences, cuisine preferences, budgets, and reliability ratings. After matching, users join a group chat, and post-dinner they rate each other on punctuality, friendliness, and reliability.

## User Review Required

> [!IMPORTANT]
> Since this project currently contains only an empty structure, this implementation plan includes bootstrapping the Android Gradle configuration, Jetpack Compose UI setup, and all core feature screens.

- **Architecture**: MVVM with Jetpack Compose & Material 3, structured cleanly with data models, view models, and mock repositories for a fully interactive prototype experience.
- **Type Safety**: Utilizing type-safe navigation routes (Compose Navigation with Kotlin Serialization) for robust screen transitions and argument passing.
- **Adaptive & Responsive Layouts**: Designed to adapt gracefully across phone, foldables, and tablet screen sizes using responsive layouts and window size considerations.
- **Trust & Rating System**: Users are matched taking into account their reliability ratings (punctuality, friendliness, reliability) to help prevent no-shows and ensure quality group dynamics.
- **Spreadsheet Schedule UI**: 7-day upcoming weekly availability grid where days form the columns and dinner time slots form the rows for quick tap-to-toggle scheduling.
- **Location & Preferences**: Users specify their current city/neighborhood alongside diet, cuisine, and budget.

## Open Questions

- None at this stage.

## Proposed Changes

### Project Bootstrap & Configuration
- Create `settings.gradle.kts`, root `build.gradle.kts`, `app/build.gradle.kts`, `AndroidManifest.xml`, and baseline theme/MainActivity with Kotlin Serialization dependencies.

### Data Models & State Management
- `Model.kt`: Define type-safe data classes for `UserProfile` (including location & trust rating), 7-day spreadsheet `AvailabilitySlot`, `DinnerGroup`, `ChatMessage`, and `MemberRating`.
- `TableForViewModel.kt`: Manage state for user profile, location, 7-day schedule grid, matching status, reliability scores, active group chat, and ratings with mock repository data.

### UI & Navigation (Jetpack Compose)
- `MainActivity.kt` & Navigation Routes: Type-safe Compose navigation routes with responsive layouts linking the core screens:
  1. **Preferences & Profile Screen**: Edit location (city/neighborhood), diet, cuisine preferences, budget, bio, and view trust rating.
  2. **Schedule & Availability Screen**: Spreadsheet UI with 7 upcoming days as columns and time slots as rows for tap-to-toggle availability.
  3. **Matches & Groups Screen**: View matched dinner group (with reliability tiers/badges), restaurant assignment, time, and participant list.
  4. **Group Chat Screen**: Interactive messaging interface for group members.
  5. **Post-Dinner Rating Screen**: Rate group members on punctuality, friendliness, and reliability.

## Verification Plan

### Automated Tests
- Build and compile verification via Gradle build task.

### Manual Verification
- Run the app on an Android emulator / device to test type-safe navigation, 7-day spreadsheet availability scheduling, profile/location setup, viewing matched dinner group, group chat, and post-dinner ratings.
