package com.yuval.podcasts.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.yuval.podcasts.data.db.entity.Podcast
import com.yuval.podcasts.data.db.entity.Episode
import com.yuval.podcasts.data.db.entity.QueueState
import com.yuval.podcasts.data.db.dao.PodcastDao
import com.yuval.podcasts.data.db.dao.EpisodeDao
import com.yuval.podcasts.data.db.dao.QueueDao

@Database(
    entities = [Podcast::class, Episode::class, QueueState::class],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun podcastDao(): PodcastDao
    abstract fun episodeDao(): EpisodeDao
    abstract fun queueDao(): QueueDao
}