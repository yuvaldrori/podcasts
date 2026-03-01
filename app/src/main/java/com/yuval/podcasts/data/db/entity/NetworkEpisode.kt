package com.yuval.podcasts.data.db.entity

/**
 * A partial entity used for updating existing Episodes from the network 
 * without overwriting user-modified states (like isPlayed or downloadStatus).
 * 
 * If the episode (id) doesn't exist, Room will insert a new row using the default
 * values defined in the main [Episode] entity for the missing fields.
 */
data class NetworkEpisode(
    val id: String,
    val podcastFeedUrl: String,
    val title: String,
    val description: String,
    val audioUrl: String,
    val imageUrl: String?,
    val pubDate: Long,
    val duration: Long
)
