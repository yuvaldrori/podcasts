package com.yuval.podcasts.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.yuval.podcasts.data.db.entity.QueueState
import kotlinx.coroutines.flow.Flow

@Dao
interface QueueDao {
    @Query("SELECT * FROM queue ORDER BY position ASC")
    fun getQueue(): Flow<List<QueueState>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun updateQueue(queue: List<QueueState>)

    @Query("DELETE FROM queue WHERE episodeId = :episodeId")
    suspend fun removeFromQueue(episodeId: String)
}