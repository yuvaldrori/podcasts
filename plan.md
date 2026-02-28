# Simple Android Podcast Player Plan

## Overview
A simple, manual-refresh Android podcast player built with modern Android development standards. 
It supports importing/exporting RSS feeds via OPML, direct RSS URL input, 1x/2x playback speeds, and a single drag-and-drop listening queue. It does not download or refresh feeds automatically in the background.

## Technology Stack
*   **UI Framework:** Jetpack Compose (Modern declarative UI)
*   **Media Engine:** AndroidX Media3 / ExoPlayer (Robust background playback & speed controls)
*   **Local Storage:** Room Database (SQLite)
*   **Background Tasks:** WorkManager (For downloading audio)
*   **Networking:** Retrofit + XML Parser (For fetching and parsing RSS feeds)

## Development Preferences
*   **Android Version:** Support Android 16 (API 36) and up. Drop support for previous versions to simplify system integration.
*   **Environment Variables:** Always use `.envrc` when environment variables (like `ANDROID_HOME` or `JAVA_HOME`) are needed.
*   **Package Installation:** If system-level packages (via `apt`) are required, ask the user to install them instead of attempting to do so or using alternatives.
*   **Local Tooling:** If a tool is needed and no suitable Ubuntu package exists, install it within the user's home directory.
*   **Version Control:** Use `git` to commit changes after successful implementation and verification of a task or sub-task.

## Development Workflow
*   **Command Line Interface:** Use the provided `Makefile` for all build, test, and verification tasks. 
*   **Maintenance:** Always keep the `Makefile` up-to-date with any new build configurations, testing suites, or deployment steps added to the project.

---

## Phase 1: Project Setup & Architecture
- [x] Initialize a new Android project with Jetpack Compose.
- [x] Set up navigation dependencies and a `BottomNavigation` layout (Tabs: Feeds, Queue, Settings).
- [x] Configure Dependency Injection (e.g., Hilt) to manage database and player instances.

## Phase 2: Data & Networking Layer
- [x] **Room Database setup:** Create tables for `Podcast` (subscription), `Episode` (metadata, download status), and `QueueState` (ordering).
- [x] **Playback History:** Add `isPlayed` (Boolean) and `lastPlayedPosition` (Long) to the `Episode` entity.
- [x] **Data Access:** Add DAO methods to fetch "New/Unplayed Episodes" across all podcasts and "All Episodes" for a specific podcast with playback status.
- [x] **RSS Fetching:** Set up network calls to fetch and parse podcast XML feeds.
- [x] **Manual Refresh logic:** Build the repository logic to manually fetch and merge new episodes without overwriting the queue state.

## Phase 3: Background Audio & Downloading
- [x] **Audio Downloading:** Implement a `WorkManager` task that downloads the audio file to internal app storage when an episode is added to the queue.
- [x] **Media3 (ExoPlayer) setup:** Implement a `MediaSessionService` for robust background audio playback.
- [x] **Playback Controls:** Add logic to handle 1x and 2x speed, play/pause, and seeking.
- [x] **Position Persistence:** Periodically save the current playback position to `lastPlayedPosition` when paused or stopped.
- [x] **Playback Resumption:** Resume playback from `lastPlayedPosition` when loading an episode.
- [x] **Auto-Cleanup:** Add an event listener that deletes the local audio file and removes the database record from the queue once playback completes.
- [x] **Completion Logic:** Update `isPlayed = true` and reset `lastPlayedPosition` in the database when an episode finishes.

## Phase 4: UI Implementation (Jetpack Compose)
- [x] **Navigation:** Set the **Queue Screen** as the default start destination.
- [x] **Queue Screen (Default):** Implement a drag-and-drop reorderable list for the active listening queue.
- [x] **New Episodes Screen:** Aggregated list of recent unplayed episodes from all podcasts with a 'Refresh' button and 'Add to Queue' action.
- [x] **Subscriptions Screen:** List view of all subscribed podcasts.
- [x] **Podcast Detail Screen:** Detailed list of all episodes for a specific podcast with "Played" markers and progress bars for partially played items.
- [x] **Player UI:** Build a persistent mini-player with speed controls (integrated in Queue screen).

## Phase 5: Import, Export, & Backup (Local/System)
- [x] **Direct RSS Add:** Create a UI dialog to manually paste and validate an RSS feed URL.
- [x] **OPML Import/Export:** Implement logic to parse an imported `.opml` file into Room DB subscriptions, and generate an `.opml` file using the system file picker (`Storage Access Framework`).
- [x] **Manual DB Backup/Restore:** Use the system file picker (`ACTION_CREATE_DOCUMENT` and `ACTION_OPEN_DOCUMENT`) to let the user export a backup of their app state (subscriptions and queue) and restore it later.

