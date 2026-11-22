package com.yuval.podcasts.media

import androidx.media3.common.MediaItem
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yuval.podcasts.data.db.dao.EpisodeDao
import com.yuval.podcasts.data.db.entity.Episode
import com.yuval.podcasts.data.db.entity.EpisodeWithPodcast
import com.yuval.podcasts.data.db.entity.Podcast
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class MediaSessionCallbackTest {

    private lateinit var episodeDao: EpisodeDao
    private lateinit var service: PlaybackService

    @Before
    fun setup() {
        episodeDao = mockk(relaxed = true)
        service = PlaybackService()
        service.exoPlayer = mockk(relaxed = true)
        service.castPlayer = mockk(relaxed = true)
        service.episodeDao = episodeDao
        service.queueDao = mockk(relaxed = true)
        service.removeEpisodeUseCase = mockk(relaxed = true)
        service.settingsRepository = mockk(relaxed = true)

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
    fun onAddMediaItems_stubItemResolvesToEpisodeMediaItem() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        bindDispatchers(dispatcher, this)

        val episode = Episode(
            id = "ext-id",
            podcastFeedUrl = "url",
            title = "Resolved Title",
            description = "",
            audioUrl = "http://audio.com/ep.mp3",
            pubDate = 0,
            duration = 0,
            downloadStatus = 0,
            localFilePath = null
        )
        val podcast = Podcast(
            feedUrl = "url",
            title = "Podcast Title",
            description = "",
            imageUrl = "https://podcast.art/cover.png",
            website = ""
        )
        coEvery { episodeDao.getEpisodeWithPodcast("ext-id") } returns EpisodeWithPodcast(episode, podcast)

        val session = mockk<MediaSession>(relaxed = true)
        val controller = mockk<MediaSession.ControllerInfo>(relaxed = true)
        val inputItems = mutableListOf(MediaItem.Builder().setMediaId("ext-id").build())

        val resolvedItems = getCallback().onAddMediaItems(session, controller, inputItems).await()

        assertEquals(1, resolvedItems.size)
        assertEquals("Resolved Title", resolvedItems[0].mediaMetadata.title)
        assertEquals("Podcast Title", resolvedItems[0].mediaMetadata.artist)
        assertEquals("Podcast Title", resolvedItems[0].mediaMetadata.albumTitle)
        assertEquals("http://audio.com/ep.mp3", resolvedItems[0].localConfiguration?.uri?.toString())
        assertEquals("https://podcast.art/cover.png", resolvedItems[0].mediaMetadata.artworkUri?.toString())
        coVerify(exactly = 1) { episodeDao.getEpisodeWithPodcast("ext-id") }
    }

    @Test
    fun onAddMediaItems_configuredItemPassesThroughWithoutDaoLookup() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        bindDispatchers(dispatcher, this)

        val configuredItem = MediaItem.Builder()
            .setMediaId("local-id")
            .setUri("http://audio.com/already-configured.mp3")
            .build()
        val session = mockk<MediaSession>(relaxed = true)
        val controller = mockk<MediaSession.ControllerInfo>(relaxed = true)

        val resolvedItems = getCallback().onAddMediaItems(session, controller, mutableListOf(configuredItem)).await()

        assertEquals(1, resolvedItems.size)
        assertEquals(configuredItem, resolvedItems[0])
        coVerify(exactly = 0) { episodeDao.getEpisodeWithPodcast(any()) }
    }
}
