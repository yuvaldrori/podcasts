# Podcasts App Test Documentation

This document explains what each automated test in the project does in simple, human-readable terms.
The tests are split into **Local Unit Tests** (fast JVM/Robolectric tests), **Instrumented Tests** (running on an Android emulator/device), and **Macrobenchmarks**.

## 🗄️ Database Tests (DAOs & Migrations)
*Located in: `app/src/test/java/com/yuval/podcasts/data/db/` and `app/src/androidTest/java/com/yuval/podcasts/data/db/`*

These tests ensure the local Room database saves, retrieves, and migrates information correctly.

*   **`EpisodeDaoTest` (unit & instrumented)**: Verifies inserting, updating, and deleting podcast episodes, fetching unplayed episodes and `getUnplayedEpisodeCount` with `limit` and `pubDate DESC` ordering, excluding queued episodes from the unplayed feed, guarded `updateDownloadStatusAfterSuccess` transitions, colliding GUIDs across feeds, and `syncNetworkEpisodes` preserving local user state.
*   **`PodcastDaoTest`**: Verifies adding and removing podcast subscriptions and confirms `getAllPodcasts()` orders subscriptions by their most recent episode's publication date (`MAX(episodes.pubDate) DESC`) rather than insertion order.
*   **`QueueDaoTest`**: Verifies `updateQueue` replacement, reactive `getQueueCount()`, ordered retrieval (`getQueueEpisodesSync`, `getQueueEpisodesWithPodcast`, `getQueueEpisodesWithPodcastSync`), and item removal.
*   **`ChapterDaoTest`**: Verifies bulk chapter deletion and replacement (`updateChaptersBulk`) while preserving chapters belonging to other episodes.
*   **`DatabaseSchemaIntegrityTest`**: Structurally validates that `AppDatabase` version matches the highest exported Room schema JSON, that a migration path (`@AutoMigration` or manual `Migration`) exists for the latest version bump, and that the `podcasts` table schema contains `etag` and `lastModified`.
*   **`DatabaseSchemaMigrationTest`**: Seeds an authentic v8 SQLite schema (including `localId` primary key, legacy `chapters` foreign key, and sample rows) and verifies `MIGRATION_8_9` upgrades to v9 composite primary keys, adds `etag`/`lastModified` with `NULL` defaults, drops the `chapters` foreign key, recreates indexes, and preserves existing rows.
*   **`AppDatabaseMigrationTest` (instrumented)**: Uses Room's `MigrationTestHelper` against exported JSON schemas to validate `MIGRATION_5_6` and `MIGRATION_8_9` preserve non-default episode, podcast, and chapter data.

## 🌐 Network Tests
*Located in: `app/src/test/java/com/yuval/podcasts/data/network/`*

These tests verify RSS fetching, conditional caching, cancellation, and XML/date parsing deterministically offline.

*   **`PodcastApiAwaitTest`**: Verifies that cancelling a coroutine racing with an OkHttp response closes the `Response` body to prevent connection leaks.
*   **`PodcastApiConditionalTest`**: Verifies HTTP 304 Not Modified conditional requests, `If-None-Match` / `If-Modified-Since` header forwarding and omission, 200 OK body streaming + ETag/Last-Modified extraction, and 404 `IOException` propagation.
*   **`PodcastApiIntegrationTest`**: Uses an in-process local `HttpServer` holding an in-flight response open to deterministically verify cancelling `PodcastApi.withRssStreamConditional` aborts the request with `CancellationException`.
*   **`OpmlConditionalIntegrationTest`**: Exercises `OpmlManager` → `PodcastApi.withRssStreamConditional` → `RssParser` end-to-end against a deterministic local OkHttp interceptor, verifying 304 Not Modified caching, sanitized titles/channel descriptions/chapters, and preserved HTML links in episode descriptions.
*   **`RssParserTest`**: Verifies extracting podcast/episode metadata, CDATA & entities, schemeless & HTTP→HTTPS URL upgrades, `<itunes:image>` precedence regardless of tag order, invalid duration resilience, and HTML/entity sanitization of titles, podcast descriptions, and chapters.
*   **`RssParserCrashTest`**: Feeds malformed XML to `RssParser` and verifies it throws a wrapped exception whose cause is `XmlPullParserException`.
*   **`DateParserTest`**: Verifies RSS publication date parsing across RFC-1123, single-digit days, legacy timezone abbreviations (`EST`, `UT`), word-boundary preservation for `"UTC"`, ISO-8601 fallback, and invalid date fallback to `0L`.
*   **`AppUpdateManagerTest`**: Verifies GitHub Releases API JSON parsing (`Version Code`, hex SHA vs. branch-name commit hash fallback, null `body` resilience, build time, commit message bounded before subsequent `###` sections, trusted GitHub release host validation rejecting `raw.githubusercontent.com` and third-party hosts), newer vs. equal/older `versionCode` comparison (including post-upgrade cache cleanup), HTTP/network/missing-asset error handling, APK streaming with progress updates, dotfile/path-traversal filename sanitization, truncated-stream rejection, mid-stream failure `.tmp` cleanup (preserving prior valid APKs), and mid-stream coroutine cancellation aborting the OkHttp call and deleting `.tmp`.

