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

class EnqueueEpisodeUseCase @Inject constructor(
    private val queueDao: QueueDao,
    private val episodeDao: EpisodeDao,
    private val workManager: WorkManager
) {
    suspend operator fun invoke(episode: Episode) {
        // Shift all existing items down by 1 in the database directly
        queueDao.shiftQueuePositionsUp()
        
        // Add new item at position 0
        queueDao.insertQueueItem(QueueState(episode.id, 0))
        
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
            .build()

        workManager.enqueueUniqueWork(
            "download_${episode.id}",
            ExistingWorkPolicy.KEEP,
            downloadWorkRequest
        )
    }
}
