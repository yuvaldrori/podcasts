package com.yuval.podcasts.data.db.entity

data class NetworkEpisodeWithChapters(
    val episode: NetworkEpisode,
    val chapters: List<Chapter>
)
