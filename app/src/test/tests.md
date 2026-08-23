# Podcasts App Test Documentation

This document explains what each automated test in the project does in simple, human-readable terms. 
The tests are split into two main categories: **Local Unit Tests** (which run fast on your computer) and **Instrumented UI Tests** (which run on an Android emulator/device).

## 🗄️ Database Tests (DAOs & Migrations)
*Located in: `app/src/test/java/com/yuval/podcasts/data/db/dao/` and `app/src/androidTest/java/com/yuval/podcasts/data/db/`*

These tests ensure the local Room database saves, retrieves, and migrates information correctly.

*   **`EpisodeDaoTest`**: Verifies that we can insert, update, and delete podcast episodes. It checks that we can correctly fetch a list of unplayed episodes, verifies that queued episodes are excluded from the unplayed episodes feed, and specifically verifies that `updateDownloadStatusAfterSuccess` only updates the download path and status if the current status matches the expected downloading state.
*   **`PodcastDaoTest`**: Checks that adding and removing Podcast subscriptions works, and ensures that fetching "all podcasts" returns the correct list ordered alphabetically.
*   **`QueueDaoTest`**: Confirms that adding episodes to the play queue, reordering the queue, and removing items from the queue behaves as expected in the database. Specifically verifies the position-shifting logic for efficient insertions, and confirms the parameterized querying of queued episodes that are not yet downloaded.
*   **`ChapterDaoTest`**: Verifies that we can bulk delete and update chapters for multiple episodes in a single operation to maintain performance during synchronization.
*   **`DatabaseSchemaIntegrityTest`**: Verifies that Room database schema versions, entity column definitions, and `AutoMigration` declarations in `AppDatabase.kt` match exported JSON schema files. Ensures any schema modification requires an explicit version bump and migration path to prevent runtime crashes on upgraded devices.
*   **`DatabaseSchemaMigrationTest`**: Verifies Room database schema migrations (such as version 8 to 9) using `MigrationTestHelper` to ensure primary keys, column types, and indices match entity definitions and preserve existing user data.
*   **`AppDatabaseMigrationTest`**: Ensures that user data (like episodes and playback positions) is preserved during database updates. It specifically verifies the complex migration from version 5 to 6 where the primary key structure changed.

## 🌐 Network Tests
*Located in: `app/src/test/java/com/yuval/podcasts/data/network/`*

These tests ensure the app can talk to the internet and understand the podcast data it gets back.

*   **`PodcastApiIntegrationTest`**: Makes sure that if the app is trying to download an RSS feed and the user cancels the action (like closing the app), the network request stops immediately to save data and battery.
*   **`PodcastRemoteDataSourceTest`**: Verifies that the remote data source correctly orchestrates the network fetch and RSS parsing into clean data models.
*   **`RssParserTest`**: Feeds fake XML files (RSS feeds) to the parser to ensure it correctly extracts the podcast title, episode names, audio links, publication dates, and artwork. It specifically verifies the robust handling of CDATA sections, HTML entities, and schemeless URLs (e.g., adding "https:" to "//example.com"), automatically upgrades insecure `http:` links to `https:` (for podcasts, episodes, images, and audio) to comply with modern Android security restrictions, and ensures that any HTML tags and entities in podcast titles, channel descriptions, episode titles, and chapter markers are sanitized to clean plain text.
*   **`RssParserCrashTest`**: A safety test that feeds broken, corrupted, or badly formatted XML to the parser to ensure the app doesn't crash, but instead handles the error gracefully by throwing a managed exception.
*   **`DateParserTest`**: Verifies that `DateParser` correctly parses RSS publication dates in multiple standard and non-standard formats (such as single-digit day representation, legacy timezone name replacements like EST, and ISO-8601 string fallbacks) to prevent dates from being zeroed out.
*   **`OpmlConditionalIntegrationTest`**: Performs live network integration tests across all podcast feeds in OPML files, validating HTTP 304 Not Modified caching support and asserting that all 38 feeds parse with clean titles, channel descriptions, preview descriptions, and chapter markers free of HTML tags or unescaped entities.

## 📁 Repository Tests
*Located in: `app/src/test/java/com/yuval/podcasts/data/repository/`*

Repositories are the "managers" that decide whether to get data from the database or the internet.

