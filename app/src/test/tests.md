# Podcasts App Test Documentation

This document explains what each automated test in the project does in simple, human-readable terms. 
The tests are split into two main categories: **Local Unit Tests** (which run fast on your computer) and **Instrumented UI Tests** (which run on an Android emulator/device).

## 🗄️ Database Tests (DAOs & Migrations)
*Located in: `app/src/test/java/com/yuval/podcasts/data/db/dao/` and `app/src/androidTest/java/com/yuval/podcasts/data/db/`*

These tests ensure the local Room database saves, retrieves, and migrates information correctly.

*   **`EpisodeDaoTest`**: Verifies that we can insert, update, and delete podcast episodes. It also checks that we can correctly fetch a list of unplayed episodes.
*   **`PodcastDaoTest`**: Checks that adding and removing Podcast subscriptions works, and ensures that fetching "all podcasts" returns the correct list ordered alphabetically.
*   **`QueueDaoTest`**: Confirms that adding episodes to the play queue, reordering the queue, and removing items from the queue behaves as expected in the database.
*   **`AppDatabaseMigrationTest`**: Ensures that user data (like episodes and playback positions) is preserved during database updates. It specifically verifies the complex migration from version 5 to 6 where the primary key structure changed.

## 🌐 Network Tests
*Located in: `app/src/test/java/com/yuval/podcasts/data/network/`*

These tests ensure the app can talk to the internet and understand the podcast data it gets back.

*   **`PodcastApiIntegrationTest`**: Makes sure that if the app is trying to download an RSS feed and the user cancels the action (like closing the app), the network request stops immediately to save data and battery.
*   **`PodcastRemoteDataSourceTest`**: Verifies that the remote data source correctly orchestrates the network fetch and RSS parsing into clean data models.
*   **`RssParserTest`**: Feeds fake XML files (RSS feeds) to the parser to ensure it correctly extracts the podcast title, episode names, audio links, publication dates, and artwork (handling both standard RSS and iTunes image tags).
*   **`RssParserCrashTest`**: A safety test that feeds broken, corrupted, or badly formatted XML to the parser to ensure the app doesn't crash, but instead handles the error gracefully.

## 📁 Repository Tests
*Located in: `app/src/test/java/com/yuval/podcasts/data/repository/`*

Repositories are the "managers" that decide whether to get data from the database or the internet.

*   **`PodcastRepositoryTest`**: The main test for the repository. It checks that when you subscribe to a podcast, it fetches the data from the internet and saves it to the database. It also ensures long tasks run on background threads.
*   **`PodcastRepositoryRefreshTest`**: Checks the "Refresh All" feature. It makes sure that if you have 10 subscriptions, it refreshes them all quickly in parallel without freezing the app.
*   **`AddLocalFileIntegrationTest`**: Tests the feature that lets users import their own local MP3 files. It checks if the app can read the MP3's metadata (title, artist, duration) and fake a "podcast episode" in the database.
*   **`PodcastRepositoryAddLocalFileTest`**: A smaller test verifying that once the local MP3 is read, the repository properly saves it into the special "Local Files" subscription.
*   **`SettingsRepositoryTest`**: Checks that user preferences (like the default playback speed) are saved and loaded correctly from device storage.

## 💼 Domain Logic (Use Cases)
*Located in: `app/src/test/java/com/yuval/podcasts/domain/usecase/`*

Use Cases handle specific business rules.

*   **`EnqueueEpisodeUseCaseTest`**: Verifies the logic for adding a new episode to the queue. If it's a brand new episode, it gets added to the front. If it's older, it gets added to the back. It also checks that downloading starts when an item is queued.
*   **`RemoveEpisodeUseCaseTest`**: Checks that when an episode is removed from the queue, its downloaded audio file is deleted from the phone to free up storage space.
*   **`ExportOpmlUseCaseTest`**: Confirms that we can export our podcast subscription list to an OPML backup file.
*   **`ImportOpmlUseCaseTest`**: Confirms that given an OPML backup file, we can correctly extract the feed URLs and trigger new subscriptions for each.
*   **`RefreshAllPodcastsUseCaseTest`**: Verifies that the global refresh command correctly schedules a background worker to sync all subscriptions.

## 🛠️ Utility & Mapping Tests
*Located in: `app/src/test/java/com/yuval/podcasts/ui/utils/` and `app/src/test/java/com/yuval/podcasts/media/`*

These tests verify small helper functions and data conversion logic.

*   **`FormatterTest`**: Verifies that our date and time formatting functions work correctly. It checks that timestamps are converted to "MMM dd, yyyy" and that durations are correctly shown as "1h 30m" or "02:15".
*   **`MediaItemMapperTest`**: Ensures that we can correctly convert a podcast "Episode" from our database into a "MediaItem" that the Android audio player understands, preserving the title, artist, and artwork.

