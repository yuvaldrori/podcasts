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
import java.util.concurrent.atomic.AtomicLong

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.pm.ServiceInfo
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
            
            try {
                setForeground(createForegroundInfo(0, total))
                setProgress(workDataOf(Constants.WORK_KEY_PROGRESS to 0, Constants.WORK_KEY_TOTAL to total))
            } catch (e: Exception) {
                // In some test environments, setForeground may fail. 
                // We log it but continue the sync as it's not critical for the task itself.
                logManager.w("SyncWorker", "Failed to set foreground/progress", mapOf("error" to e.message.toString()))
            }
            
            val completedCount = AtomicInteger(0)
            val semaphore = Semaphore(Constants.MAX_PARALLEL_REFRESHES)
            val lastForegroundUpdate = AtomicLong(0L)

            coroutineScope {
                podcasts.map { podcast ->
                    async {
                        semaphore.withPermit {
                            try {
                                repository.fetchAndStorePodcast(podcast.feedUrl)
                            } catch (e: Exception) {
                                if (e is kotlinx.coroutines.CancellationException) throw e
                                logManager.e("SyncWorker", "Failed to refresh podcast: ${podcast.feedUrl}", mapOf("error" to (e.javaClass.simpleName + ": " + e.message)))
                            } finally {
                                val current = completedCount.incrementAndGet()
                                val currentTime = System.currentTimeMillis()
                                try {
                                    setProgress(workDataOf(Constants.WORK_KEY_PROGRESS to current, Constants.WORK_KEY_TOTAL to total))
                                    val lastUpdate = lastForegroundUpdate.get()
                                    if (current == total || currentTime - lastUpdate > 1000) {
                                        lastForegroundUpdate.set(currentTime)
                                        setForeground(createForegroundInfo(current, total))
                                    }
                                } catch (e: Exception) {
                                    // Ignore failures in setForeground/setProgress during the loop
                                }
                            }
                        }
                    }
                }.awaitAll()
            }

            repository.requeueMissingDownloads()
            
            logManager.i("SyncWorker", "Sync completed successfully")
            Result.success()
        } catch (e: Exception) {
            logManager.e("SyncWorker", "Sync failed", mapOf("error" to (e.javaClass.simpleName + ": " + e.message)))
            if (runAttemptCount < 3) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }

    private fun createForegroundInfo(progress: Int, total: Int): ForegroundInfo {
        val notificationId = Constants.NOTIFICATION_ID_SYNC
        val channelId = "sync_channel"
        
        val channel = NotificationChannel(
            channelId,
            appContext.getString(R.string.notification_channel_sync),
            NotificationManager.IMPORTANCE_LOW
        )
        val notificationManager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)

        val notification = Notification.Builder(appContext, channelId)
            .setContentTitle(appContext.getString(R.string.notification_syncing_title))
            .setContentText(if (total > 0) appContext.getString(R.string.notification_syncing_progress, progress, total) else "")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(total, progress, total == 0)
            .setOngoing(true)
            .build()

        return ForegroundInfo(notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    }
}
