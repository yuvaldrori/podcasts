package com.yuval.podcasts.domain.usecase

import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.yuval.podcasts.data.db.entity.Episode
import com.yuval.podcasts.data.db.entity.QueueState
import com.yuval.podcasts.data.repository.PodcastRepository
import com.yuval.podcasts.work.DownloadWorker
import javax.inject.Inject

import kotlinx.coroutines.flow.first

class EnqueueEpisodeUseCase @Inject constructor(
    private val repository: PodcastRepository,
    private val workManager: WorkManager
) {
    suspend operator fun invoke(episode: Episode) {
        // Consolidated DB operation (Queue update + Playback status update)
        repository.requeueEpisode(episode)

        // Do not schedule background download if episode is local or already downloaded and physical file exists
        val fileExists = episode.localFilePath?.let { java.io.File(it).exists() } == true
        if (episode.isLocal || (episode.downloadStatus == 2 && fileExists)) {
            return
        }

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
            "${com.yuval.podcasts.data.Constants.WORK_TAG_DOWNLOAD_PREFIX}${episode.id}",
            ExistingWorkPolicy.KEEP,
            downloadWorkRequest
        )    }
}

