# Plan: Local Subscriptions Feature

This plan outlines the implementation of a "Local Subscription" feature, allowing users to import local MP3 files into the app.

## 1. Overview
A special "subscription" will be created in the database to house manually uploaded local audio files. This allows users to use the app's player and queue management for their own recordings or manually downloaded files.

## 2. Architecture & Data Model
- **Local Feed ID:** A hardcoded `feedUrl = "local_subscription"` will identify the local podcast.
- **Podcast Entity:** Upon first upload, a `Podcast` entry with this ID will be created titled "Local Files".
- **Episode Entity:** Local files will be stored as `Episode` entities with:
    - `podcastFeedUrl = "local_subscription"`
    - `audioUrl = [Internal Path]`
    - `downloadStatus = 2` (Completed)
    - `localFilePath = [Internal Path]`

## 3. Metadata Extraction Strategy
To gather details about the uploaded file, we will use a tiered approach:
1. **Primary (Embedded Metadata):** Use Android's native `MediaMetadataRetriever` to extract ID3 tags (Title, Artist, Duration).
2. **AI-Style Fallback (Filename Cleaning):** If metadata is missing, the app will scan the filename and use regex logic to "beautify" it (e.g., removing underscores, dates, and file extensions) to create a human-readable title.
3. **Storage:** Files will be copied to the app's internal `filesDir/local_uploads/` to ensure they persist even if the original source file is moved or deleted.

## 4. UI & Interaction
- **Settings Screen:** Add a button "Import Local Audio File".
- **File Picker:** Use `ActivityResultContracts.GetContent()` filtering for `audio/mpeg`.
- **Sharing Logic:**
    - If `episodeWebLink` exists: Share the link.
    - Else if `audioUrl` is a remote URL (starts with http): Share the URL.
    - **Fallback (Local Files):** Share only the text metadata (e.g., "Listening to: [Title] via Podcasts App"). No physical file or local path will be shared to ensure privacy and reliability.

## 5. Implementation Steps
1. **Domain Layer:** Create `AddLocalFileUseCase.kt` to handle file copying, metadata extraction, and DB insertion.
2. **Repository Layer:** Add `addLocalFile(uri: Uri)` to `PodcastRepository` interface and implementation.
3. **UI Layer:** 
    - Update `SettingsViewModel` to orchestrate the import process.
    - Update `SettingsScreen.kt` to include the import UI.
    - Update `EpisodeDetailScreen.kt` to implement the tiered sharing fallback logic.

## 6. Testing Strategy
- **Unit Test:** `AddLocalFileUseCaseTest` to verify metadata extraction and DB mapping.
- **UI Test:** Verify the "Share" button emits correct text content for local vs. remote episodes.
- **Integration Test:** Verify the `local_subscription` feed is correctly populated in Room.
