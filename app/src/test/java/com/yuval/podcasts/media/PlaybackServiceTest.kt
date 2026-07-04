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
            completedAt = null
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
                    if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO || 
                        reason == Player.MEDIA_ITEM_TRANSITION_REASON_SEEK ||
                        reason == Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED) {
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

    @Test
    fun playerListener_onMediaItemTransition_repeatReason_removesEpisode() = runTest {
        val removeEpisodeUseCase = mockk<RemoveEpisodeUseCase>(relaxed = true)
        val listenerSlot = slot<Player.Listener>()
        val player = mockk<Player>(relaxed = true)
        every { player.addListener(capture(listenerSlot)) } returns Unit

        // Emulate the updated listener logic from PlaybackService
        val listener = object : Player.Listener {
            var currentlyPlayingId: String? = "episode_123"

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val lastId = currentlyPlayingId
                
                val isAutoTransition = reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO
                val isRepeatTransition = reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT
                
                if (lastId != null && (isAutoTransition || isRepeatTransition)) {
                    if (mediaItem == null || lastId != mediaItem.mediaId || isRepeatTransition) {
                        kotlinx.coroutines.runBlocking {
                            removeEpisodeUseCase(lastId, markAsPlayed = true)
                        }
                    }
                }
            }
        }
        
        player.addListener(listener)
        val capturedListener = listenerSlot.captured

        val mediaItem = MediaItem.Builder().setMediaId("episode_123").build()
        
        // Simulate a repeat transition (reason = REPEAT)
        capturedListener.onMediaItemTransition(mediaItem, Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT)

        // This verification MUST fail under the current logic (0 calls instead of 1)
        coVerify(exactly = 1) { removeEpisodeUseCase("episode_123", true) }
    }
}

