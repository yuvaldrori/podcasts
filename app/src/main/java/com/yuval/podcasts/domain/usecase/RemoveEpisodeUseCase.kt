package com.yuval.podcasts.domain.usecase

import androidx.work.WorkManager
import com.yuval.podcasts.data.repository.PodcastRepository
import com.yuval.podcasts.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/**
 * Encapsulates the business logic required to fully remove an episode from the listening queue.
 * This ensures that local files are deleted and database statuses are updated consistently,
 * regardless of whether the removal was triggered by a user swipe or by the player finishing the track.
 */
class RemoveEpisodeUseCase @Inject constructor(
    private val repository: PodcastRepository,
    private val workManager: WorkManager,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {
    suspend operator fun invoke(episodeId: String, markAsPlayed: Boolean = false) = withContext(ioDispatcher) {
        if (markAsPlayed) {
            repository.updatePlaybackStatus(episodeId, true, System.currentTimeMillis())
            repository.updateLastPlayedPosition(episodeId, 0L)
        }

        // Cancel any pending or active download for this episode
        workManager.cancelUniqueWork("${com.yuval.podcasts.data.Constants.WORK_TAG_DOWNLOAD_PREFIX}$episodeId")

        // Delete the physical audio file to reclaim storage
        // We'll use getEpisodeWithPodcastFlow or similar if we need more info, 
        // but for now let's add getEpisodeById to repository if missing.
        val episode = repository.getEpisodeByIdFlow(episodeId).first()
        episode?.localFilePath?.let { path ->
            val file = File(path)
            if (file.exists()) {
                file.delete()
            }
        }
        
        // Reset the download status in the DB
        repository.updateDownloadStatus(episodeId, 0, null)
        
        // Finally, remove the item from the queue
        repository.removeFromQueue(episodeId)
    }
}
