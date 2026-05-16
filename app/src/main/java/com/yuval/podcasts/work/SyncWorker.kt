package com.yuval.podcasts.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.yuval.podcasts.data.repository.PodcastRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

import androidx.work.workDataOf
import com.yuval.podcasts.data.Constants
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.util.concurrent.atomic.AtomicInteger

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val repository: com.yuval.podcasts.data.repository.PodcastRepository,
    private val logManager: com.yuval.podcasts.utils.LogManager
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        logManager.i("SyncWorker", "Sync starting")
        return try {
            val podcasts = repository.allPodcasts.first()
            val total = podcasts.size
            if (total == 0) return Result.success()
            
            val completedCount = AtomicInteger(0)
            val semaphore = Semaphore(Constants.MAX_PARALLEL_REFRESHES)

            setProgress(workDataOf(Constants.WORK_KEY_PROGRESS to 0, Constants.WORK_KEY_TOTAL to total))
            
            coroutineScope {
                podcasts.map { podcast ->
                    async {
                        semaphore.withPermit {
                            try {
                                repository.fetchAndStorePodcast(podcast.feedUrl)
                            } catch (e: Exception) {
                                if (e is kotlinx.coroutines.CancellationException) throw e
                                logManager.e("SyncWorker", "Failed to refresh podcast: ${podcast.feedUrl}", mapOf("error" to (e.message ?: "")))
                            } finally {
                                val current = completedCount.incrementAndGet()
                                setProgress(workDataOf(Constants.WORK_KEY_PROGRESS to current, Constants.WORK_KEY_TOTAL to total))
                            }
                        }
                    }
                }.awaitAll()
            }

            repository.requeueMissingDownloads()
            
            logManager.i("SyncWorker", "Sync completed successfully")
            Result.success()
        } catch (e: Exception) {
            logManager.e("SyncWorker", "Sync failed", mapOf("error" to (e.message ?: "unknown"), "stack" to android.util.Log.getStackTraceString(e)))
            if (runAttemptCount < 3) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }
}
