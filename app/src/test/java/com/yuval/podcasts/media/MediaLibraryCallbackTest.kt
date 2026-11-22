package com.yuval.podcasts.media

import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yuval.podcasts.data.db.dao.QueueDao
import com.yuval.podcasts.data.db.entity.Episode
import com.yuval.podcasts.data.db.entity.EpisodeWithPodcast
import com.yuval.podcasts.data.db.entity.Podcast
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

        val field = PlaybackService::class.java.getDeclaredField("currentPlayer")
        field.isAccessible = true
        field.set(service, service.exoPlayer)
    }

    private fun bindDispatchers(dispatcher: kotlinx.coroutines.CoroutineDispatcher, scope: CoroutineScope) {
        service.ioDispatcher = dispatcher
        service.mainDispatcher = dispatcher
        val scopeField = PlaybackService::class.java.getDeclaredField("serviceScope")
        scopeField.isAccessible = true
        scopeField.set(service, scope)
    }

    @Test
    fun onGetLibraryRoot_returnsBrowsableRoot() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        bindDispatchers(dispatcher, this)
        val callback = getCallback(service)
        val session = mockk<MediaLibrarySession>(relaxed = true)
        val controller = mockk<MediaSession.ControllerInfo>(relaxed = true)

        val result = callback.onGetLibraryRoot(session, controller, null).await()

        assertEquals("root", result.value?.mediaId)
        assertTrue(result.value?.mediaMetadata?.isBrowsable == true)
        assertTrue(result.value?.mediaMetadata?.isPlayable == false)
    }

    @Test
    fun onGetChildren_root_returnsQueueFolder() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        bindDispatchers(dispatcher, this)
        val callback = getCallback(service)
        val session = mockk<MediaLibrarySession>(relaxed = true)
        val controller = mockk<MediaSession.ControllerInfo>(relaxed = true)

        val result = callback.onGetChildren(session, controller, "root", 0, 100, null).await()
        val items = result.value

        assertEquals(1, items?.size)
        assertEquals("queue", items?.get(0)?.mediaId)
        assertEquals("Queue", items?.get(0)?.mediaMetadata?.title)
        assertTrue(items?.get(0)?.mediaMetadata?.isBrowsable == true)
        assertTrue(items?.get(0)?.mediaMetadata?.isPlayable == false)
    }

    @Test
    fun onGetChildren_queue_returnsMappedQueueEpisodesWithPodcastMetadata() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        bindDispatchers(dispatcher, this)
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

        val dummyPodcast = Podcast(
            feedUrl = "feed_url",
            title = "Podcast Title",
            description = "Description",
            imageUrl = "http://podcast.art/img.jpg",
            website = "http://website.com"
        )
        val dummyEpisodeWithPodcast = EpisodeWithPodcast(dummyEpisode, dummyPodcast)
        coEvery { queueDao.getQueueEpisodesWithPodcastSync() } returns listOf(dummyEpisodeWithPodcast)

        val result = callback.onGetChildren(session, controller, "queue", 0, 100, null).await()
        val items = result.value

        assertEquals(1, items?.size)
        val item = items!![0]
        assertEquals("episode_1", item.mediaId)
        assertEquals("Episode 1", item.mediaMetadata.title)
        assertEquals("Podcast Title", item.mediaMetadata.artist)
        assertEquals("Podcast Title", item.mediaMetadata.albumTitle)
        assertEquals("http://podcast.art/img.jpg", item.mediaMetadata.artworkUri?.toString())
        assertTrue(item.mediaMetadata.isPlayable == true)
        assertTrue(item.mediaMetadata.isBrowsable == false)
    }

    private fun getCallback(service: PlaybackService): MediaLibrarySession.Callback {
        val field = PlaybackService::class.java.getDeclaredField("mediaSessionCallback")
        field.isAccessible = true
        return field.get(service) as MediaLibrarySession.Callback
    }
}
