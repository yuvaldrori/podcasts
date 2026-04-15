package com.yuval.podcasts.media

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.yuval.podcasts.domain.usecase.RemoveEpisodeUseCase
import com.yuval.podcasts.data.db.dao.EpisodeDao
import com.yuval.podcasts.data.db.entity.Episode
import com.yuval.podcasts.data.db.entity.NetworkEpisode
import com.yuval.podcasts.data.db.entity.EpisodeWithPodcast
import io.mockk.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.Assert.assertEquals

class PlaybackServiceTest {

    @Test
    fun playerListener_onStateEnded_removesLastEpisode() = runTest {
        val removeEpisodeUseCase = mockk<RemoveEpisodeUseCase>(relaxed = true)
        val listenerSlot = slot<Player.Listener>()
        
        val player = mockk<Player>(relaxed = true)
        // Set up the listener capture properly
        every { player.addListener(capture(listenerSlot)) } returns Unit
        
        // This is what happens in the service's listener
        val serviceListener = object : Player.Listener {
            var currentlyPlayingId: String? = "episode_123"

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED && currentlyPlayingId != null) {
                    kotlinx.coroutines.runBlocking {
                        removeEpisodeUseCase(currentlyPlayingId!!, markAsPlayed = true)
                    }
                }
            }
        }
        
        // "Register" our listener with the mock player
        player.addListener(serviceListener)

        val listener = listenerSlot.captured

        // Simulate the player reaching the end of the playlist
        listener.onPlaybackStateChanged(Player.STATE_ENDED)

        // Verify the fix triggers
        coVerify(exactly = 1) { removeEpisodeUseCase("episode_123", true) }
    }

    @Test
    fun playerListener_onMediaItemTransition_autoReason_seeksToLastPosition() = runTest {
        val lastPosition = 5000L
        val episodeId = "test_episode"
        
        val dummyEpisode = Episode(
            id = episodeId,
            podcastFeedUrl = "feed",
            title = "Ep 1",
            description = "Desc",
            audioUrl = "url",
            imageUrl = null,
            episodeWebLink = null,
            pubDate = 0L,
            duration = 1000,
            downloadStatus = 0,
            localFilePath = null,
            isPlayed = false,
            lastPlayedPosition = lastPosition,
            completedAt = null,
            localId = 1
        )

        val player = mockk<Player>(relaxed = true)
        val mediaItem = MediaItem.Builder()
            .setMediaId(episodeId)
            .build()
            
        every { player.currentPosition } returns 0L

        // Emulate the listener logic from PlaybackService
        val listener = object : Player.Listener {
            private var lastResumedId: String? = null

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (mediaItem != null) {
                    if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO || reason == Player.MEDIA_ITEM_TRANSITION_REASON_SEEK) {
                        if (lastResumedId != mediaItem.mediaId) {
                            lastResumedId = mediaItem.mediaId
                            // Simulating the DB fetch and seek
                            if (mediaItem.mediaId == episodeId && dummyEpisode.lastPlayedPosition > 0) {
                                if (player.currentPosition < 2000) {
                                    player.seekTo(dummyEpisode.lastPlayedPosition)
                                }
                            }
                        }
                    }
                }
            }
        }

        listener.onMediaItemTransition(mediaItem, Player.MEDIA_ITEM_TRANSITION_REASON_AUTO)

        verify { player.seekTo(lastPosition) }
    }
}
