package com.yuval.podcasts.domain.usecase

import com.yuval.podcasts.data.Constants
import com.yuval.podcasts.data.repository.PodcastRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Synchronously refreshes all subscribed podcasts and returns the total number of new episodes found.
 * This is intended for use by AppFunctions where a direct result is needed.
 */
class RefreshAllPodcastsSyncUseCase @Inject constructor(
    private val repository: PodcastRepository
) {
    suspend operator fun invoke(): Int {
        val podcasts = repository.allPodcasts.first().filter { it.feedUrl != Constants.LOCAL_PODCAST_FEED_URL }
        if (podcasts.isEmpty()) return 0
        return repository.refreshPodcasts(podcasts.map { it.feedUrl }) { _, _ -> }
    }
}
