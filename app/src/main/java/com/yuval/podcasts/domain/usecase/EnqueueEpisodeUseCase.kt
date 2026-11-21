package com.yuval.podcasts.domain.usecase

import androidx.work.ExistingWorkPolicy
import androidx.work.WorkManager
import com.yuval.podcasts.data.Constants
import com.yuval.podcasts.data.db.entity.DownloadStatus
import com.yuval.podcasts.data.db.entity.Episode
import com.yuval.podcasts.data.repository.PodcastRepository
import com.yuval.podcasts.di.IoDispatcher
import com.yuval.podcasts.media.PlayerManager
import com.yuval.podcasts.work.DownloadWorker
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

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
        // Consolidated DB operation (Queue update + Playback status update)
        repository.requeueEpisode(episode, currentlyPlayingEpisodeId)

        // Do not schedule background download if episode is local or already downloaded and physical file exists
        val fileExists = episode.localFilePath?.let { File(it).exists() } == true
        if (episode.isLocal || (episode.downloadStatus == DownloadStatus.DOWNLOADED.value && fileExists)) {
            return@withContext
        }

        // Trigger background download
        val downloadWorkRequest = DownloadWorker.createWorkRequest(
            episodeId = episode.id,
            audioUrl = episode.audioUrl,
            title = episode.title,
            isExpedited = true
        )

        workManager.enqueueUniqueWork(
            "${Constants.WORK_TAG_DOWNLOAD_PREFIX}${episode.id}",
            ExistingWorkPolicy.KEEP,
            downloadWorkRequest
        )
    }
}

