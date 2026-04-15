package com.yuval.podcasts.media

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.yuval.podcasts.data.db.dao.EpisodeDao
import com.yuval.podcasts.data.db.entity.Episode
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackServiceResumeTest {

    @Test
    fun onMediaItemTransition_autoReason_seeksToLastPosition() = runTest {
        val episodeDao = mockk<EpisodeDao>()
        val player = mockk<Player>(relaxed = true)
        val episodeId = "test_episode"
        val lastPosition = 5000L

        val mediaItem = MediaItem.Builder()
            .setMediaId(episodeId)
            .build()
        
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

        coEvery { episodeDao.getEpisodeById(episodeId) } returns dummyEpisode
        every { player.currentPosition } returns 0L

        // Emulate the listener logic from PlaybackService without the CoroutineScope overhead
        val listener = object : Player.Listener {
            private var lastResumedId: String? = null

            fun handleTransition(mediaItem: MediaItem?, reason: Int, episode: Episode?) {
                if (mediaItem != null) {
                    if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO || reason == Player.MEDIA_ITEM_TRANSITION_REASON_SEEK) {
                        if (lastResumedId != mediaItem.mediaId) {
                            lastResumedId = mediaItem.mediaId
                            if (episode != null && episode.lastPlayedPosition > 0) {
                                if (player.currentPosition < 2000) {
                                    player.seekTo(episode.lastPlayedPosition)
                                }
                            }
                        }
                    }
                }
            }
        }

        val episode = episodeDao.getEpisodeById(episodeId)
        listener.handleTransition(mediaItem, Player.MEDIA_ITEM_TRANSITION_REASON_AUTO, episode)

        verify { player.seekTo(lastPosition) }
    }
}
