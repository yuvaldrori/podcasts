package com.yuval.podcasts.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.yuval.podcasts.data.db.entity.Episode
import com.yuval.podcasts.data.db.entity.EpisodeWithPodcast
import com.yuval.podcasts.data.db.entity.QueueState
import kotlinx.coroutines.flow.Flow

@Dao
interface QueueDao {
    @Transaction
    @Query("""
        SELECT episodes.* FROM episodes 
        INNER JOIN queue ON episodes.id = queue.episodeId 
        ORDER BY queue.position ASC
    """)
    fun getQueueEpisodesWithPodcast(): Flow<List<EpisodeWithPodcast>>

    @Query("""
        SELECT episodes.* FROM episodes 
        INNER JOIN queue ON episodes.id = queue.episodeId 
        ORDER BY queue.position ASC
    """)
    fun getQueueEpisodes(): Flow<List<Episode>>

    @Query("SELECT * FROM queue ORDER BY position ASC")
    fun getQueue(): Flow<List<QueueState>>

    @Query("""
        SELECT episodes.* FROM episodes 
        INNER JOIN queue ON episodes.id = queue.episodeId 
        ORDER BY queue.position ASC 
        LIMIT 1
    """)
    suspend fun getNextEpisode(): Episode?

    @Transaction
    suspend fun updateQueue(queue: List<QueueState>) {
        clearQueue()
        insertQueue(queue)
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQueue(queue: List<QueueState>)

    @Query("DELETE FROM queue")
    suspend fun clearQueue()

    @Query("DELETE FROM queue WHERE episodeId = :episodeId")
    suspend fun removeFromQueue(episodeId: String)

    @Query("UPDATE queue SET position = position + 1")
    suspend fun shiftQueuePositionsUp()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQueueItem(queueState: QueueState)

    @Query("""
        SELECT episodes.* FROM episodes 
        INNER JOIN queue ON episodes.id = queue.episodeId 
        WHERE episodes.downloadStatus != 2
    """)
    suspend fun getQueuedEpisodesNotDownloaded(): List<Episode>
}