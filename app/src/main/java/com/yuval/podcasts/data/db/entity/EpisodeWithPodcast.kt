package com.yuval.podcasts.data.db.entity

import androidx.room.Embedded
import androidx.room.Relation

data class EpisodeWithPodcast(
    @Embedded val episode: Episode,
    @Relation(
        parentColumn = "podcastFeedUrl",
        entityColumn = "feedUrl"
    )
    val podcast: Podcast
)