## Phase 6: Polish & Testing
- [x] Handle permissions (`POST_NOTIFICATIONS` for Android 13+, Foreground Service types for playback and downloading).
- [x] Test background download resilience and media playback controls on the lock screen.
- [x] Handle error states (e.g., no internet when refreshing, invalid RSS links).

## Phase 7: Feature Enhancements (Requested)
- [x] **Sort Subscriptions by Recent Update:** Update `PodcastDao` queries to sort the subscriptions list so that podcasts with the most recently published episodes appear at the top.
- [x] **"Dismiss All" in New Tab:** Add a "Dismiss All" action in the `NewEpisodesScreen` to remove all currently listed episodes from the "New" view (marking them as skipped/dismissed).
- [x] **Individual Episode Actions in New Tab:** Update the UI in the "New" tab to explicitly show two actions per episode: "Add to Queue" and "Dismiss/Reject" (removes it from the New tab without adding to the queue).
- [x] **Extended Playback Controls:** Enhance the player UI and `PlayerManager` to support playback speed toggling (1x / 2x) and skip controls (go back 30 seconds, go forward 30 seconds).
- [x] **Episode Duration & Position Display:** Update the playback controls to display the current playback position and total episode duration (e.g., '12:34 / 45:00').

## Phase 8: Feature Enhancements (Requested)
- [x] **Persistent Playback Speed:** Make 2X the default playback speed. If the user changes it, remember the last used setting across app restarts (e.g., using DataStore or SharedPreferences).
- [x] **Remove from New when Queued:** When adding an episode to the queue from the "New" tab, simultaneously remove it from the "New" tab (mark it as queued/dismissed from unplayed status).
- [x] **Add to Queue from Podcast Details:** Add a button in the individual podcast subscription page (Podcast Details) to add a specific episode to the queue.
- [x] **Prepend to Queue:** When adding an episode to the queue, always add it to the top (position 0) rather than the bottom, shifting existing items down.
- [x] **Drag & Drop Queue Reordering:** Allow users to rearrange the episode order in the queue tab by dragging and dropping items up and down.
- [x] **Emulator MCP Testing:** Test all new features using the Android emulator via the emulator MCP to verify UI interactions and state correctness.
- [x] **Show Episode Publish Date:** Display the publication date for each episode in the episode list views.
- [x] **Scrolling MiniPlayer Title:** Ensure the MiniPlayer scrolls the title of the currently playing podcast if it exceeds the available screen width.

## Phase 9: UX & Core Functionality Polish
- [x] **Visual Download State Indicators:** Add UI indicators to `EpisodeItem` showing `downloadStatus` (e.g., Cloud, Spinning, Checkmark) so users know when episodes are offline.
- [x] **Auto-Play Next in Queue:** Update `PlaybackService` to automatically load and play the next episode in the queue when the current one finishes.
- [x] **Swipe-to-Dismiss / Swipe-to-Queue:** Implement Jetpack Compose `SwipeToDismiss` gestures in the "New Episodes" and "Queue" tabs for faster management.
- [x] **Pull to Refresh:** Wrap the "New Episodes" list with a `pullRefresh` modifier to allow drag-to-refresh for podcast feeds.
- [x] **Artwork/Images in UI:** Utilize the Coil library to display podcast cover art thumbnails inside the `EpisodeItem` component.
- [x] **Episode Details Screen:** When tapping an episode item, navigate to a dedicated screen showing extended details from the RSS feed, including episode-specific artwork.

## Phase 10: Comprehensive Local Unit Testing
- [x] **ViewModels:** Add tests for `FeedsViewModel`, `QueueViewModel`, `EpisodeDetailViewModel`, and `SettingsViewModel` to verify state emissions and repository interactions using mocked dependencies and coroutine testing tools.
- [x] **Repositories:** Add tests for `PodcastRepository` and `SettingsRepository` to verify offline-first logic, network error handling, and preference management.
- [x] **Parsers & Data Managers:** Add tests for `OpmlManager` to ensure accurate parsing of OPML XML into subscription lists and correct generation of OPML XML from entities.
- [x] **Player State Logic:** Add tests for `PlayerManager` by mocking the underlying player to verify state management (e.g., play, pause, next) and event handling.
## Phase 11: Subscription Management
- [ ] **Unsubscribe Functionality:** Add a "Remove" or "Unsubscribe" button/action in the Subscriptions tab for each podcast.
- [ ] **Cascading Deletes:** Ensure that removing a podcast deletes its database record, removes all its episodes from the 'episodes' table, removes any associated items from the listening queue, and deletes downloaded audio files associated with those episodes.
