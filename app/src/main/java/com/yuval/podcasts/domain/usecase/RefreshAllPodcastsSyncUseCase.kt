package com.yuval.podcasts.domain.usecase

import com.yuval.podcasts.data.Constants
import com.yuval.podcasts.data.repository.PodcastRepository
import com.yuval.podcasts.utils.LogManager
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
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

        val semaphore = Semaphore(Constants.MAX_PARALLEL_REFRESHES)

        val deferreds = podcasts.map { podcast ->
            async {
                semaphore.withPermit {
                    try {
                        repository.fetchAndStorePodcast(podcast.feedUrl)
                    } catch (e: Exception) {
                        if (e is kotlinx.coroutines.CancellationException) throw e
                        logManager.e(TAG, "Failed to refresh ${podcast.feedUrl}", mapOf("error" to "${e.javaClass.simpleName}: ${e.message}"))
                        0
                    }
                }
            }
        }
        deferreds.awaitAll().sum()
    }

    companion object {
        private const val TAG = "RefreshAllSync"
    }
}
