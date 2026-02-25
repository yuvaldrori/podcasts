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

---

## Phase 1: Project Setup & Architecture
- [x] Initialize a new Android project with Jetpack Compose.
- [x] Set up navigation dependencies and a `BottomNavigation` layout (Tabs: Feeds, Queue, Settings).
- [ ] Configure Dependency Injection (e.g., Hilt) to manage database and player instances.

## Phase 2: Data & Networking Layer
- [ ] **Room Database setup:** Create tables for `Podcast` (subscription), `Episode` (metadata, download status), and `QueueState` (ordering).
- [ ] **RSS Fetching:** Set up network calls to fetch and parse podcast XML feeds.
- [ ] **Manual Refresh logic:** Build the repository logic to manually fetch and merge new episodes without overwriting the queue state.

## Phase 3: Background Audio & Downloading
- [ ] **Audio Downloading:** Implement a `WorkManager` task that downloads the audio file to internal app storage when an episode is added to the queue.
- [ ] **Media3 (ExoPlayer) setup:** Implement a `MediaSessionService` for robust background audio playback.
- [ ] **Playback Controls:** Add logic to handle 1x and 2x speed, play/pause, and seeking.
- [ ] **Auto-Cleanup:** Add an event listener that deletes the local audio file and removes the database record from the queue once playback completes.

## Phase 4: UI Implementation (Jetpack Compose)
- [ ] **Feeds Screen:** Create a list view of recently refreshed podcasts and their episodes.
- [ ] **Queue Screen:** Implement a drag-and-drop reorderable list for the active listening queue.
- [ ] **Player UI:** Build a persistent mini-player above the bottom nav, expanding to a full-screen player with speed controls.

## Phase 5: Import, Export, & Backup (Local/System)
- [ ] **Direct RSS Add:** Create a UI dialog to manually paste and validate an RSS feed URL.
- [ ] **OPML Import/Export:** Implement logic to parse an imported `.opml` file into Room DB subscriptions, and generate an `.opml` file using the system file picker (`Storage Access Framework`).
- [ ] **Manual DB Backup/Restore:** Use the system file picker (`ACTION_CREATE_DOCUMENT` and `ACTION_OPEN_DOCUMENT`) to let the user export a backup of their app state (subscriptions and queue) and restore it later.

## Phase 6: Polish & Testing
- [ ] Handle permissions (`POST_NOTIFICATIONS` for Android 13+, Foreground Service types for playback and downloading).
- [ ] Test background download resilience and media playback controls on the lock screen.
- [ ] Handle error states (e.g., no internet when refreshing, invalid RSS links).
