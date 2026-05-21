package com.yuval.podcasts.domain.usecase

import com.yuval.podcasts.data.db.entity.QueueState
import com.yuval.podcasts.data.repository.PodcastRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Reorders the queue so that all episodes from a specific podcast are moved to the bottom.
 * Within the moved group, episodes are sorted chronologically (oldest first).
 */
class ReorderSubscriptionInQueueUseCase @Inject constructor(
    private val repository: PodcastRepository
) {
    suspend operator fun invoke(podcastFeedUrl: String) {
        val currentQueue = repository.listeningQueue.first()
        if (currentQueue.isEmpty()) return

        val (subscriptionEpisodes, otherEpisodes) = currentQueue.partition { 
            it.podcast.feedUrl == podcastFeedUrl 
        }

        if (subscriptionEpisodes.isEmpty()) return

        // New order: existing other episodes first, then subscription episodes at the bottom (sorted chronologically)
        val newOrder = otherEpisodes + subscriptionEpisodes.sortedBy { it.episode.pubDate }

        val newQueueStates = newOrder.mapIndexed { index, epWithPodcast ->
            QueueState(epWithPodcast.episode.id, index)
        }

        repository.updateQueue(newQueueStates)
    }
}
