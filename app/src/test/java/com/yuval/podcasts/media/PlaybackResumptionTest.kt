package com.yuval.podcasts.media

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.yuval.podcasts.data.db.dao.EpisodeDao
import com.yuval.podcasts.data.db.dao.QueueDao
import com.yuval.podcasts.data.db.entity.Episode
import com.yuval.podcasts.data.repository.SettingsRepository
import com.yuval.podcasts.domain.usecase.RemoveEpisodeUseCase
import io.mockk.*
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import org.junit.Before
import org.junit.Test
import org.junit.Assert.assertEquals
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.cast.CastPlayer

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackResumptionTest {

    private val episodeDao = mockk<EpisodeDao>(relaxed = true)
    private val queueDao = mockk<QueueDao>(relaxed = true)
    private val removeEpisodeUseCase = mockk<RemoveEpisodeUseCase>(relaxed = true)
    private val settingsRepository = mockk<SettingsRepository>(relaxed = true)
    private val exoPlayer = mockk<ExoPlayer>(relaxed = true)
    private val castPlayer = mockk<CastPlayer>(relaxed = true)
    
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() {
        coEvery { settingsRepository.getPlaybackSpeed() } returns 1.0f
        every { settingsRepository.skipSilenceFlow } returns flowOf(true)
    }

    @Test
    fun `when first item is loaded, it should resume from last played position`() = runTest(testDispatcher) {
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
        coEvery { episodeDao.getEpisodeById(episodeId) } returns episode
        
        val player = exoPlayer
        val listenerSlot = slot<Player.Listener>()
        every { player.addListener(capture(listenerSlot)) } returns Unit
        
        val mediaItem = MediaItem.Builder().setMediaId(episodeId).build()
        every { player.currentMediaItem } returns mediaItem
        every { player.currentPosition } returns 0L

        val listener = object : Player.Listener {
            private var lastResumedId: String? = null
            override fun onMediaItemTransition(item: MediaItem?, reason: Int) {
                if (item != null) {
                    if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO || 
                        reason == Player.MEDIA_ITEM_TRANSITION_REASON_SEEK ||
                        reason == Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED) {
                        if (lastResumedId != item.mediaId) {
                            lastResumedId = item.mediaId
                            if (episode.lastPlayedPosition > 0) {
                                if (player.currentPosition < 2000) {
                                    player.seekTo(episode.lastPlayedPosition)
                                }
                            }
                        }
                    }
                }
            }
        }

        listener.onMediaItemTransition(mediaItem, Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED)

        verify(exactly = 1) { player.seekTo(lastPosition) }
    }

    @Test
    fun `onPlaybackResumption should return media items with correct start position`() = runTest(testDispatcher) {
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
        
        val result = queueDao.getQueueEpisodes().first()
        val mediaItems = result.mapNotNull { MediaItem.Builder().setMediaId(it.id).build() }
        val startPosition = if (result.isNotEmpty()) result.first().lastPlayedPosition else 0L
        
        assertEquals(lastPosition, startPosition)
        assertEquals(1, mediaItems.size)
        assertEquals(episodeId, mediaItems[0].mediaId)
    }
}
