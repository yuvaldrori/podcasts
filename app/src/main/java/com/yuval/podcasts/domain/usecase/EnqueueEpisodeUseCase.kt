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
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class EnqueueEpisodeUseCase @Inject constructor(
    private val queueDao: QueueDao,
    private val episodeDao: EpisodeDao,
    private val workManager: WorkManager
) {
    suspend operator fun invoke(episode: Episode) {
        val currentQueue = queueDao.getQueue().first()
        
        // Shift all existing items down by 1
        val updatedQueue = currentQueue.map { it.copy(position = it.position + 1) }.toMutableList()
        // Add new item at position 0
        updatedQueue.add(QueueState(episode.id, 0))
        
        queueDao.updateQueue(updatedQueue)
        
        // Dismiss from "New" tab
        episodeDao.updatePlaybackStatus(episode.id, true)

        // Trigger background download
        val downloadData = Data.Builder()
            .putString(DownloadWorker.KEY_EPISODE_ID, episode.id)
            .putString(DownloadWorker.KEY_AUDIO_URL, episode.audioUrl)
            .build()

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val downloadWorkRequest = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setConstraints(constraints)
            .setInputData(downloadData)
            .build()

        workManager.enqueueUniqueWork(
            "download_${episode.id}",
            ExistingWorkPolicy.KEEP,
            downloadWorkRequest
        )
    }
}