## 📁 Data, OPML & Repository Tests
*Located in: `app/src/test/java/com/yuval/podcasts/data/`*

*   **`ConstantsTest`**: Verifies `AUDIO_MIME_TYPES` and `OPML_MIME_TYPES` contain all required literal MIME formats and `*/*` fallback.
*   **`OpmlManagerTest`**: Verifies OPML XML parsing, invalid scheme filtering, malformed XML partial recovery, XXE entity-expansion prevention (`&evil;` is not expanded), and OPML export excluding `local_subscription`.
*   **`PodcastRepositoryTest`**: Verifies `fetchAndStorePodcast` (network execution on `ioDispatcher`, ETag/Last-Modified header copying & clearing, 304 handling, `forceRefresh`, chapter clearing, local-feed skipping), `unsubscribePodcast` (DB transaction + local file deletion), `markAsPlayed` atomicity, `removeFromQueue`, `reorderQueue`, `requeueEpisode`, `requeueMissingDownloads`, and `refreshPodcasts`.
*   **`QueueInsertionOrderTest`**: Verifies the pure queue-insertion ordering rules in `findQueueInsertionIndex`: same-subscription oldest-first grouping, chronological placement at group boundaries without splitting groups, pinning below any currently playing episode, and preserving existing queue order.
*   **`DownloadProgressTrackerTest`**: Verifies real-time percentage tracking, clamping to `0..100`, and clearing download progress state.
*   **`LocalMediaDataSourceTest`**: Verifies path-traversal hardening via `content://` `DISPLAY_NAME` inputs (`../../../etc/passwd` is sanitized inside `local_podcasts`, and `..` is rejected with `SecurityException`).
*   **`AddLocalFileIntegrationTest`**: Wires real in-memory Room DB, `DefaultPodcastRepository`, `LocalMediaDataSource`, and `ImportLocalFileUseCase` to verify copying audio files, extracting or falling back on metadata, and inserting local podcast/episode rows into Room.
*   **`SettingsRepositoryTest`**: Verifies DataStore defaults and persistence for playback speed, Smart Silence, and Volume Boost.

## 💼 Domain Logic (Use Cases)
*Located in: `app/src/test/java/com/yuval/podcasts/domain/usecase/`*

*   **`EnqueueEpisodeUseCaseTest`**: Verifies forwarding the active playing episode ID only while playing, scheduling unique `KEEP` download work for remote un-downloaded/missing-file episodes, and skipping downloads for local or already-downloaded files.
*   **`RemoveEpisodeUseCaseTest`**: Verifies removing an episode from the queue and delegating `markAsPlayed` based on the `markAsPlayed` flag (default `true`).
*   **`ImportLocalFileUseCaseTest`**: Verifies ensuring the local pseudo-podcast exists, delegating extraction to `LocalMediaDataSource`, and inserting the imported episode.
*   **`ReorderSubscriptionInQueueUseCaseTest`**: Verifies moving a subscription's episodes to the bottom of the queue sorted chronologically (`pubDate` ascending) even when initially out of chronological order.
*   **`RefreshAllPodcastsSyncUseCaseTest`**: Verifies delegating synchronous refresh of all subscribed podcasts (`forceRefresh = false`) and returning the new episode count.