*   **`PodcastRepositoryTest`**: The main test for the repository. It checks that when you subscribe to a podcast, it fetches the data from the internet and saves it to the database. It also ensures long tasks run on background threads.
*   **`LocalMediaDataSourceTest`**: Verifies that when importing local files, the app correctly sanitizes filenames to prevent "Path Traversal" security vulnerabilities (keeping files safely inside the app's folder).
*   **`AddLocalFileIntegrationTest`**: Tests the feature that lets users import their own local MP3 files. It checks if the app can read the MP3's metadata (title, artist, duration) and fake a "podcast episode" in the database.
*   **`SettingsRepositoryTest`**: Checks that user preferences (like the default playback speed, default Smart Silence, and default Volume Boost) are loaded correctly from device storage.

## 💼 Domain Logic (Use Cases)
*Located in: `app/src/test/java/com/yuval/podcasts/domain/usecase/`*

Use Cases handle specific business rules.

*   **`EnqueueEpisodeUseCaseTest`**: Verifies the logic for adding a new episode to the queue. If it's a brand new episode, it gets added to the front. If it's older, it gets added to the back. It checks that downloading starts when a remote, un-downloaded item is queued, and verifies that background downloads are skipped for local episodes or already downloaded episodes (provided their physical file exists). It also verifies that if a downloaded episode's physical file is missing from storage, the usecase schedules a fresh background download.
*   **`RemoveEpisodeUseCaseTest`**: Checks that when an episode is removed from the queue, its downloaded audio file is deleted from the phone to free up storage space.
*   **`ImportLocalFileUseCaseTest`**: Verifies that importing a local audio file parses its metadata (using metadata extractor) and correctly triggers insertion into the local media database.
*   **`ReorderSubscriptionInQueueUseCaseTest`**: Confirms the business rules for moving all episodes belonging to a specific podcast subscription to the bottom of the playback queue.
*   **`RefreshAllPodcastsSyncUseCaseTest`**: Verifies that the synchronous refresh command used by AppFunctions delegates the refresh logic to the repository and returns the correct total count of newly added episodes.

## ⚙️ Background Workers
*Located in: `app/src/test/java/com/yuval/podcasts/work/`*

These tests verify background tasks managed by WorkManager.

*   **`SyncWorkerTest`**: Verifies smart periodic update execution, ensuring `SyncWorker` calls `refreshPodcasts` with `forceRefresh = false` to leverage ETAG and Last-Modified conditional headers.
*   **`HardRefreshWorkerTest`**: Verifies forced periodic update execution, ensuring `HardRefreshWorker` calls `refreshPodcasts` with `forceRefresh = true` to bypass ETAG and Last-Modified headers and perform full HTTP 200 re-fetches.
*   **`CleanupWorkerTest`**: Verifies that the periodic cleanup job identifies and deletes orphaned audio files from disk while preserving active queue and downloaded episodes.

## 🛠️ Utility & Mapping Tests
*Located in: `app/src/test/java/com/yuval/podcasts/ui/utils/` and `app/src/test/java/com/yuval/podcasts/media/`*

These tests verify small helper functions and data conversion logic.

*   **`FormatterTest`**: Verifies that our date and time formatting functions work correctly. It checks that timestamps are converted to "MMM dd, yyyy" and that durations are correctly shown as "1h 30m" or "02:15".
*   **`HtmlUtilsTest`**: Ensures that when converting podcast descriptions from HTML to displayable text, any embedded links are validated for safety (allowing only standard link types like `http`, `https`, and `mailto`, and blocking potentially dangerous links like `javascript:`). Also verifies that `stripHtml` properly strips raw HTML tags, nested markup, and formatting attributes while cleanly decoding standard HTML entities (such as `&amp;`, `&quot;`, `&nbsp;`, `&lt;`, and `&gt;`) into clean plain text for UI previews.
*   **`UiTextTest`**: Checks that `UiText` wrapper correctly resolves dynamic string resources and plain text formatting in diverse context settings.
*   **`MediaItemMapperTest`**: Ensures that we can correctly convert a podcast "Episode" from our database into a "MediaItem" that the Android audio player understands, preserving the title, artist, album, and artwork. It verifies that `podcastTitle` is mapped to both the `artist` and `albumTitle` metadata properties with fallback to `podcastFeedUrl` for `artist` if missing. It also verifies that episode artwork is preferred with automatic fallback to subscription/podcast artwork when episode-level artwork is unavailable, that residual HTML markup or entity codes in titles, artists, and album names are stripped, and that when a downloaded episode's physical file is missing from disk, the mapped media item correctly falls back to streaming the network audio URL.
*   **`StorageUtilsTest`**: Checks that file size converters, directory cleaners, and file validation tools calculate sizes accurately and safely delete nested directories.

## 🎧 Media Player Tests
*Located in: `app/src/test/java/com/yuval/podcasts/media/`*

These tests verify the audio player, background playback, and media buttons.

*   **`PlaybackServiceTest`**: The main audio service test. Ensures that when a track ends, it moves to the next track. Also checks that the service remembers where you paused if you close the app. Additionally verifies that when a transition occurs with a REPEAT reason, the finished episode is correctly marked as played and removed from the queue, verifies that when transitioning to an episode with a cached playback position, the player synchronously seeks to the saved position immediately without delay, and ensures playback position is only saved on user-initiated pauses and not during transient buffering events when `playWhenReady` is true.
*   **`PlaybackServiceQueueSyncTest`**: Makes sure that if you reorder or delete items in the queue screen, the actual audio player's internal playlist updates instantly to match.
*   **`PlaybackServiceMetadataSyncTest`**: Verifies that if an episode's metadata (like title or artwork) changes in the database, the player's current item is updated seamlessly using `replaceMediaItem` without interrupting playback.
*   **`PlaybackServiceCustomCommandTest`**: Verifies custom control commands executed via the MediaSession (such as custom rewind/fast-forward buttons from notification or lock screens).
*   **`PlaybackServiceResumeTest`**: Ensures that playback resumption via external controllers properly restores the player state and initiates the ExoPlayer instance correctly.
*   **`PlaybackServicePositionRestorationTest`**: Verifies that playback position restoration does not seek a newly selected track to the previously played track's position during rapid async track switches.
*   **`PlaybackServiceSilenceToggleTest`**: Tests skip-silence state observation and propagation to ExoPlayer.
*   **`MediaSessionCallbackTest`**: Tests the logic that "resolves" media IDs into playable items. This ensures that when external controllers (like Android Auto) request a track, the app correctly finds the URI and metadata from the database and resolves artwork with subscription artwork fallback.
*   **`MediaLibraryCallbackTest`**: Tests the media library service's browse callbacks (like `onGetLibraryRoot` and `onGetChildren`) used by Android Auto, ensuring they return the correct folder structure and queue episodes.
*   **`MediaButtonRemappingTest`**: Ensures that pressing the "Fast Forward" or "Rewind" buttons on Bluetooth headphones correctly skips forward/backward by 30/10 seconds instead of skipping to the next episode.
*   **`PlayerManagerTest`**: Tests the helper class that the UI uses to talk to the background service. It checks play, pause, and seeking functions, and confirms that `isPlaying` state remains consistently `true` during buffering when `playWhenReady` is active to prevent UI Play/Pause button flapping.
*   **`PlayerManagerInitializationTest`**: Checks that the PlayerManager doesn't try to send commands before it has successfully connected to the background audio service.
*   **`PlayerSpeedTest`**: Verifies that changing the playback speed (e.g., 1.5x) works and documents that ExoPlayer retains PlaybackParameters across stop() and prepare() cycles by design (as playback speed is managed as a persistent user preference in PlayerManager).
*   **`PlayerStopPlayTest`**: Ensures that stopping the player clears the current media and resets everything cleanly.
*   **`PlayerLastEpisodeTest`**: Verifies that when the last episode in the queue finishes, the player correctly stops and does not restart the playlist from the beginning. It also ensures the repeat mode is always set to OFF.
*   **`PlaybackResumptionTest`**: Verifies that when the app is cold-started or connected to a car (Bluetooth), the current episode correctly resumes from its last played position instead of starting from the beginning. It tests both the manual initialization and the standard Media3 `onPlaybackResumption` mechanism.

## 📱 UI ViewModels
*Located in: `app/src/test/java/com/yuval/podcasts/ui/viewmodel/`*

ViewModels prepare data for the screen. These tests check that the data is correct before it gets drawn.

*   **`PlayerViewModelTest`**: Tests the bridge between the UI and the audio player, ensuring UI buttons (Play/Pause, Skip) correctly trigger the corresponding player actions and verifying that single-episode playback resolves podcast artwork fallback.
*   **`ThemeViewModelTest`**: Verifies that Material You dynamic color theme palettes are seeded exclusively from the subscription/podcast cover art rather than individual episode art.
*   **`FeedsViewModelTest`**: Checks the "Subscriptions" screen logic. Makes sure it loads your podcasts, handles pulling down to refresh, and shows an error message if the internet is down.
*   **`QueueViewModelTest`**: Checks the "Up Next" queue logic. Makes sure that removing an item from the queue tells the player to skip if that item was currently playing.
*   **`QueueViewModelTimeTest`**: Verifies the math that calculates "Total Queue Time Remaining". If you have 3 hours of podcasts but you listen at 2x speed, it correctly tells you there is 1.5 hours remaining.
*   **`EpisodeDetailViewModelTest`**: Ensures the episode details screen loads the correct episode and knows whether that episode is already in your queue or not.
*   **`PodcastDetailViewModelTest`**: Verifies that the podcast detail screen loads episodes for the specified feed URL and handles adding episodes to the queue.
*   **`SettingsViewModelTest`**: Checks the settings screen logic, specifically ensuring that importing/exporting OPML files works, and logs can be downloaded correctly.

## 🤖 Android UI Tests (Instrumented)
*Located in: `app/src/androidTest/java/com/yuval/podcasts/ui/`*

These tests actually boot up the UI on an Android device to "click" buttons and check the screen.

*   **`EpisodeItemTest`**: Verifies that `EpisodeItem` (used across the Queue, New Episodes, and Podcast Details screens) and `PodcastItem` (used on the Subscriptions tab) cleanly render plain text descriptions with all raw HTML tags (`<p>`, `<strong>`, `<br>`, etc.) and entities (`&nbsp;`, `&amp;`) stripped.
*   **`NewEpisodesScreenTest`**: Tests user interactions on the New Episodes feed, including snackbar triggers and swipe state persistence across scrolls.
*   **`QueueScreenTest`**: Verifies dragging and dropping episodes to reorder the playback queue.
*   **`EpisodeDetailScreenShareTest`**: Verifies that tapping the Share button on the episode detail screen launches the system share sheet with the formatted episode title and web link.

*   **`EpisodeDetailScreenShareTest`**: Opens the Episode Detail screen and clicks the "Share" button. It verifies that for internet podcasts, it shares the website link. But for *local* files (like an imported voice memo), it just shares the text "Listening to [Name] via Podcasts App" since there is no link to share.
*   **`NewEpisodesScreenTest`**: Opens the "New Episodes" feed and verifies that pulling down to refresh triggers a background check for new episodes both when the list is populated and when it is empty. It also checks that refresh error states display the appropriate snackbar messages.
*   **`QueueScreenTest`**: Tests the queue screen functionality. Verifies playback control state updates, empty and non-empty pull-to-refresh gesture propagation, reordering, and top-bar listening time visibility constraints (ensuring it shows only when the queue has items). Includes drag-and-drop and state restoration tests that query the unmerged semantics tree to reliably interact with reorder handles under modern Compose semantics.

## 📱 AppFunctions Integration Tests
*Located in: `app/src/test/java/com/yuval/podcasts/appfunctions/` and `app/src/androidTest/java/com/yuval/podcasts/appfunctions/`*

These tests ensure that system-exposed capabilities (like Google Assistant or Gemini interactions) execute their actions correctly.

*   **`PodcastAppFunctionsTest`**: A local unit test confirming that system voice commands (like resume queue, pause, skip forward/backward, and moving subscription episodes to the bottom of the queue) map to the correct PlayerManager actions using the app's predefined seek duration constants, and that subscription feed resolving uses the optimized Elvis chain fallback hierarchy.
*   **`PodcastAppFunctionsIntegrationTest`**: An on-device instrumented test running on the emulator. It obtains the real Hilt-injected `PodcastAppFunctions` instance from the `PodcastApplication` context and verifies that all exposed functions (such as `resumeQueue`, `stopPlayback`, `skipForward`, `skipBackward`, `nextEpisode`, `refreshNewEpisodes`, `addDebugLog`, and `moveSubscriptionToBottom`) execute their operations successfully in the real application context without mock dependencies.

## ⚡ Macrobenchmark Tests
*Located in: `benchmark/src/main/java/com/yuval/podcasts/benchmark/`*

These tests measure the app's performance in real-world scenarios, including startup and scrolling responsiveness, on an Android device or emulator.

*   **`StartupBenchmark`**: Measures the cold startup time of the application (`MainActivity` launch and creation) to ensure startup times remain optimal and there are no regression blockages on the main thread.
*   **`ScrollBenchmark`**: Measures the scrolling performance (`FrameTimingMetric`) on the "New Episodes" list using hot startup mode to ensure smooth, stutter-free scrolling (avoiding frame drops and jank).

---
*Note: We also have OpmlManagerTest (checks OPML XML parsing/generation for backup files), OpmlImportWorkerTest (verifies background worker that downloads, extracts, and imports subscriptions from an OPML feed), DownloadWorkerRequestTest (verifies that DownloadWorker.createWorkRequest correctly maps inputs, constraints, and expedited flags to the WorkManager request), DownloadProgressTrackerTest (verifies real-time percentage tracking and range coercion for active download streams), PodcastApiConditionalTest (verifies HTTP 304 Not Modified conditional requests, ETag/Last-Modified headers, and 200 OK fallbacks), DatabaseSchemaMigrationTest (verifies Room database schema migrations and integrity validation against exported schemas using MigrationTestHelper), CleanupWorkerTest (verifies background cleanup worker that deletes orphaned podcast download files from storage), and MediaSessionIntegrationTest (instrumented integration test verifying PlaybackService custom commands and play/pause controls), and MediaBrowserIntegrationTest (instrumented integration test verifying PlaybackService catalog root and browse capability for Android Auto).*
