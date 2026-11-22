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
    fun updateChaptersBulk_replacesOnlyTargetedEpisodesChapters() = runBlocking {
        val ep1 = Episode("ep1", "f", "T1", "D", "A", null, null, 0L, 0L, 0, null, false, 0L, null)
        val ep2 = Episode("ep2", "f", "T2", "D", "A", null, null, 0L, 0L, 0, null, false, 0L, null)
        episodeDao.insertEpisodes(listOf(ep1, ep2))

        // Initial chapters for both episodes
        chapterDao.insertChapters(
            listOf(
                Chapter(episodeId = "ep1", title = "Old", startTimeMs = 0),
                Chapter(episodeId = "ep2", title = "Keep Me", startTimeMs = 500)
            )
        )

        // Bulk update only ep1
        val newChapters = listOf(Chapter(episodeId = "ep1", title = "New", startTimeMs = 100))
        chapterDao.updateChaptersBulk(listOf("ep1"), newChapters)

        val ep1Results = chapterDao.getChaptersForEpisode("ep1").first()
        assertEquals(1, ep1Results.size)
        assertEquals("New", ep1Results[0].title)
        assertEquals(100L, ep1Results[0].startTimeMs)

        val ep2Results = chapterDao.getChaptersForEpisode("ep2").first()
        assertEquals(1, ep2Results.size)
        assertEquals("Keep Me", ep2Results[0].title)
        assertEquals(500L, ep2Results[0].startTimeMs)
    }
}