## ⚙️ Background Workers
*Located in: `app/src/test/java/com/yuval/podcasts/work/` and `app/src/androidTest/java/com/yuval/podcasts/work/`*

*   **`SyncWorkerTest`**: Verifies periodic sync calls `refreshPodcasts(..., forceRefresh = false)` and rethrows `CancellationException`.
*   **`HardRefreshWorkerTest`**: Verifies forced periodic refresh calls `refreshPodcasts(..., forceRefresh = true)` and requeues missing downloads.
*   **`CleanupWorkerTest`**: Verifies deleting orphaned `.mp3` and `.part` files while preserving queued episodes, non-queued downloaded episodes, and active downloading `.part` files.
*   **`DownloadWorkerRequestTest`**: Verifies `DownloadWorker.createWorkRequest` maps input data, network constraint, and expedited flag.
*   **`DownloadWorkerStreamTest`**: Verifies mid-stream stop and coroutine cancellation revert DB status from `DOWNLOADING` to `NOT_DOWNLOADED`, clear `DownloadProgressTracker`, and delete `.part` files, and verifies streaming progress updates are both emitted (`atLeast = 1`) and time-throttled (`atMost = 25`).
*   **`DownloadWorkerIntegrationTest` (instrumented)**: Verifies `DownloadWorker` returns `Result.retry()` on network `IOException` and transitions `EpisodeDao` status from `DOWNLOADING` back to `NOT_DOWNLOADED`.
*   **`OpmlImportWorkerTest`**: Verifies `OpmlImportWorker` reads an OPML `Uri` stream, delegates URLs to `repository.refreshPodcasts`, and reports initial and callback progress `(0/2, 1/2, 2/2)` to WorkManager `ProgressUpdater`.

## 🛠️ Utility, Cast & Mapping Tests
*Located in: `app/src/test/java/com/yuval/podcasts/utils/`, `ui/utils/`, `ui/screens/`, and `media/`*

*   **`StorageUtilsTest`**: Verifies `getDownloadsDir` creates the `podcasts` directory inside `filesDir`, `getFileName` generates deterministic per-episode filenames, and `getFileForEpisode` resolves inside `podcasts/`.
*   **`LogManagerTest`**: Verifies `LogManager.exportLogsToStream` flushes pending buffered entries immediately and produces valid JSONL output.
*   **`FormatterTest`**: Verifies locale-independent date (`MMM dd, yyyy`), short duration, playback time, and remaining-time formatting.
*   **`HtmlUtilsTest`**: Verifies safe URL filtering (`http`/`https`/`mailto` only) in `Spanned.toAnnotatedString()` and HTML tag/entity stripping in `HtmlUtils.stripHtml()`.
*   **`UiTextTest`**: Verifies `UiText.DynamicString` and `UiText.StringResource` (including exact `resId`, varargs forwarding to `Context.getString`, and structural `equals`/`hashCode`).
*   **`DragDropStateTest`**: Verifies single-step and multi-item drag jumps in `DragDropState` keep the dragged item pinned to the finger without offset drift.
*   **`MediaItemMapperTest`**: Verifies mapping `Episode` → `MediaItem` (local file URI vs missing-file remote fallback, podcast/episode artwork precedence, podcast title → artist/albumTitle, Cast remote URL extra, `MEDIA_TYPE_PODCAST_EPISODE`, and `inferAudioMimeType` across `.m4a`, `.aac`, `.ogg`, `.wav`, `.flac`, and default fallback).
*   **`PodcastMediaItemConverterTest`**: Verifies Google Cast conversion swaps local file URIs for remote HTTP URLs and round-trips back to local file URIs.
*   **`CastOptionsProviderTest`**: Verifies `CastOptionsProvider` disables Cast SDK's duplicate `MediaSession`/notification, enables the System Output Switcher, stops receiver on session end, and configures default media receiver ID.

## 🎧 Media Player Tests
*Located in: `app/src/test/java/com/yuval/podcasts/media/` and `app/src/androidTest/java/com/yuval/podcasts/media/`*