## 🎧 Media Player Tests
*Located in: `app/src/test/java/com/yuval/podcasts/media/`*

These tests verify the audio player, background playback, and media buttons.

*   **`PlaybackServiceTest`**: The main audio service test. Ensures that when a track ends, it moves to the next track. Also checks that the service remembers where you paused if you close the app.
*   **`PlaybackServiceQueueSyncTest`**: Makes sure that if you reorder or delete items in the queue screen, the actual audio player's internal playlist updates instantly to match.
*   **`PlaybackServiceMetadataSyncTest`**: Verifies that if an episode's metadata (like title or artwork) changes in the database, the player's current item is updated seamlessly using `replaceMediaItem` without interrupting playback.
*   **`MediaSessionCallbackTest`**: Tests the logic that "resolves" media IDs into playable items. This ensures that when external controllers (like Android Auto) request a track, the app correctly finds the URI and metadata from the database.
*   **`MediaButtonRemappingTest`**: Ensures that pressing the "Fast Forward" or "Rewind" buttons on Bluetooth headphones correctly skips forward/backward by 30/10 seconds instead of skipping to the next episode.
*   **`PlayerManagerTest`**: Tests the helper class that the UI uses to talk to the background service. It checks play, pause, and seeking functions.
*   **`PlayerManagerInitializationTest`**: Checks that the PlayerManager doesn't try to send commands before it has successfully connected to the background audio service.
*   **`PlayerSpeedTest` / `PlayerSpeedControllerTest`**: Verifies that changing the playback speed (e.g., 1.5x) works and that the speed is saved so the next episode plays at the same speed.
*   **`PlayerStopPlayTest`**: Ensures that stopping the player clears the current media and resets everything cleanly.
*   **`PlayerLastEpisodeTest`**: Verifies that when the last episode in the queue finishes, the player correctly stops and does not restart the playlist from the beginning. It also ensures the repeat mode is always set to OFF.
*   **`PlayerManagerBugTest`**: A specific regression test ensuring that when the app restarts, the UI correctly shows the paused state of the last played episode.

## 📱 UI ViewModels
*Located in: `app/src/test/java/com/yuval/podcasts/ui/viewmodel/`*

ViewModels prepare data for the screen. These tests check that the data is correct before it gets drawn.

*   **`PlayerViewModelTest`**: Tests the bridge between the UI and the audio player, ensuring UI buttons (Play/Pause, Skip) correctly trigger the corresponding player actions.
*   **`FeedsViewModelTest`**: Checks the "Subscriptions" screen logic. Makes sure it loads your podcasts, handles pulling down to refresh, and shows an error message if the internet is down.
*   **`QueueViewModelTest`**: Checks the "Up Next" queue logic. Makes sure that removing an item from the queue tells the player to skip if that item was currently playing.
*   **`QueueViewModelTimeTest`**: Verifies the math that calculates "Total Queue Time Remaining". If you have 3 hours of podcasts but you listen at 2x speed, it correctly tells you there is 1.5 hours remaining.
*   **`EpisodeDetailViewModelTest`**: Ensures the episode details screen loads the correct episode and knows whether that episode is already in your queue or not.
*   **`PodcastDetailViewModelTest`**: Ensures clicking a podcast loads only the episodes belonging to that specific podcast.
*   **`SettingsViewModelTest`**: Checks the settings screen logic, specifically ensuring that importing/exporting OPML files (podcast backups) works and shows error messages if a file is broken.

## 🤖 Android UI Tests (Instrumented)
*Located in: `app/src/androidTest/java/com/yuval/podcasts/ui/screens/`*

These tests actually boot up the UI on an Android device to "click" buttons and check the screen.

*   **`EpisodeDetailScreenShareTest`**: Opens the Episode Detail screen and clicks the "Share" button. It verifies that for internet podcasts, it shares the website link. But for *local* files (like an imported voice memo), it just shares the text "Listening to [Name] via Podcasts App" since there is no link to share.
*   **`NewEpisodesScreenTest`**: Opens the "New Episodes" feed, simulates pulling down from the top of the screen to refresh, and checks that the loading spinner appears and disappears correctly.
*   **`QueueScreenTest`**: Specifically tests the "Up Next" queue. It verifies that the Play/Pause buttons update their icons correctly when an episode starts playing. It also contains stress tests for the drag-and-drop gesture to ensure it doesn't crash or "snap" during rapid reordering.

---
*Note: We also have `OpmlManagerTest` (checks OPML XML parsing/generation for backup files) and `PlayerModuleTest` (checks that our Dependency Injection provides the right ExoPlayer instances).*
