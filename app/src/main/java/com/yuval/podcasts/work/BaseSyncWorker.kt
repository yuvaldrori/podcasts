package com.yuval.podcasts.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.yuval.podcasts.R
import com.yuval.podcasts.data.Constants
import com.yuval.podcasts.data.repository.PodcastRepository
import com.yuval.podcasts.utils.LogManager
import com.yuval.podcasts.utils.WorkerNotificationHelper
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

abstract class BaseSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters,
    protected val repository: PodcastRepository,
    protected val logManager: LogManager,
    protected val forceRefresh: Boolean,
    private val notificationId: Int,
    private val workerTag: String
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun getForegroundInfo(): ForegroundInfo {
        return createForegroundInfo(0, 0)
    }

    override suspend fun doWork(): Result {
        logManager.i(workerTag, "Sync starting (forceRefresh=$forceRefresh)")
        return try {
            val podcasts = repository.allPodcasts.first()
            val total = podcasts.size
            if (total == 0) return Result.success()
            
            var isForegroundAllowed = true
            try {
                setForeground(createForegroundInfo(0, total))
            } catch (e: Exception) {
                isForegroundAllowed = false
                logManager.w(workerTag, "Failed to set initial foreground status", mapOf("error" to e.message.toString()))
            }
            try {
                setProgress(workDataOf(Constants.WORK_KEY_PROGRESS to 0, Constants.WORK_KEY_TOTAL to total))
            } catch (e: Exception) {
                logManager.w(workerTag, "Failed to set initial progress", mapOf("error" to e.message.toString()))
            }
            
            var lastForegroundUpdateMs = 0L
            val progressMutex = Mutex()

            repository.refreshPodcasts(podcasts.map { it.feedUrl }, forceRefresh = forceRefresh) { current, totalCount ->
                progressMutex.withLock {
                    val currentTime = System.currentTimeMillis()
                    try {
                        setProgress(workDataOf(Constants.WORK_KEY_PROGRESS to current, Constants.WORK_KEY_TOTAL to totalCount))
                        if (isForegroundAllowed && (current == totalCount || currentTime - lastForegroundUpdateMs > Constants.SYNC_PROGRESS_NOTIFICATION_THROTTLE_MS)) {
                            lastForegroundUpdateMs = currentTime
                            try {
                                setForeground(createForegroundInfo(current, totalCount))
                            } catch (e: Exception) {
                                isForegroundAllowed = false
                            }
                        }
                    } catch (e: Exception) {
                        // Ignore failures in setProgress during loop
                    }
                }
            }

            repository.requeueMissingDownloads()
            
            logManager.i(workerTag, "Sync completed successfully")
            Result.success()
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            logManager.e(workerTag, "Sync failed", mapOf("error" to (e.javaClass.simpleName + ": " + e.message)))
            if (runAttemptCount < Constants.SYNC_RETRY_COUNT) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }

    private fun createForegroundInfo(progress: Int, total: Int): ForegroundInfo {
        return WorkerNotificationHelper.createForegroundInfo(
            context = applicationContext,
            notificationId = notificationId,
            channelId = Constants.NOTIFICATION_CHANNEL_ID_SYNC,
            channelName = applicationContext.getString(R.string.notification_channel_sync),
            title = applicationContext.getString(R.string.notification_syncing_title),
            contentText = if (total > 0) applicationContext.getString(R.string.notification_syncing_progress, progress, total) else "",
            progress = progress,
            maxProgress = total
        )
    }
}
