# 🎧 Podcast App - Developer Onboarding Guide

Welcome to the Podcast App project! This guide will help you understand the architecture, key libraries, and data flows within the application so you can start contributing quickly.

## 🏗️ Architecture Overview

This project strictly follows the **Modern Android Architecture** recommendations provided by Google. It is separated into distinct layers, ensuring that data is managed cleanly and the UI reacts to state changes via Kotlin Flows.

The app uses **Jetpack Compose** for a declarative UI and **Hilt** for Dependency Injection.

### 1. UI Layer (`com.yuval.podcasts.ui`)
*   **Screens**: Found in `ui/screens`. Each screen is a Compose function (e.g., `QueueScreen`, `SubscriptionsScreen`).
*   **ViewModels**: Found in `ui/viewmodel`. These map data from the domain/repository layer to UI states. They expose `StateFlow`s that the Compose screens collect.
*   **Components**: Reusable UI parts like `EpisodeItem` and the `UnifiedPlayer`.

### 2. Domain Layer (`com.yuval.podcasts.domain.usecase`)
This layer houses the core business logic. Use cases encapsulate specific, atomic actions (e.g., `EnqueueEpisodeUseCase`, `RemoveEpisodeUseCase`). This keeps ViewModels thin and makes complex logic highly testable.

### 3. Data Layer (`com.yuval.podcasts.data`)
*   **Repository (`PodcastRepository`)**: The single source of truth for the app. It decides whether to fetch data from the network or load it from the local database.
*   **Database (`db`)**: Uses **Room** to store `Podcast` and `Episode` entities. The app relies heavily on a `QueueDao` to manage the playback queue.
*   **Network (`network`)**: Uses **Retrofit** for generic API calls and a custom `RssParser` to parse podcast XML feeds.

---

## 🎵 Media Playback (`com.yuval.podcasts.media`)

Media playback is powered by **AndroidX Media3 (ExoPlayer)**. Because podcasts need to play in the background while the user does other things, the architecture here is split into two halves:

1.  **PlaybackService**: A foreground service that extends `MediaSessionService`. It holds the actual `ExoPlayer` instance and a `MediaSession`. It is responsible for the actual audio output, lock screen notifications, and handling hardware media buttons (like Bluetooth headphones). It continuously observes the database queue to sync its internal playlist.
2.  **PlayerManager**: This is a Singleton injected into the UI ViewModels. It acts as a client. It connects to the `PlaybackService` via a `MediaBrowser` and issues commands (Play, Pause, Seek). 

*Note: The UI never touches ExoPlayer directly. The UI talks to PlayerManager -> PlayerManager talks to the MediaBrowser -> the MediaBrowser talks to the PlaybackService -> PlaybackService controls ExoPlayer.*

---

## 🔄 Background Processing (`com.yuval.podcasts.work`)

The app uses **WorkManager** for asynchronous, guaranteed background tasks. 
*   **DownloadWorker**: Downloads podcast audio files to local storage for offline playback.
*   **OpmlImportWorker**: Parses external OPML files and subscribes the user to those feeds in the background.
*   **CleanupWorker**: Periodically removes old, played, or downloaded episodes to free up device storage.

---

## 🛠️ Key Technologies & SDKs

*   **Min SDK:** 36 (This app is extremely modern and targets only the latest Android environments. We do not use legacy `Compat` classes where native APIs are available).
*   **Language:** Kotlin (with heavily utilized Coroutines and Flow).
*   **UI Toolkit:** Jetpack Compose.
*   **Dependency Injection:** Dagger Hilt.
*   **Local Storage:** Room Database.
*   **Networking:** Retrofit & OkHttp.
*   **Media:** AndroidX Media3 (ExoPlayer).

---

## 🚀 Getting Started (How to build)

This project includes a convenient `Makefile` to streamline daily developer tasks. You don't need to memorize long Gradle commands.

### Useful Makefile Commands:
*   `make verify` - **Run this before pushing!** It executes a clean build, runs strict Android Lint (warnings treated as errors), executes all unit tests, and assembles the debug APK.
*   `make run` - Installs the debug APK on a connected device/emulator and launches the `MainActivity`.
*   `make test` - Runs all local unit tests.
*   `make lint` - Runs the strict lint checks independently.
*   `make emulator` - Spins up the default `test_avd` Android emulator in the background.

---

## 🧪 Testing Strategy

We rely heavily on local unit tests (Robolectric + MockK) rather than slow UI tests to ensure stability. Our tests are grouped logically by architectural layer:

1.  **Data Layer (`app/src/test/.../data/`)**:
    *   **DAOs**: We test Room queries using Robolectric to ensure SQL statements (especially complex Queue joins) are correct.
    *   **Network & Parsers**: We use mock XML payloads to ensure `RssParser` gracefully handles malformed feeds without crashing (`RssParserCrashTest`).
2.  **Domain Layer (`app/src/test/.../domain/`)**:
    *   Tests focus on single-responsibility use cases like verifying that adding an episode to the queue computes the proper index.
3.  **UI/ViewModels (`app/src/test/.../ui/`)**:
    *   We use the `Turbine` library to test Kotlin `StateFlow` emissions. We verify that when a ViewModel receives data from the repository, it correctly maps it into the UI State.
4.  **Media Engine (`app/src/test/.../media/`)**:
    *   This is the most critical test suite. We verify complex ExoPlayer behaviors, such as ensuring that `MediaButtonRemappingTest` intercepts Bluetooth taps correctly, and `PlaybackServiceQueueSyncTest` ensures that dynamically reordering the UI queue seamlessly shifts the ExoPlayer's internal playlist without dropping audio.

If you add a new feature or fix a bug, please ensure you add a corresponding unit test to the appropriate directory!