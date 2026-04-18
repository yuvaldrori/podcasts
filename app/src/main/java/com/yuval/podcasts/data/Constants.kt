package com.yuval.podcasts.data

object Constants {
    const val LOCAL_PODCAST_FEED_URL = "local_subscription"
    const val DATABASE_NAME = "podcasts_db"
    
    // Media & Playback
    const val DEFAULT_PLAYBACK_SPEED = 2.0f
    const val SEEK_FORWARD_MS = 30000L
    const val SEEK_BACKWARD_MS = 15000L
    const val SAVE_POSITION_INTERVAL_MS = 15000L
    const val NETWORK_TIMEOUT_MS = 30000
    
    const val COMMAND_REWIND_10 = "REWIND_10"
    const val COMMAND_SKIP_30 = "SKIP_30"
    const val REWIND_10_MS = 10000L
    const val SKIP_30_MS = 30000L

    // UI & ViewModel
    const val FLOW_STOP_TIMEOUT_MS = 5000L
    const val UNPLAYED_EPISODES_LIMIT = 150

    // WorkManager Names
    const val WORK_NAME_CLEANUP = "cleanup_orphaned_files"
    const val WORK_NAME_SYNC_ALL = "sync_all_podcasts"
    const val WORK_NAME_OPML_IMPORT = "opml_import"
    const val WORK_NAME_PERIODIC_SYNC = "sync_work"
    const val WORK_TAG_DOWNLOAD_PREFIX = "download_"

    // Storage
    const val DOWNLOAD_DIR_NAME = "podcasts"
    const val SYNC_RETRY_COUNT = 3
    const val PERIODIC_SYNC_HOURS = 12L

    // Notification IDs
    const val NOTIFICATION_ID_DOWNLOAD = 1
    const val NOTIFICATION_ID_IMPORT = 2

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
