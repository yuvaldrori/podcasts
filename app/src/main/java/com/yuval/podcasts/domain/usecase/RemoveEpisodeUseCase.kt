package com.yuval.podcasts.domain.usecase

import com.yuval.podcasts.data.repository.PodcastRepository
import com.yuval.podcasts.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Encapsulates the business logic required to fully remove an episode from the listening queue.
 * This ensures that local files are deleted and database statuses are updated consistently,
 * regardless of whether the removal was triggered by a user swipe or by the player finishing the track.
 */
class RemoveEpisodeUseCase @Inject constructor(
    private val repository: PodcastRepository,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {
    suspend operator fun invoke(episodeId: String, markAsPlayed: Boolean = false) = withContext(ioDispatcher) {
        if (markAsPlayed) {
            repository.updatePlaybackStatus(episodeId, true, System.currentTimeMillis())
            repository.updateLastPlayedPosition(episodeId, 0L)
        }
        repository.removeFromQueue(episodeId)
    }
}
