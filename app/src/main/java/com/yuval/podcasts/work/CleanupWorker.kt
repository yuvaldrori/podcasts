package com.yuval.podcasts.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.yuval.podcasts.data.db.dao.QueueDao
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File

@HiltWorker
class CleanupWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val queueDao: QueueDao
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val downloadsDir = File(appContext.filesDir, "podcasts")
            if (!downloadsDir.exists() || !downloadsDir.isDirectory) {
                return@withContext Result.success()
            }

            // Get all files in the directory
            val files = downloadsDir.listFiles() ?: return@withContext Result.success()
            
            // Get all active episode IDs currently in the queue
            val queuedEpisodes = queueDao.getQueueEpisodes().first()
            val validHashes = queuedEpisodes.map { "episode_${it.id.hashCode()}.mp3" }.toSet()

            var deletedCount = 0
            for (file in files) {
                if (!validHashes.contains(file.name)) {
                    val deleted = file.delete()
                    if (deleted) deletedCount++
                }
            }

            android.util.Log.d("CleanupWorker", "Cleaned up $deletedCount orphaned podcast files.")
            Result.success()
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            android.util.Log.e("CleanupWorker", "Cleanup failed: ${e.message}", e)
            Result.failure()
        }
    }
}
