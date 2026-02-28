package com.yuval.podcasts.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.yuval.podcasts.data.db.entity.Episode
import com.yuval.podcasts.data.db.entity.EpisodeWithPodcast
import kotlinx.coroutines.flow.Flow

@Dao
interface EpisodeDao {
    @Query("SELECT * FROM episodes WHERE podcastFeedUrl = :feedUrl ORDER BY pubDate DESC")
    fun getEpisodesForPodcast(feedUrl: String): Flow<List<Episode>>

    @Transaction
    @Query("SELECT * FROM episodes WHERE isPlayed = 0 ORDER BY pubDate DESC LIMIT 150")
    fun getUnplayedEpisodesWithPodcast(): Flow<List<EpisodeWithPodcast>>

    @Query("SELECT * FROM episodes WHERE isPlayed = 0 ORDER BY pubDate DESC LIMIT 150")
    fun getUnplayedEpisodes(): Flow<List<Episode>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEpisodes(episodes: List<Episode>)

    @Query("SELECT * FROM episodes WHERE id = :id")
    suspend fun getEpisodeById(id: String): Episode?

    @Query("SELECT * FROM episodes WHERE id = :id")
    fun getEpisodeByIdFlow(id: String): Flow<Episode?>

    @Transaction
    @Query("SELECT * FROM episodes WHERE id = :id")
    fun getEpisodeWithPodcastFlow(id: String): Flow<EpisodeWithPodcast?>

    @Query("UPDATE episodes SET downloadStatus = :status, localFilePath = :path WHERE id = :id")
    suspend fun updateDownloadStatus(id: String, status: Int, path: String?)

    @Query("UPDATE episodes SET isPlayed = :isPlayed WHERE id = :id")
    suspend fun updatePlaybackStatus(id: String, isPlayed: Boolean)

    @Query("UPDATE episodes SET lastPlayedPosition = :position WHERE id = :id")
    suspend fun updateLastPlayedPosition(id: String, position: Long)

    @Query("UPDATE episodes SET isPlayed = 1 WHERE isPlayed = 0")
    suspend fun markAllUnplayedAsPlayed()
}