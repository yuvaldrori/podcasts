package com.yuval.podcasts.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.yuval.podcasts.data.db.dao.QueueDao
import com.yuval.podcasts.data.db.dao.EpisodeDao
import com.yuval.podcasts.di.IoDispatcher
import com.yuval.podcasts.utils.StorageUtils
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

@HiltWorker
class CleanupWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val queueDao: QueueDao,
    private val episodeDao: EpisodeDao,
    private val logManager: com.yuval.podcasts.utils.LogManager,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(ioDispatcher) {
        try {
            logManager.i("CleanupWorker", "Cleanup starting")
            val downloadsDir = StorageUtils.getDownloadsDir(appContext)
            
            // Get all files in the directory
            val files = downloadsDir.listFiles() ?: return@withContext Result.success()
            
            // Get all active episode IDs currently in the queue or downloaded/downloading
            val queuedEpisodes = queueDao.getQueueEpisodesSync()
            val downloadedOrDownloadingEpisodes = episodeDao.getDownloadedOrDownloadingEpisodes()
            
            val validEpisodeIds = (queuedEpisodes.map { it.id } + downloadedOrDownloadingEpisodes.map { it.id }).toSet()
            val validNames = validEpisodeIds.map { StorageUtils.getFileName(it) }.toSet()

            var deletedCount = 0
            for (file in files) {
                val cleanName = if (file.name.endsWith(".part")) {
                    file.name.removeSuffix(".part")
                } else {
                    file.name
                }
                if (!validNames.contains(cleanName)) {
                    val deleted = file.delete()
                    if (deleted) deletedCount++
                }
            }

            logManager.i("CleanupWorker", "Cleaned up $deletedCount orphaned podcast files.")
            Result.success()
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            logManager.e("CleanupWorker", "Cleanup failed", mapOf("error" to (e.message ?: "unknown")))
            Result.failure()
        }
    }
}
