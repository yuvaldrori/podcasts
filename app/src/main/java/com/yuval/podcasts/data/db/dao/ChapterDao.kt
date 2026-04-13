package com.yuval.podcasts.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.yuval.podcasts.data.db.entity.Chapter
import kotlinx.coroutines.flow.Flow

@Dao
interface ChapterDao {
    @Query("SELECT * FROM chapters WHERE episodeId = :episodeId ORDER BY startTimeMs ASC")
    fun getChaptersForEpisode(episodeId: String): Flow<List<Chapter>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChapters(chapters: List<Chapter>)

    @Query("DELETE FROM chapters WHERE episodeId = :episodeId")
    suspend fun deleteChaptersForEpisode(episodeId: String)

    @Transaction
    suspend fun updateChapters(episodeId: String, chapters: List<Chapter>) {
        deleteChaptersForEpisode(episodeId)
        insertChapters(chapters)
    }

    @Transaction
    suspend fun updateChaptersBulk(episodeIds: List<String>, chapters: List<Chapter>) {
        episodeIds.forEach { deleteChaptersForEpisode(it) }
        insertChapters(chapters)
    }
}
