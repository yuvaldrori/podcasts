package com.yuval.podcasts.data.db.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.yuval.podcasts.data.db.AppDatabase
import com.yuval.podcasts.data.db.entity.Chapter
import com.yuval.podcasts.data.db.entity.Episode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ChapterDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var chapterDao: ChapterDao
    private lateinit var episodeDao: EpisodeDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        chapterDao = database.chapterDao()
        episodeDao = database.episodeDao()
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun deleteChaptersBulk_deletesOnlyTargetedEpisodes() = runBlocking {
        // Setup episodes (required for foreign key)
        val ep1 = Episode("ep1", "f", "T1", "D", "A", null, null, 0L, 0L, 0, null, false, 0L, null)
        val ep2 = Episode("ep2", "f", "T2", "D", "A", null, null, 0L, 0L, 0, null, false, 0L, null)
        val ep3 = Episode("ep3", "f", "T3", "D", "A", null, null, 0L, 0L, 0, null, false, 0L, null)
        episodeDao.insertEpisodes(listOf(ep1, ep2, ep3))

        // Setup chapters
        chapterDao.insertChapters(listOf(
            Chapter(episodeId = "ep1", title = "C1", startTimeMs = 0),
            Chapter(episodeId = "ep2", title = "C2", startTimeMs = 0),
            Chapter(episodeId = "ep3", title = "C3", startTimeMs = 0)
        ))

        // Bulk delete chapters for ep1 and ep2
        chapterDao.deleteChaptersBulk(listOf("ep1", "ep2"))

        assertEquals(0, chapterDao.getChaptersForEpisode("ep1").first().size)
        assertEquals(0, chapterDao.getChaptersForEpisode("ep2").first().size)
        assertEquals(1, chapterDao.getChaptersForEpisode("ep3").first().size)
    }

    @Test
    fun updateChaptersBulk_replacesChaptersEfficiently() = runBlocking {
        val ep1 = Episode("ep1", "f", "T1", "D", "A", null, null, 0L, 0L, 0, null, false, 0L, null)
        episodeDao.insertEpisodes(listOf(ep1))

        // Initial chapters
        chapterDao.insertChapters(listOf(Chapter(episodeId = "ep1", title = "Old", startTimeMs = 0)))

        // Bulk update
        val newChapters = listOf(Chapter(episodeId = "ep1", title = "New", startTimeMs = 100))
        chapterDao.updateChaptersBulk(listOf("ep1"), newChapters)

        val results = chapterDao.getChaptersForEpisode("ep1").first()
        assertEquals(1, results.size)
        assertEquals("New", results[0].title)
        assertEquals(100L, results[0].startTimeMs)
    }
}
