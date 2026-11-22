package com.yuval.podcasts.work

import androidx.work.ExistingWorkPolicy
import androidx.work.WorkManager
import com.yuval.podcasts.data.Constants
import com.yuval.podcasts.data.db.entity.DownloadStatus
import com.yuval.podcasts.data.db.entity.Episode
import java.io.File

/**
 * Enqueues a unique background download for [episode] unless it is local or already downloaded
 * with its file present on disk. Performs file existence I/O; call on an IO dispatcher.
 */
internal fun WorkManager.enqueueEpisodeDownloadIfNeeded(
    episode: Episode,
    isExpedited: Boolean
) {
    if (episode.isLocal) return
    val fileExists = episode.localFilePath?.let { File(it).exists() } == true
    if (episode.downloadStatus == DownloadStatus.DOWNLOADED.value && fileExists) return

    val request = DownloadWorker.createWorkRequest(
        episodeId = episode.id,
        audioUrl = episode.audioUrl,
        title = episode.title,
        isExpedited = isExpedited
    )
    enqueueUniqueWork(
        "${Constants.WORK_TAG_DOWNLOAD_PREFIX}${episode.id}",
        ExistingWorkPolicy.KEEP,
        request
    )
}
