package com.yuval.podcasts.work

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.net.Uri
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.yuval.podcasts.data.opml.OpmlManager
import com.yuval.podcasts.data.repository.PodcastRepository
import com.yuval.podcasts.di.IoDispatcher
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

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
        val uri = Uri.parse(uriString)

        try {
            setForeground(createForegroundInfo(0, 100))
            
            val urls = appContext.contentResolver.openInputStream(uri)?.use { stream ->
                opmlManager.parse(stream)
            } ?: return@withContext Result.failure()

            val total = urls.size
            if (total == 0) return@withContext Result.success()

            for ((index, url) in urls.withIndex()) {
                // Update Progress explicitly before processing each
                setProgress(workDataOf("PROGRESS" to index, "TOTAL" to total))
                setForeground(createForegroundInfo(index, total))

                try {
                    repository.fetchAndStorePodcast(url)
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    android.util.Log.e("OpmlImportWorker", "Failed to import podcast: $url", e)
                    // If one fails, log it but continue processing the rest of the OPML
                }
            }

            setProgress(workDataOf("PROGRESS" to total, "TOTAL" to total))
            Result.success()
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            android.util.Log.e("OpmlImportWorker", "Import failed: ${e.message}", e)
            Result.failure()
        }
    }

    private fun createForegroundInfo(progress: Int, total: Int): ForegroundInfo {
        val notificationId = 2
        val channelId = "import_channel"
        
        val channel = NotificationChannel(
            channelId,
            "Imports",
            NotificationManager.IMPORTANCE_LOW
        )
        val notificationManager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)

        val notification = Notification.Builder(appContext, channelId)
            .setContentTitle("Importing Podcasts")
            .setContentText("Processed $progress of $total")
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
