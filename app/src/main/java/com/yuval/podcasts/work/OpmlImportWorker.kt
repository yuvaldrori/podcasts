package com.yuval.podcasts.work

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.net.Uri
import androidx.core.net.toUri
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.yuval.podcasts.R
import com.yuval.podcasts.data.Constants
import com.yuval.podcasts.data.opml.OpmlManager
import com.yuval.podcasts.data.repository.PodcastRepository
import com.yuval.podcasts.di.IoDispatcher
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

import androidx.work.workDataOf
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.atomic.AtomicInteger

@HiltWorker
class OpmlImportWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val opmlManager: OpmlManager,
    private val repository: PodcastRepository,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(ioDispatcher) {
        val uriString = inputData.getString(KEY_URI) ?: return@withContext Result.failure()
        val uri = uriString.toUri()

        try {
            val urls = appContext.contentResolver.openInputStream(uri)?.use { stream ->
                opmlManager.parse(stream)
            } ?: return@withContext Result.failure()

            val total = urls.size
            if (total == 0) return@withContext Result.success()

            val completedCount = AtomicInteger(0)
            val semaphore = Semaphore(Constants.MAX_PARALLEL_REFRESHES)

            setProgress(workDataOf(Constants.WORK_KEY_PROGRESS to 0, Constants.WORK_KEY_TOTAL to total))
            setForeground(createForegroundInfo(0, total))
            
            coroutineScope {
                urls.map { url ->
                    async {
                        semaphore.withPermit {
                            try {
                                repository.fetchAndStorePodcast(url)
                            } catch (e: Exception) {
                                if (e is kotlinx.coroutines.CancellationException) throw e
                                android.util.Log.e("OpmlImportWorker", "Failed to import podcast: $url", e)
                            } finally {
                                val current = completedCount.incrementAndGet()
                                setProgress(workDataOf(Constants.WORK_KEY_PROGRESS to current, Constants.WORK_KEY_TOTAL to total))
                                setForeground(createForegroundInfo(current, total))
                            }
                        }
                    }
                }.awaitAll()
            }

            Result.success()
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            android.util.Log.e("OpmlImportWorker", "Import failed: ${e.message}", e)
            Result.failure()
        }
    }

    private fun createForegroundInfo(progress: Int, total: Int): ForegroundInfo {
        val notificationId = Constants.NOTIFICATION_ID_IMPORT
        val channelId = "import_channel"
        
        val channel = NotificationChannel(
            channelId,
            appContext.getString(R.string.notification_channel_imports),
            NotificationManager.IMPORTANCE_LOW
        )
        val notificationManager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)

        val notification = Notification.Builder(appContext, channelId)
            .setContentTitle(appContext.getString(R.string.notification_importing_title))
            .setContentText(appContext.getString(R.string.notification_importing_progress, progress, total))
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(total, progress, total == 0)
            .setOngoing(true)
            .build()

        return ForegroundInfo(notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    }

    companion object {
        const val KEY_URI = "uri"
    }
}
