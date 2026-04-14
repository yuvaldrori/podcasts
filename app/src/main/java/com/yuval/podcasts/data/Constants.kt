package com.yuval.podcasts.data

object Constants {
    const val LOCAL_PODCAST_FEED_URL = "local_subscription"
    
    // Media & Playback
    const val SEEK_FORWARD_MS = 30000L
    const val SEEK_BACKWARD_MS = 15000L
    const val SAVE_POSITION_INTERVAL_MS = 15000L
    const val NETWORK_TIMEOUT_MS = 30000
    
    // UI & ViewModel
    const val FLOW_STOP_TIMEOUT_MS = 5000L

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
