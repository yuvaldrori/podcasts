package com.yuval.podcasts.data.repository

import kotlinx.serialization.Serializable

@Serializable
data class HistoryEntry(
    val episodeId: String,
    val podcastFeedUrl: String,
    val completedAt: Long
)

@Serializable
data class HistoryBackup(
    val entries: List<HistoryEntry>
)
