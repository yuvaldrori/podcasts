# 🎧 Podcast App - Developer Onboarding Guide

Welcome to the Podcast App project! This guide will help you understand the architecture, key libraries, and data flows within the application so you can start contributing quickly.

## 🏗️ Architecture Overview

This project strictly follows the **Modern Android Architecture** recommendations provided by Google. It is separated into distinct layers, ensuring that data is managed cleanly and the UI reacts to state changes via Kotlin Flows.

The app uses **Jetpack Compose** for a declarative UI and **Hilt** for Dependency Injection.

### 1. UI Layer (`com.yuval.podcasts.ui`)
*   **Screens**: Found in `ui/screens`. Each screen is a Compose function (e.g., `QueueScreen`, `SubscriptionsScreen`).
*   **ViewModels**: Found in `ui/viewmodel`. These map data from the domain/repository layer to UI states. They expose `StateFlow`s that the Compose screens collect.
*   **Components**: Reusable UI parts like `EpisodeItem`, `PodcastCover`, and the `UnifiedPlayer`.
*   **Dynamic Theme**: The app uses `androidx.palette` to extract colors from podcast artwork, providing a personalized Material You experience.

### 2. Domain Layer (`com.yuval.podcasts.domain.usecase`)
This layer houses the core business logic. Use cases encapsulate specific, atomic actions (e.g., `EnqueueEpisodeUseCase`, `RemoveEpisodeUseCase`). This keeps ViewModels thin and makes complex logic highly testable.

### 3. Data Layer (`com.yuval.podcasts.data`)
*   **Repository (`PodcastRepository`)**: The single source of truth for the app. It uses a bulk-synchronization pattern that merges network data with local state in memory before performing a single-transaction `@Upsert`.
*   **Database (`db`)**: Uses **Room** to store `Podcast`, `Episode`, and `Chapter` entities.
*   **History Sidecar**: To avoid complex migrations, history can be exported/imported as a JSON "Sidecar" file.
    *   **Placeholder Architecture**: The app supports importing history *before* podcasts are synced. It creates "placeholder" episodes that are later automatically merged with full network metadata during the next RSS sync.
*   **Network (`network`)**: Uses a custom `RssParser` with namespace awareness to parse RSS, itunes tags (artwork), and Podlove Chapters.

---

## 🎵 Media Playback (`com.yuval.podcasts.media`)

Media playback is powered by **AndroidX Media3 (ExoPlayer)**. 

1.  **PlaybackService**: A foreground service extending `MediaSessionService`. 
    *   **Reactivity**: It uses a non-blocking `asListenableFuture` bridge to resolve media items.
    *   **Interactive Notifications**: Supports custom commands (`REWIND_10`, `SKIP_30`) for enhanced lock-screen control.
    *   **Silence Trimming**: Toggled in real-time via `SettingsRepository` flows.
2.  **PlayerManager**: A Singleton injected into ViewModels that communicates with the service via `MediaBrowser`.

---

## 🔄 Background Processing (`com.yuval.podcasts.work`)

The app uses **WorkManager** for asynchronous, guaranteed background tasks. 
*   **DownloadWorker**: Downloads podcast audio files for offline playback.
*   **OpmlImportWorker**: Parses OPML files to bulk-subscribe to feeds.
*   **CleanupWorker**: Periodically removes old files to free up storage.

---

## 🛠️ Key Technologies & SDKs

*   **Min SDK:** 36 (Targets the latest Android environments).
*   **Language:** Kotlin (Coroutines and Flow).
*   **UI Toolkit:** Jetpack Compose & Material 3.
*   **Theming:** Palette API for dynamic colors.
*   **Local Storage:** Room Database.
*   **Media:** AndroidX Media3.
*   **Performance:** Macrobenchmark for UI smoothness.

---

## 🚀 Getting Started (How to build)

This project includes a convenient `Makefile` to streamline daily developer tasks.

### Useful Makefile Commands:
*   `make init` - Initialize the environment (SDK tools, licenses).
*   `make avd-init` - Create the Pixel 8 Pro AVD image.
*   `make emulator` - Start the optimized emulator in the background.
*   `make stop-emulator` - Stop the running emulator.
*   `make verify` - **Run this before pushing!** Clean build, lint, and all tests.
*   `make test` - Runs all local unit tests.
*   `make benchmark-run` - Runs macrobenchmarks on a connected device.

---

## 🧪 Testing Strategy

We maintain a comprehensive, multi-layered testing strategy:

1.  **Local Unit Tests (Robolectric + MockK)**: Covers DAOs, Parsers, Use Cases, and ViewModels.
2.  **Worker Integration Tests**: Verified using `TestListenableWorkerBuilder`.
3.  **Media Engine Tests**: Verifies ExoPlayer state transitions and custom session commands.
4.  **Performance Benchmarking**: Located in the `:benchmark` module to measure frame-drop regressions on main lists.

If you add a new feature or fix a bug, please ensure you add a corresponding test!
