package com.yuval.podcasts.domain.usecase

import androidx.work.WorkManager
import com.yuval.podcasts.data.db.dao.EpisodeDao
import com.yuval.podcasts.data.db.dao.QueueDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/**
 * Encapsulates the business logic required to fully remove an episode from the listening queue.
 * This ensures that local files are deleted and database statuses are updated consistently,
 * regardless of whether the removal was triggered by a user swipe or by the player finishing the track.
 */
class RemoveEpisodeUseCase @Inject constructor(
    private val episodeDao: EpisodeDao,
    private val queueDao: QueueDao,
    private val workManager: WorkManager
) {
    suspend operator fun invoke(episodeId: String, markAsPlayed: Boolean = false) = withContext(Dispatchers.IO) {
        if (markAsPlayed) {
            episodeDao.updatePlaybackStatus(episodeId, true)
            episodeDao.updateLastPlayedPosition(episodeId, 0L)
        }

        // Cancel any pending or active download for this episode
        workManager.cancelUniqueWork("download_$episodeId")

        // Delete the physical audio file to reclaim storage
        val episode = episodeDao.getEpisodeById(episodeId)
        episode?.localFilePath?.let { path ->
            val file = File(path)
            if (file.exists()) {
                file.delete()
            }
        }
        
        // Reset the download status in the DB
        episodeDao.updateDownloadStatus(episodeId, 0, null)
        
        // Finally, remove the item from the queue
        queueDao.removeFromQueue(episodeId)
    }
}
