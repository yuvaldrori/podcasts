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
import java.util.concurrent.atomic.AtomicLong

import androidx.work.ForegroundInfo
import com.yuval.podcasts.R

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val repository: com.yuval.podcasts.data.repository.PodcastRepository,
    private val logManager: com.yuval.podcasts.utils.LogManager
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun getForegroundInfo(): ForegroundInfo {
        return createForegroundInfo(0, 0)
    }

    override suspend fun doWork(): Result {
        logManager.i("SyncWorker", "Sync starting")
        return try {
            val podcasts = repository.allPodcasts.first()
            val total = podcasts.size
            if (total == 0) return Result.success()
            
            var isForegroundAllowed = true
            try {
                setForeground(createForegroundInfo(0, total))
            } catch (e: Exception) {
                isForegroundAllowed = false
                logManager.w("SyncWorker", "Failed to set initial foreground status", mapOf("error" to e.message.toString()))
            }
            try {
                setProgress(workDataOf(Constants.WORK_KEY_PROGRESS to 0, Constants.WORK_KEY_TOTAL to total))
            } catch (e: Exception) {
                logManager.w("SyncWorker", "Failed to set initial progress", mapOf("error" to e.message.toString()))
            }
            
            val lastForegroundUpdate = AtomicLong(0L)

            repository.refreshPodcasts(podcasts.map { it.feedUrl }) { current, totalCount ->
                val currentTime = System.currentTimeMillis()
                try {
                    setProgress(workDataOf(Constants.WORK_KEY_PROGRESS to current, Constants.WORK_KEY_TOTAL to totalCount))
                    val lastUpdate = lastForegroundUpdate.get()
                    if (isForegroundAllowed && (current == totalCount || currentTime - lastUpdate > Constants.SYNC_PROGRESS_NOTIFICATION_THROTTLE_MS)) {
                        lastForegroundUpdate.set(currentTime)
                        try {
                            setForeground(createForegroundInfo(current, totalCount))
                        } catch (e: Exception) {
                            isForegroundAllowed = false
                        }
                    }
                } catch (e: Exception) {
                    // Ignore failures in setProgress during the loop
                }
            }

            repository.requeueMissingDownloads()
            
            logManager.i("SyncWorker", "Sync completed successfully")
            Result.success()
        } catch (e: Exception) {
            logManager.e("SyncWorker", "Sync failed", mapOf("error" to (e.javaClass.simpleName + ": " + e.message)))
            if (runAttemptCount < Constants.SYNC_RETRY_COUNT) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }

    private fun createForegroundInfo(progress: Int, total: Int): ForegroundInfo {
        return com.yuval.podcasts.utils.WorkerNotificationHelper.createForegroundInfo(
            context = appContext,
            notificationId = Constants.NOTIFICATION_ID_SYNC,
            channelId = Constants.NOTIFICATION_CHANNEL_ID_SYNC,
            channelName = appContext.getString(R.string.notification_channel_sync),
            title = appContext.getString(R.string.notification_syncing_title),
            contentText = if (total > 0) appContext.getString(R.string.notification_syncing_progress, progress, total) else "",
            progress = progress,
            maxProgress = total
        )
    }
}
