package com.yuval.podcasts.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.yuval.podcasts.data.db.entity.Podcast
import kotlinx.coroutines.flow.Flow

@Dao
interface PodcastDao {
    @Query("""
        SELECT podcasts.* FROM podcasts 
        LEFT JOIN episodes ON podcasts.feedUrl = episodes.podcastFeedUrl 
        GROUP BY podcasts.feedUrl 
        ORDER BY MAX(episodes.pubDate) DESC
    """)
    fun getAllPodcasts(): Flow<List<Podcast>>

    @Query("SELECT * FROM podcasts WHERE feedUrl = :feedUrl")
    suspend fun getPodcast(feedUrl: String): Podcast?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPodcast(podcast: Podcast)

    @Query("DELETE FROM podcasts WHERE feedUrl = :feedUrl")
    suspend fun deletePodcast(feedUrl: String)
}