*   **`PlaybackServiceTest`**: Verifies `PlaybackServicePlayerListener` transitions (`AUTO`/`REPEAT` mark-played & remove-from-queue, synchronous cached-position seeking, saving position only on user pause, not buffering) and lazy `CastPlayer` listener transfer and safe player release.
*   **`PlaybackServiceQueueSyncTest`**: Verifies surgical queue sync preserves playback index, removing a playing middle episode transitions cleanly to the next episode with playback active, Cast sessions skip redundant `replaceMediaItem` calls, and omitted/unmappable `MediaItem`s remain ID-aligned without index shifts.
*   **`PlaybackServiceMetadataSyncTest`**: Verifies non-playing queue item metadata updates via `replaceMediaItem` without replacing the currently playing item or calling `setMediaItems`.
*   **`PlaybackServiceCustomCommandTest`**: Verifies `REWIND_10` and `SKIP_30` custom commands seek `currentPlayer` and return `SessionResult.RESULT_SUCCESS`.
*   **`PlaybackServiceResumeTest`**: Verifies `PlaybackServicePlayerListener.onMediaItemTransition` seeks to cached `lastPlayedPosition` when at start and skips seeking when already past threshold.
*   **`PlaybackServicePositionRestorationTest`**: Verifies `PlaybackServicePlayerListener` does not seek when `currentPlayer.currentMediaItem` has already moved to a different track.
*   **`PlaybackServiceSilenceToggleTest`**: Verifies `PlaybackService.observeSkipSilence` (wired in `onCreate`) propagates `SettingsRepository.skipSilenceFlow` changes to `exoPlayer.skipSilenceEnabled`.
*   **`MediaSessionCallbackTest`**: Invokes `PlaybackService`'s real `onAddMediaItems` callback to verify stub item DB resolution with podcast metadata and pass-through of already-configured items without DAO lookup.
*   **`MediaLibraryCallbackTest`**: Invokes `PlaybackService`'s real `onGetLibraryRoot` and `onGetChildren` callbacks for Android Auto browsing, verifying root/queue folders and full episode + podcast metadata mapping.
*   **`MediaButtonRemappingTest`**: Invokes `PlaybackService`'s real `onMediaButtonEvent` callback to verify `KEYCODE_MEDIA_NEXT` and `KEYCODE_MEDIA_PREVIOUS` seek forward and backward on `currentPlayer`.
*   **`PlaybackResumptionTest`**: Invokes `PlaybackService`'s real `onPlaybackResumption` callback to verify restoring the last-played episode index and position, fallback to index 0 when missing, and empty-queue handling.
*   **`PlayerManagerTest`**: Verifies `PlayerManager` speed sync, play/pause toggle, bounded seeks, `playQueue` with podcast metadata, `STATE_ENDED` clearing, buffering `isPlaying` stability, `onPlayWhenReadyChanged(false)` transition from playing, monotonic `onPositionDiscontinuity` emission, `stopAndClear` resetting active state, and `release()` preserving singleton scope.
*   **`PlayerManagerInitializationTest`**: Subscribes to `currentPosition` before controller connection and verifies `setupControllerListener()` pushes the paused controller's initial position (`45000L`) to already-subscribed collectors.
*   **`MediaSessionIntegrationTest` (instrumented)**: Connects a real `MediaController` to `PlaybackService`, verifies custom commands return `RESULT_SUCCESS`, and deterministically toggles `play()`/`pause()` via `Player.Listener` callbacks.
*   **`MediaBrowserIntegrationTest` (instrumented)**: Connects a real `MediaBrowser` to `PlaybackService` and verifies `getLibraryRoot`, `getChildren("root")`, and `getChildren("queue")` return `LibraryResult.RESULT_SUCCESS` with expected browse items.

## 📱 UI ViewModels
*Located in: `app/src/test/java/com/yuval/podcasts/ui/viewmodel/`*

