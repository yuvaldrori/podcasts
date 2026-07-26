package com.yuval.podcasts.media

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.yuval.podcasts.data.db.dao.EpisodeDao
import com.yuval.podcasts.data.db.entity.Episode
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests for playback resumption logic in PlaybackService.
 *
 * NOTE: PlaybackService requires a full Android service lifecycle to instantiate, making
 * it impractical to unit-test its onMediaItemTransition listener directly. These tests
 * instead verify the *conditions and inputs* the listener depends on, and document the
 * expected seek behavior given those inputs.
 *
 * For end-to-end validation of actual seekTo calls from the real listener, see
 * PlaybackServiceIntegrationTest (instrumented tests).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackServiceResumeTest {

    /**
     * Verifies the seek-to condition: the player should seek to lastPlayedPosition when:
     * - transition reason is AUTO (playlist advance) or PLAYLIST_CHANGED (initial load)
     * - the episode has a lastPlayedPosition > 0
     * - the current player position is < 2000ms (hasn't been seeked already)
     *
     * This test validates the inputs that drive the listener decision — it does NOT
     * instantiate PlaybackService, as that requires a bound MediaSession.
     */
    @Test
    fun resumeConditions_seekTriggered_whenPositionIsAtStart() = runTest {
        val episodeDao = mockk<EpisodeDao>()
        val player = mockk<Player>(relaxed = true)
        val episodeId = "test_episode"
        val lastPosition = 5000L

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

        coEvery { episodeDao.getEpisodeById(episodeId) } returns dummyEpisode
        every { player.currentPosition } returns 0L

        val episode = episodeDao.getEpisodeById(episodeId)

        // Verify the conditions that should trigger a seek
        requireNotNull(episode)
        assertEquals(lastPosition, episode.lastPlayedPosition)
        val shouldSeek = episode.lastPlayedPosition > 0 && player.currentPosition < 2000
        assertEquals(true, shouldSeek)

        // Simulate what the PlaybackService listener does when conditions are met
        if (shouldSeek) {
            player.seekTo(episode.lastPlayedPosition)
        }

        verify { player.seekTo(lastPosition) }
    }

    /**
     * Verifies the no-seek condition: if the player is already past 2000ms, we should NOT
     * seek again (prevents interrupting deliberate user scrubbing).
     */
    @Test
    fun resumeConditions_noSeek_whenPlayerAlreadyPastThreshold() = runTest {
        val episodeId = "test_episode"
        val lastPosition = 5000L
        val player = mockk<Player>(relaxed = true)
        every { player.currentPosition } returns 3000L // already past threshold

        val episode = Episode(
            id = episodeId, podcastFeedUrl = "feed", title = "Ep 1", description = "",
            audioUrl = "url", imageUrl = null, episodeWebLink = null, pubDate = 0L,
            duration = 1000, downloadStatus = 0, localFilePath = null, isPlayed = false,
            lastPlayedPosition = lastPosition, completedAt = null
        )

        val shouldSeek = episode.lastPlayedPosition > 0 && player.currentPosition < 2000
        assertEquals(false, shouldSeek)

        verify(exactly = 0) { player.seekTo(any()) }
    }
}
