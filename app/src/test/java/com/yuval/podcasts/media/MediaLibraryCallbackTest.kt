package com.yuval.podcasts.media

import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaLibraryService.LibraryParams
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaSession
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yuval.podcasts.data.db.dao.QueueDao
import com.yuval.podcasts.data.db.entity.Episode
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class MediaLibraryCallbackTest {

    private lateinit var queueDao: QueueDao
    private lateinit var service: PlaybackService

    @Before
    fun setup() {
        queueDao = mockk(relaxed = true)
        service = PlaybackService()
        
        service.exoPlayer = mockk(relaxed = true)
        service.castPlayer = mockk(relaxed = true)
        service.episodeDao = mockk(relaxed = true)
        service.queueDao = queueDao
        service.removeEpisodeUseCase = mockk(relaxed = true)
        service.settingsRepository = mockk(relaxed = true)
        service.ioDispatcher = Dispatchers.Unconfined
        service.mainDispatcher = Dispatchers.Unconfined
        
        // Initialize private currentPlayer using reflection
        val field = PlaybackService::class.java.getDeclaredField("currentPlayer")
        field.isAccessible = true
        field.set(service, service.exoPlayer)

        // Initialize serviceScope using reflection
        val scopeField = PlaybackService::class.java.getDeclaredField("serviceScope")
        scopeField.isAccessible = true
        scopeField.set(service, kotlinx.coroutines.CoroutineScope(Dispatchers.Unconfined))
    }

    @Test
    fun onGetLibraryRoot_returnsBrowsableRoot() = runTest {
        val callback = getCallback(service)
        val session = mockk<MediaLibrarySession>(relaxed = true)
        val controller = mockk<MediaSession.ControllerInfo>(relaxed = true)
        
        val result = callback.onGetLibraryRoot(session, controller, null).get()
        
        assertEquals("root", result.value?.mediaId)
        assertTrue(result.value?.mediaMetadata?.isBrowsable == true)
        assertTrue(result.value?.mediaMetadata?.isPlayable == false)
    }

    @Test
    fun onGetChildren_root_returnsQueueFolder() = runTest {
        val callback = getCallback(service)
        val session = mockk<MediaLibrarySession>(relaxed = true)
        val controller = mockk<MediaSession.ControllerInfo>(relaxed = true)
        
        val result = callback.onGetChildren(session, controller, "root", 0, 100, null).get()
        val items = result.value
        
        assertEquals(1, items?.size)
        assertEquals("queue", items?.get(0)?.mediaId)
        assertEquals("Queue", items?.get(0)?.mediaMetadata?.title)
        assertTrue(items?.get(0)?.mediaMetadata?.isBrowsable == true)
        assertTrue(items?.get(0)?.mediaMetadata?.isPlayable == false)
    }

    @Test
    fun onGetChildren_queue_returnsMappedQueueEpisodes() = runTest {
        val callback = getCallback(service)
        val session = mockk<MediaLibrarySession>(relaxed = true)
        val controller = mockk<MediaSession.ControllerInfo>(relaxed = true)
        
        val dummyEpisode = Episode(
            id = "episode_1",
            podcastFeedUrl = "feed_url",
            title = "Episode 1",
            description = "Description",
            audioUrl = "http://audio.mp3",
            pubDate = 0L,
            duration = 1000,
            downloadStatus = 0,
            localFilePath = null
        )
        
        coEvery { queueDao.getQueueEpisodesSync() } returns listOf(dummyEpisode)
        
        val result = callback.onGetChildren(session, controller, "queue", 0, 100, null).get()
        val items = result.value
        
        assertEquals(1, items?.size)
        assertEquals("episode_1", items?.get(0)?.mediaId)
        assertEquals("Episode 1", items?.get(0)?.mediaMetadata?.title)
        assertTrue(items?.get(0)?.mediaMetadata?.isPlayable == true)
        assertTrue(items?.get(0)?.mediaMetadata?.isBrowsable == false)
    }

    private fun getCallback(service: PlaybackService): MediaLibrarySession.Callback {
        val field = PlaybackService::class.java.getDeclaredField("mediaSessionCallback")
        field.isAccessible = true
        return field.get(service) as MediaLibrarySession.Callback
    }
}
