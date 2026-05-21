package com.yuval.podcasts.domain.usecase

import com.yuval.podcasts.data.db.entity.Episode
import com.yuval.podcasts.data.db.entity.EpisodeWithPodcast
import com.yuval.podcasts.data.db.entity.Podcast
import com.yuval.podcasts.data.db.entity.QueueState
import com.yuval.podcasts.data.repository.PodcastRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ReorderSubscriptionInQueueUseCaseTest {

    private val repository = mockk<PodcastRepository>(relaxed = true)
    private val useCase = ReorderSubscriptionInQueueUseCase(repository)

    @Test
    fun `invoke moves specific subscription episodes to bottom in chronological order`() = runTest {
        // Arrange
        val feedUrl = "https://example.com/rss"
        val otherUrl = "https://other.com/rss"
        
        val p1 = createPodcast(feedUrl)
        val p2 = createPodcast(otherUrl)

        val ep1 = createEpisode("ep1", feedUrl, 1000L) // Subscription, older
        val ep2 = createEpisode("ep2", otherUrl, 2000L) // Other
        val ep3 = createEpisode("ep3", feedUrl, 3000L) // Subscription, newer
        val ep4 = createEpisode("ep4", otherUrl, 4000L) // Other

        val currentQueue = listOf(
            EpisodeWithPodcast(ep1, p1),
            EpisodeWithPodcast(ep2, p2),
            EpisodeWithPodcast(ep3, p1),
            EpisodeWithPodcast(ep4, p2)
        )

        coEvery { repository.listeningQueue } returns flowOf(currentQueue)

        // Act
        useCase(feedUrl)

        // Assert
        // Expected order: ep2 (Other), ep4 (Other), ep1 (Sub, old), ep3 (Sub, new)
        val expectedStates = listOf(
            QueueState("ep2", 0),
            QueueState("ep4", 1),
            QueueState("ep1", 2),
            QueueState("ep3", 3)
        )
        coVerify { repository.updateQueue(expectedStates) }
    }

    private fun createPodcast(feedUrl: String) = Podcast(
        feedUrl = feedUrl,
        title = "Title",
        description = "Desc",
        imageUrl = "url",
        website = "website"
    )

    private fun createEpisode(id: String, feedUrl: String, pubDate: Long) = Episode(
        id = id,
        podcastFeedUrl = feedUrl,
        title = "Ep Title",
        description = "Ep Desc",
        audioUrl = "url",
        pubDate = pubDate,
        duration = 1000L,
        downloadStatus = 0,
        localFilePath = null
    )
}
