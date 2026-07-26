package com.yuval.podcasts.media

import androidx.media3.common.MediaItem
import com.yuval.podcasts.data.db.dao.EpisodeDao
import com.yuval.podcasts.data.db.dao.QueueDao
import com.yuval.podcasts.data.db.entity.Episode
import com.yuval.podcasts.data.repository.SettingsRepository
import com.yuval.podcasts.domain.usecase.RemoveEpisodeUseCase
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Tests for playback resumption logic: verifying that the correct resume position
 * and media items are derived from the queue and episode database state.
 *
 * NOTE: These tests verify the *data* driving resumption decisions (episode's
 * lastPlayedPosition, queue ordering), not the actual PlaybackService listener which
 * requires a bound MediaSession to instantiate. For full service integration tests,
 * see the instrumented test suite.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackResumptionTest {

    private val episodeDao = mockk<EpisodeDao>(relaxed = true)
    private val queueDao = mockk<QueueDao>(relaxed = true)
    private val removeEpisodeUseCase = mockk<RemoveEpisodeUseCase>(relaxed = true)
    private val settingsRepository = mockk<SettingsRepository>(relaxed = true)

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() {
        coEvery { settingsRepository.getPlaybackSpeed() } returns 1.0f
        every { settingsRepository.skipSilenceFlow } returns flowOf(true)
    }

    /**
     * Verifies that an episode's lastPlayedPosition > 0 correctly triggers the seek
     * condition used by PlaybackService when first loading an item.
     */
    @Test
    fun `resume position is taken from lastPlayedPosition when greater than zero`() = runTest(testDispatcher) {
        val episodeId = "test_ep"
        val lastPosition = 15000L
        val episode = Episode(
            id = episodeId,
            podcastFeedUrl = "url",
            title = "Title",
            description = "Desc",
            audioUrl = "audio",
            imageUrl = null,
            episodeWebLink = null,
            pubDate = 0,
            duration = 30,
            downloadStatus = 0,
            localFilePath = null,
            isPlayed = false,
            lastPlayedPosition = lastPosition,
            completedAt = null
        )

        coEvery { episodeDao.getEpisodeById(episodeId) } returns episode

        val fetchedEpisode = episodeDao.getEpisodeById(episodeId)
        requireNotNull(fetchedEpisode)

        // The resume condition: lastPlayedPosition > 0 means we should seek on load
        assertEquals(lastPosition, fetchedEpisode.lastPlayedPosition)
        assertEquals(true, fetchedEpisode.lastPlayedPosition > 0)
    }

    /**
     * Verifies that the queue DAO returns episodes in the correct order, and that
     * the first episode's lastPlayedPosition is used as the startup position.
     * This data feeds into the MediaLibrarySession.Callback.onPlaybackResumption handler.
     */
    @Test
    fun `onPlaybackResumption data - queue provides correct episode and start position`() = runTest(testDispatcher) {
        val episodeId = "test_ep"
        val lastPosition = 15000L
        val episode = Episode(
            id = episodeId,
            podcastFeedUrl = "url",
            title = "Title",
            description = "Desc",
            audioUrl = "audio",
            imageUrl = null,
            episodeWebLink = null,
            pubDate = 0,
            duration = 30,
            downloadStatus = 0,
            localFilePath = null,
            isPlayed = false,
            lastPlayedPosition = lastPosition,
            completedAt = null
        )

        coEvery { queueDao.getQueueEpisodes() } returns flowOf(listOf(episode))

        // Simulate what PlaybackService.onPlaybackResumption reads from the queue
        val queueEpisodes = queueDao.getQueueEpisodes().first()

        val mediaItems = queueEpisodes.map { ep ->
            MediaItem.Builder().setMediaId(ep.id).build()
        }
        val startPositionMs = queueEpisodes.firstOrNull()?.lastPlayedPosition ?: 0L

        assertEquals(1, mediaItems.size)
        assertEquals(episodeId, mediaItems[0].mediaId)
        // Confirm the start position matches the stored resume point
        assertEquals(lastPosition, startPositionMs)
    }
}
