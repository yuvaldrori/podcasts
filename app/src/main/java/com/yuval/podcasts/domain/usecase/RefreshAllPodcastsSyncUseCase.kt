package com.yuval.podcasts.domain.usecase

import com.yuval.podcasts.data.repository.PodcastRepository
import com.yuval.podcasts.utils.LogManager
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Synchronously refreshes all subscribed podcasts and returns the total number of new episodes found.
 * This is intended for use by AppFunctions where a direct result is needed.
 */
class RefreshAllPodcastsSyncUseCase @Inject constructor(
    private val repository: PodcastRepository,
    private val logManager: LogManager
) {
    suspend operator fun invoke(): Int = coroutineScope {
        val podcasts = repository.allPodcasts.first()
        if (podcasts.isEmpty()) return@coroutineScope 0

        val deferreds = podcasts.map { podcast ->
            async {
                try {
                    repository.fetchAndStorePodcast(podcast.feedUrl)
                } catch (e: Exception) {
                    logManager.e("RefreshAllSync", "Failed to refresh ${podcast.feedUrl}", mapOf("error" to e.message.toString()))
                    0
                }
            }
        }
        deferreds.awaitAll().sum()
    }
}
