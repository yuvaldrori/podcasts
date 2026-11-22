package com.yuval.podcasts.media

import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yuval.podcasts.data.db.dao.QueueDao
import com.yuval.podcasts.data.db.entity.Episode
import com.yuval.podcasts.data.db.entity.EpisodeWithPodcast
import com.yuval.podcasts.data.db.entity.Podcast
import com.yuval.podcasts.data.repository.SettingsRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class PlaybackResumptionTest {

    private lateinit var queueDao: QueueDao
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var service: PlaybackService

    @Before
    fun setup() {
        queueDao = mockk(relaxed = true)
        settingsRepository = mockk(relaxed = true)
        service = PlaybackService()

        service.exoPlayer = mockk(relaxed = true)
        service.castPlayer = mockk(relaxed = true)
        service.episodeDao = mockk(relaxed = true)
        service.queueDao = queueDao
        service.removeEpisodeUseCase = mockk(relaxed = true)
        service.settingsRepository = settingsRepository

        val playerField = PlaybackService::class.java.getDeclaredField("currentPlayer")
        playerField.isAccessible = true
        playerField.set(service, service.exoPlayer)
    }

    private fun bindDispatchers(dispatcher: kotlinx.coroutines.CoroutineDispatcher, scope: kotlinx.coroutines.CoroutineScope) {
        service.ioDispatcher = dispatcher
        service.mainDispatcher = dispatcher
        val scopeField = PlaybackService::class.java.getDeclaredField("serviceScope")
        scopeField.isAccessible = true
        scopeField.set(service, scope)
    }

    private fun getCallback(): MediaLibrarySession.Callback {
        val field = PlaybackService::class.java.getDeclaredField("mediaSessionCallback")
        field.isAccessible = true
        return field.get(service) as MediaLibrarySession.Callback
    }

    @Test
    fun onPlaybackResumption_resumesLastPlayedEpisodeAtSavedPosition() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        bindDispatchers(dispatcher, this)

        val podcast = Podcast("feed-url", "Show Name", "Desc", "https://art.test/show.png", "https://web.test")
        val ep1 = Episode("ep1", "feed-url", "Ep 1", "D1", "https://cdn.test/1.mp3", null, null, 1000L, 600L, 0, null, false, 5000L, null)
        val ep2 = Episode("ep2", "feed-url", "Ep 2", "D2", "https://cdn.test/2.mp3", null, null, 2000L, 900L, 0, null, false, 42000L, null)

        every { queueDao.getQueueEpisodesWithPodcast() } returns flowOf(
            listOf(EpisodeWithPodcast(ep1, podcast), EpisodeWithPodcast(ep2, podcast))
        )
        coEvery { settingsRepository.getLastPlayedEpisodeId() } returns "ep2"

        val session = mockk<MediaSession>(relaxed = true)
        val controller = mockk<MediaSession.ControllerInfo>(relaxed = true)

        val result = getCallback().onPlaybackResumption(session, controller, true).await()

        assertEquals(2, result.mediaItems.size)
        assertEquals("ep1", result.mediaItems[0].mediaId)
        assertEquals("ep2", result.mediaItems[1].mediaId)
        assertEquals("Show Name", result.mediaItems[1].mediaMetadata.artist)
        assertEquals(1, result.startIndex)
        assertEquals(42000L, result.startPositionMs)
    }

    @Test
    fun onPlaybackResumption_fallsBackToFirstEpisodeWhenLastPlayedNotInQueue() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        bindDispatchers(dispatcher, this)

        val podcast = Podcast("feed-url", "Show Name", "Desc", "https://art.test/show.png", "https://web.test")
        val ep1 = Episode("ep1", "feed-url", "Ep 1", "D1", "https://cdn.test/1.mp3", null, null, 1000L, 600L, 0, null, false, 15000L, null)

        every { queueDao.getQueueEpisodesWithPodcast() } returns flowOf(listOf(EpisodeWithPodcast(ep1, podcast)))
        coEvery { settingsRepository.getLastPlayedEpisodeId() } returns "missing-ep"

        val session = mockk<MediaSession>(relaxed = true)
        val controller = mockk<MediaSession.ControllerInfo>(relaxed = true)

        val result = getCallback().onPlaybackResumption(session, controller, false).await()

        assertEquals(1, result.mediaItems.size)
        assertEquals(0, result.startIndex)
        assertEquals(15000L, result.startPositionMs)
    }

    @Test
    fun onPlaybackResumption_returnsEmptyResultWhenQueueIsEmpty() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        bindDispatchers(dispatcher, this)

        every { queueDao.getQueueEpisodesWithPodcast() } returns flowOf(emptyList())
        coEvery { settingsRepository.getLastPlayedEpisodeId() } returns null

        val session = mockk<MediaSession>(relaxed = true)
        val controller = mockk<MediaSession.ControllerInfo>(relaxed = true)

        val result = getCallback().onPlaybackResumption(session, controller, true).await()

        assertTrue(result.mediaItems.isEmpty())
        assertEquals(0, result.startIndex)
        assertEquals(0L, result.startPositionMs)
    }
}
