package com.yuval.podcasts.data.db.entity

data class ParsedPodcast(
    val podcast: Podcast,
    val episodes: List<NetworkEpisodeWithChapters>
)
