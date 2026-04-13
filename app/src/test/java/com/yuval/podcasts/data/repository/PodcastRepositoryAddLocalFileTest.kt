package com.yuval.podcasts.data.repository

import com.yuval.podcasts.data.Constants
import com.yuval.podcasts.data.db.dao.EpisodeDao
import com.yuval.podcasts.data.db.dao.PodcastDao
import com.yuval.podcasts.data.db.dao.ChapterDao
import com.yuval.podcasts.data.db.entity.Episode
import com.yuval.podcasts.data.db.entity.Podcast
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class PodcastRepositoryAddLocalFileTest {

    private lateinit var podcastDao: PodcastDao
    private lateinit var episodeDao: EpisodeDao
    private lateinit var chapterDao: ChapterDao
    private lateinit var localMediaDataSource: LocalMediaDataSource
    private lateinit var repository: DefaultPodcastRepository

    @Before
    fun setup() {
        podcastDao = mockk(relaxed = true)
        episodeDao = mockk(relaxed = true)
        chapterDao = mockk(relaxed = true)
        localMediaDataSource = mockk()

        repository = DefaultPodcastRepository(
            context = mockk(relaxed = true),
            database = mockk(relaxed = true),
            remoteDataSource = mockk(relaxed = true),
            
            podcastDao = podcastDao,
            episodeDao = episodeDao,
            queueDao = mockk(relaxed = true),
            chapterDao = chapterDao,
            workManager = mockk(relaxed = true),
            localMediaDataSource = localMediaDataSource,
            ioDispatcher = Dispatchers.Unconfined
        )
    }

    @Test
    fun `addLocalFile inserts local podcast and episode on success`() = runTest {
        val uri = mockk<android.net.Uri>()
        val mockFile = File("/mock/path/test.mp3")
        val metadata = LocalMediaMetadata("Title", "Artist", 120L, "Desc", mockFile)
        
        coEvery { localMediaDataSource.copyAndExtract(uri) } returns Result.success(metadata)
        coEvery { podcastDao.getPodcast(Constants.LOCAL_PODCAST_FEED_URL) } returns null

        val result = repository.addLocalFile(uri)
        
        assertTrue(result.isSuccess)
        
        coVerify { podcastDao.insertPodcast(match { it.feedUrl == Constants.LOCAL_PODCAST_FEED_URL }) }
        
        val episodesSlot = slot<List<Episode>>()
        coVerify { episodeDao.insertEpisodes(capture(episodesSlot)) }
        
        val episode = episodesSlot.captured.first()
        assertEquals("Title", episode.title)
        assertEquals("Desc", episode.description)
        assertEquals(120L, episode.duration)
        assertEquals("/mock/path/test.mp3", episode.audioUrl)
        assertEquals(Constants.LOCAL_PODCAST_FEED_URL, episode.podcastFeedUrl)
        assertEquals(2, episode.downloadStatus)
    }
}