*   **`PlayerViewModelTest`**: Verifies play/pause/seek delegation, deferred IO-dispatcher missing-file re-enqueue, chapter seeking (in-place vs switching episode), and podcast artwork fallback.
*   **`ThemeViewModelTest`**: Verifies Material You dynamic palette generation uses podcast artwork over episode artwork and offloads palette work to `ioDispatcher`.
*   **`FeedsViewModelTest`**: Verifies refresh state and progress from WorkManager, download progress exposure, and `addToQueue` delegation to `EnqueueEpisodeUseCase`.
*   **`NavBadgeViewModelTest`**: Verifies reactive queue and unplayed-episode badge counts backed by lightweight SQL count flows.
*   **`QueueViewModelTest`**: Verifies optimistic drag-and-drop commit and failure rollback (confirming subsequent DB emissions flow through after drag completes), removing playing vs non-playing episodes, and speed-adjusted time remaining.
*   **`QueueViewModelTimeTest`**: Verifies live playback position ticks update `queueTimeRemaining` without re-emitting `uiState` and override DB duration/position for the active episode.
*   **`EpisodeDetailViewModelTest`**: Verifies loading episode details, pre-parsing HTML descriptions into clickable `AnnotatedString`s (and reusing them without re-parsing across queue updates), queue membership state, and `addToQueue` delegation.
*   **`PodcastDetailViewModelTest`**: Verifies loading episodes for a podcast feed URL and adding episodes to the queue.
*   **`SettingsViewModelTest`**: Verifies adding podcasts, importing/exporting OPML (excluding local pseudo-subscriptions), importing local audio, exporting logs, clearing messages, exact error string resource IDs, and the in-app update state machine (concurrent check guard, up-to-date/available/error transitions, mid-flight download progress, evicted-APK re-download on install, install error routing, download failure logging, and download cancellation).

## 🤖 Android UI Tests (Instrumented)
*Located in: `app/src/androidTest/java/com/yuval/podcasts/ui/`*

*   **`EpisodeItemTest`**: Verifies `EpisodeItem` renders plain-text previews with HTML tags and entities completely stripped (`substring = false`), and `PodcastItem` renders podcast title and description.
*   **`NewEpisodesScreenTest`**: Verifies error snackbar display, scrolling and Dismiss / Add-to-Queue button callbacks, and pull-to-refresh on both populated and empty lists.
*   **`AddToQueueIntegrationTest`**: End-to-end instrumented test wiring real in-memory Room, `DefaultPodcastRepository`, `NewEpisodesScreen`, and `QueueScreen` to verify persisted and visual top-to-bottom queue insertion order.
*   **`QueueScreenTest`**: Verifies play/pause button state, drag-and-drop reordering and commit callbacks, state restoration preserving reordered queue order, pull-to-refresh on empty and non-empty queues, and top-bar listening-time visibility.
*   **`EpisodeDetailScreenShareTest`**: Verifies tapping Share launches `ACTION_CHOOSER` with `text/plain` `ACTION_SEND` sharing title + web link for remote episodes and text-only metadata for local episodes (even if `episodeWebLink` is non-null).

## 📱 AppFunctions Tests
*Located in: `app/src/test/java/com/yuval/podcasts/appfunctions/` and `app/src/androidTest/java/com/yuval/podcasts/appfunctions/`*

*   **`PodcastAppFunctionsTest`**: Unit tests verifying voice commands delegate to `PlayerManager` with expected seek constants, `addDebugLog`, `refreshNewEpisodes`, and subscription resolution fallback hierarchy.
*   **`PodcastAppFunctionsIntegrationTest` (instrumented)**: Verifies real Hilt resolution via `EntryPointAccessors`, real `LogManager` log persistence for `addDebugLog`, and real in-memory Room DB persistence for `refreshNewEpisodes` and `moveSubscriptionToBottom` (both reordering matched subscriptions and returning not-found messages).

## ⚡ Macrobenchmark Tests
*Located in: `benchmark/src/main/java/com/yuval/podcasts/benchmark/`*

*   **`StartupBenchmark`**: Grants `POST_NOTIFICATIONS` in `setupBlock` and measures cold startup (`StartupTimingMetric`) and `TraceSectionMetric("MainActivity_onCreate")`.
*   **`ScrollBenchmark`**: Uses `CompilationMode.Partial()` and Compose `testTagsAsResourceId` (`tab_new_episodes`, `episode_list`), resets scroll position to the top before each `StartupMode.HOT` iteration, and measures `FrameTimingMetric` while flinging the New Episodes list.
