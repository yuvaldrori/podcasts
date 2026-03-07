package com.yuval.podcasts.domain.usecase

import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.yuval.podcasts.data.db.dao.EpisodeDao
import com.yuval.podcasts.data.db.dao.QueueDao
import com.yuval.podcasts.data.db.entity.Episode
import com.yuval.podcasts.data.db.entity.QueueState
import com.yuval.podcasts.work.DownloadWorker
import javax.inject.Inject

import kotlinx.coroutines.flow.first

class EnqueueEpisodeUseCase @Inject constructor(
    private val queueDao: QueueDao,
    private val episodeDao: EpisodeDao,
    private val workManager: WorkManager
) {
    suspend operator fun invoke(episode: Episode) {
        // Fetch current queue
        val currentQueue = queueDao.getQueueEpisodes().first()

        // Find the correct insertion index
        // We want older episodes to come before newer ones.
        // So we scan from top to bottom (index 0 onwards) and find the FIRST episode
        // that is NEWER than the one we are inserting. We insert right before it.
        // If no episode is newer (i.e. all existing items are older), it goes to the end.
        val insertionIndex = currentQueue.indexOfFirst { it.pubDate > episode.pubDate }.let {
            if (it == -1) currentQueue.size else it
        }

        // Build the new ordered list in memory
        val updatedList = currentQueue.toMutableList()
        updatedList.add(insertionIndex, episode)

        // Map back to QueueState and update the database in bulk
        val newQueueStates = updatedList.mapIndexed { index, ep ->
            QueueState(ep.id, index)
        }
        queueDao.updateQueue(newQueueStates)
        
        // Dismiss from "New" tab
        episodeDao.updatePlaybackStatus(episode.id, true)

        // Trigger background download
        val downloadData = Data.Builder()
            .putString(DownloadWorker.KEY_EPISODE_ID, episode.id)
            .putString(DownloadWorker.KEY_AUDIO_URL, episode.audioUrl)
            .putString(DownloadWorker.KEY_EPISODE_TITLE, episode.title)
            .build()

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val downloadWorkRequest = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setConstraints(constraints)
            .setInputData(downloadData)
            .setExpedited(androidx.work.OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()

        workManager.enqueueUniqueWork(
            "download_${episode.id}",
            ExistingWorkPolicy.KEEP,
            downloadWorkRequest
        )
    }
}
