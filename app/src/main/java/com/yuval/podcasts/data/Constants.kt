package com.yuval.podcasts.data

import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

object Constants {
    const val LOCAL_PODCAST_FEED_URL = "local_subscription"
    const val DATABASE_NAME = "podcasts_db"
    
    // Media & Playback
    const val DEFAULT_PLAYBACK_SPEED = 2.0f
    val SEEK_FORWARD_MS = 30.seconds.inWholeMilliseconds
    val SEEK_BACKWARD_MS = 15.seconds.inWholeMilliseconds
    val SAVE_POSITION_INTERVAL_MS = 15.seconds.inWholeMilliseconds
    val NETWORK_TIMEOUT_MS = 30.seconds.inWholeMilliseconds.toInt()
    val NETWORK_CALL_TIMEOUT_MS = 60.seconds.inWholeMilliseconds.toInt()
    const val SEEK_POSITION_RESTORATION_THRESHOLD_MS = 2000L
    
    const val COMMAND_REWIND_10 = "REWIND_10"
    const val COMMAND_SKIP_30 = "SKIP_30"
    val REWIND_10_MS = 10.seconds.inWholeMilliseconds
    val SKIP_30_MS = 30.seconds.inWholeMilliseconds
    const val VOLUME_BOOST_GAIN_MB = 600

    // UI & ViewModel
    val FLOW_STOP_TIMEOUT_MS = 5.seconds.inWholeMilliseconds
    const val UNPLAYED_EPISODES_LIMIT = 150
    const val DYNAMIC_THEME_DEBOUNCE_MS = 200L
    
    // UI Sizes
    const val COVER_SIZE_LIST_DP = 48
    const val COVER_SIZE_DETAIL_DP = 120
    const val MINI_PLAYER_HEIGHT_DP = 56
    const val PLAYER_ART_SIZE_DP = 40
    const val PLAYER_BUTTON_MIN_SIZE_DP = 36
    
    // Animation Durations
    const val NAVIGATION_ANIMATION_MS = 0
    
    // Mime Types
    const val MIME_TYPE_OPML = "text/x-opml"
    const val MIME_TYPE_JSONL = "application/jsonl"
    const val MIME_TYPE_AUDIO_ALL = "audio/*"
    const val MIME_TYPE_TEXT_PLAIN = "text/plain"
    const val MIME_TYPE_ALL = "*/*"

    // WorkManager Names
    const val WORK_NAME_CLEANUP = "cleanup_orphaned_files"
    const val WORK_NAME_SYNC_ALL = "sync_all_podcasts"
    const val WORK_NAME_OPML_IMPORT = "opml_import"
    const val WORK_TAG_DOWNLOAD_PREFIX = "download_"
    
    // WorkManager Keys
    const val WORK_KEY_PROGRESS = "PROGRESS"
    const val WORK_KEY_TOTAL = "TOTAL"

    // WorkManager Throttling
    const val SYNC_PROGRESS_NOTIFICATION_THROTTLE_MS = 1000L
    const val OPML_IMPORT_PROGRESS_NOTIFICATION_THROTTLE_MS = 500L
    const val DOWNLOAD_PROGRESS_WORKER_THROTTLE_MS = 100L
    const val DOWNLOAD_PROGRESS_NOTIFICATION_INCREMENT_PERCENT = 5
    
    // Storage
    const val DOWNLOAD_DIR_NAME = "podcasts"
    const val SYNC_RETRY_COUNT = 3
    const val PERIODIC_CLEANUP_INTERVAL_HOURS = 24L
    const val BYTES_PER_KB = 1024L
    const val BYTES_PER_MB = 1024L * 1024L
    const val DOWNLOAD_BUFFER_SIZE_BYTES = 64 * 1024

    // Notification IDs
    const val NOTIFICATION_ID_DOWNLOAD = 1
    const val NOTIFICATION_ID_IMPORT = 2
    const val NOTIFICATION_ID_SYNC = 3

    // Performance & Concurrency
    const val MAX_PARALLEL_REFRESHES = 3

    // Logging
    const val LOG_BUFFER_LIMIT = 50
    val LOG_FLUSH_INTERVAL_MS = 2.seconds.inWholeMilliseconds
    val MAX_LOG_FILE_SIZE = 10 * BYTES_PER_MB

    // Network Headers
    const val HTTP_USER_AGENT = "PodcastsAndroidApp/1.0"

    
    /**
     * MIN_LOG_LEVEL controls which logs are persisted:
     * 2: VERBOSE (All logs)
     * 3: DEBUG
     * 4: INFO (Default)
     * 5: WARN
     * 6: ERROR
     */
    const val MIN_LOG_LEVEL = 4

    object Rss {
        const val RSS = "rss"
        const val CHANNEL = "channel"
        const val ITEM = "item"
        const val TITLE = "title"
        const val DESCRIPTION = "description"
        const val LINK = "link"
        const val PUB_DATE = "pubDate"
        const val IMAGE = "image"
        const val IMAGE_HREF = "href"
        const val IMAGE_URL = "url"
        const val URL = "url"
        const val GUID = "guid"
        const val ENCLOSURE = "enclosure"
        const val ENCLOSURE_URL = "url"
        const val DURATION = "duration"
        
        const val CHAPTERS = "chapters"
        const val CHAPTER = "chapter"
        const val CHAPTER_START = "start"
        const val CHAPTER_TITLE = "title"
        const val CHAPTER_HREF = "href"
        const val CHAPTER_IMAGE = "image"
    }
}
