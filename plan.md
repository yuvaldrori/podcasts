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
- [x] **RSS Fetching:** Set up network calls to fetch and parse podcast XML feeds.
- [x] **Manual Refresh logic:** Build the repository logic to manually fetch and merge new episodes without overwriting the queue state.

## Phase 3: Background Audio & Downloading
- [x] **Audio Downloading:** Implement a `WorkManager` task that downloads the audio file to internal app storage when an episode is added to the queue.
- [x] **Media3 (ExoPlayer) setup:** Implement a `MediaSessionService` for robust background audio playback.
- [x] **Playback Controls:** Add logic to handle 1x and 2x speed, play/pause, and seeking.
- [x] **Auto-Cleanup:** Add an event listener that deletes the local audio file and removes the database record from the queue once playback completes.

## Phase 4: UI Implementation (Jetpack Compose)
- [ ] **Feeds Screen:** Create a list view of recently refreshed podcasts and their episodes.
- [ ] **Queue Screen:** Implement a drag-and-drop reorderable list for the active listening queue.
- [ ] **Player UI:** Build a persistent mini-player above the bottom nav, expanding to a full-screen player with speed controls.

## Phase 5: Import, Export, & Backup (Local/System)
- [x] **Direct RSS Add:** Create a UI dialog to manually paste and validate an RSS feed URL.
- [x] **OPML Import/Export:** Implement logic to parse an imported `.opml` file into Room DB subscriptions, and generate an `.opml` file using the system file picker (`Storage Access Framework`).
- [x] **Manual DB Backup/Restore:** Use the system file picker (`ACTION_CREATE_DOCUMENT` and `ACTION_OPEN_DOCUMENT`) to let the user export a backup of their app state (subscriptions and queue) and restore it later.

## Phase 6: Polish & Testing
- [x] Handle permissions (`POST_NOTIFICATIONS` for Android 13+, Foreground Service types for playback and downloading).
- [x] Test background download resilience and media playback controls on the lock screen.
- [x] Handle error states (e.g., no internet when refreshing, invalid RSS links).
