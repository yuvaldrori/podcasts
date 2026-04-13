package com.yuval.podcasts.data.repository

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.yuval.podcasts.data.db.AppDatabase
import com.yuval.podcasts.data.db.dao.EpisodeDao
import com.yuval.podcasts.data.db.dao.PodcastDao
import com.yuval.podcasts.data.db.dao.QueueDao
import com.yuval.podcasts.data.db.entity.Episode
import com.yuval.podcasts.data.network.PodcastRemoteDataSource
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class HistoryBackupTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var podcastDao: PodcastDao
    private lateinit var episodeDao: EpisodeDao
    private lateinit var queueDao: QueueDao
    private lateinit var repository: PodcastRepository
    private lateinit var contentResolver: ContentResolver

    @Before
    fun setup() {
        context = mockk(relaxed = true)
        database = mockk(relaxed = true)
        podcastDao = mockk(relaxed = true)
        episodeDao = mockk(relaxed = true)
        queueDao = mockk(relaxed = true)
        contentResolver = mockk(relaxed = true)

        every { context.contentResolver } returns contentResolver
        every { podcastDao.getAllPodcasts() } returns flowOf(emptyList())
        every { queueDao.getQueueEpisodesWithPodcast() } returns flowOf(emptyList())

        repository = DefaultPodcastRepository(
            context = context,
            database = database,
            remoteDataSource = mockk(relaxed = true),
            podcastDao = podcastDao,
            episodeDao = episodeDao,
            queueDao = queueDao,
            workManager = mockk(relaxed = true),
            localMediaDataSource = mockk(relaxed = true),
            ioDispatcher = Dispatchers.Unconfined
        )
    }

    @Test
    fun exportHistory_writesCorrectJson() = runTest {
        val playedEpisodes = listOf(
            Episode("ep1", "url1", "Title1", "Desc1", "audio1", null, null, 1000L, 60L, 2, null, true, 0L, 5000L, 1L),
            Episode("ep2", "url2", "Title2", "Desc2", "audio2", null, null, 2000L, 120L, 2, null, true, 0L, 6000L, 2L)
        )
        coEvery { episodeDao.getPlayedEpisodes() } returns playedEpisodes
        
        val outputStream = ByteArrayOutputStream()
        val uri = mockk<Uri>()
        every { contentResolver.openOutputStream(uri) } returns outputStream

        val result = repository.exportHistory(context, uri)

        assertTrue(result.isSuccess)
        val json = outputStream.toString()
        assertTrue(json.contains("ep1"))
        assertTrue(json.contains("ep2"))
        assertTrue(json.contains("5000"))
        assertTrue(json.contains("6000"))
    }

    @Test
    fun importHistory_marksExistingEpisodesAsPlayed() = runTest {
        val json = """
            {
                "entries": [
                    {"episodeId": "ep1", "podcastFeedUrl": "url1", "completedAt": 5000},
                    {"episodeId": "ep2", "podcastFeedUrl": "url2", "completedAt": 6000}
                ]
            }
        """.trimIndent()
        
        val inputStream = ByteArrayInputStream(json.toByteArray())
        val uri = mockk<Uri>()
        every { contentResolver.openInputStream(uri) } returns inputStream
        
        coEvery { episodeDao.markAsPlayedBulk(any(), any()) } returns Unit

        val result = repository.importHistory(uri)

        assertTrue(result.isSuccess)
        coVerify { episodeDao.markAsPlayedBulk("ep1", 5000L) }
        coVerify { episodeDao.markAsPlayedBulk("ep2", 6000L) }
    }

    @Test
    fun importHistory_handlesMissingEpisodes_gracefully() = runTest {
        // This simulates the edge case: user imports history JSON, 
        // but the episodes aren't in the DB yet (sync not finished).
        // markAsPlayedBulk in the DAO is an UPDATE query. 
        // If the row doesn't exist, UPDATE simply updates 0 rows. SQL doesn't fail.
        
        val json = """
            {
                "entries": [
                    {"episodeId": "not_in_db", "podcastFeedUrl": "url1", "completedAt": 7000}
                ]
            }
        """.trimIndent()
        
        val inputStream = ByteArrayInputStream(json.toByteArray())
        val uri = mockk<Uri>()
        every { contentResolver.openInputStream(uri) } returns inputStream
        
        // Mocking DAO to behave like SQL (completes without error even if 0 rows affected)
        coEvery { episodeDao.markAsPlayedBulk(any(), any()) } returns Unit

        val result = repository.importHistory(uri)

        assertTrue(result.isSuccess)
        coVerify { episodeDao.markAsPlayedBulk("not_in_db", 7000L) }
    }

    @Test
    fun importHistory_failsOnMalformedJson() = runTest {
        val json = "invalid json"
        val inputStream = ByteArrayInputStream(json.toByteArray())
        val uri = mockk<Uri>()
        every { contentResolver.openInputStream(uri) } returns inputStream

        val result = repository.importHistory(uri)

        assertTrue(result.isFailure)
    }
}
