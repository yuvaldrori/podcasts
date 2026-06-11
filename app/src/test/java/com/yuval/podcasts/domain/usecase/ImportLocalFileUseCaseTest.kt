package com.yuval.podcasts.domain.usecase

import android.content.Context
import android.net.Uri
import com.yuval.podcasts.data.Constants
import com.yuval.podcasts.data.db.entity.Episode
import com.yuval.podcasts.data.repository.LocalMediaDataSource
import com.yuval.podcasts.data.repository.LocalMediaMetadata
import com.yuval.podcasts.data.repository.PodcastRepository
import com.yuval.podcasts.utils.LogManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class ImportLocalFileUseCaseTest {

    private lateinit var repository: PodcastRepository
    private lateinit var localMediaDataSource: LocalMediaDataSource
    private lateinit var context: Context
    private lateinit var logManager: LogManager
    private lateinit var useCase: ImportLocalFileUseCase

    @Before
    fun setup() {
        repository = mockk(relaxed = true)
        localMediaDataSource = mockk()
        context = mockk(relaxed = true)
        logManager = mockk(relaxed = true)
        
        useCase = ImportLocalFileUseCase(context, repository, localMediaDataSource, logManager)
    }

    @Test
    fun `invoke inserts local podcast and episode on success`() = runTest {
        val uri = mockk<Uri>()
        val mockFile = File("/mock/path/test.mp3")
        val metadata = LocalMediaMetadata("Title", "Artist", 120L, "Desc", mockFile)
        
        coEvery { localMediaDataSource.copyAndExtract(uri) } returns Result.success(metadata)
        coEvery { repository.getPodcast(Constants.LOCAL_PODCAST_FEED_URL) } returns null

        val result = useCase(uri)
        
        assertTrue(result.isSuccess)
        
        coVerify { repository.insertPodcast(match { it.feedUrl == Constants.LOCAL_PODCAST_FEED_URL }) }
        
        val episodesSlot = slot<List<Episode>>()
        coVerify { repository.insertEpisodes(capture(episodesSlot)) }
        
        val episode = episodesSlot.captured.first()
        assertEquals("Title", episode.title)
        assertEquals("Desc", episode.description)
        assertEquals(120L, episode.duration)
        assertEquals("/mock/path/test.mp3", episode.audioUrl)
        assertEquals(Constants.LOCAL_PODCAST_FEED_URL, episode.podcastFeedUrl)
        assertEquals(2, episode.downloadStatus)
    }
}
