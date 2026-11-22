package com.yuval.podcasts.domain.usecase

import androidx.work.WorkManager
import com.yuval.podcasts.data.db.entity.Episode
import com.yuval.podcasts.data.repository.PodcastRepository
import com.yuval.podcasts.di.IoDispatcher
import com.yuval.podcasts.media.PlayerManager
import com.yuval.podcasts.work.enqueueEpisodeDownloadIfNeeded
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject

class EnqueueEpisodeUseCase @Inject constructor(
    private val repository: PodcastRepository,
    private val playerManager: PlayerManager,
    private val workManager: WorkManager,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {
    suspend operator fun invoke(episode: Episode) = withContext(ioDispatcher) {
        val currentlyPlayingEpisodeId = if (playerManager.isPlaying.value) {
            playerManager.currentMediaId.value
        } else {
            null
        }
        repository.requeueEpisode(episode, currentlyPlayingEpisodeId)
        workManager.enqueueEpisodeDownloadIfNeeded(episode, isExpedited = true)
    }
}
