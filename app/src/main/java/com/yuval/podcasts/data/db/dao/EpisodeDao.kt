package com.yuval.podcasts.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.yuval.podcasts.data.db.entity.Episode
import kotlinx.coroutines.flow.Flow

@Dao
interface EpisodeDao {
    @Query("SELECT * FROM episodes WHERE podcastFeedUrl = :feedUrl ORDER BY pubDate DESC")
    fun getEpisodesForPodcast(feedUrl: String): Flow<List<Episode>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEpisodes(episodes: List<Episode>)

    @Query("SELECT * FROM episodes WHERE id = :id")
    suspend fun getEpisodeById(id: String): Episode?

    @Query("UPDATE episodes SET downloadStatus = :status, localFilePath = :path WHERE id = :id")
    suspend fun updateDownloadStatus(id: String, status: Int, path: String?)
}