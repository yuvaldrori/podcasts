package com.yuval.podcasts.work

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.yuval.podcasts.data.db.dao.EpisodeDao
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

@HiltWorker
class DownloadWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val episodeDao: EpisodeDao
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val episodeId = inputData.getString(KEY_EPISODE_ID) ?: return@withContext Result.failure()
        val audioUrl = inputData.getString(KEY_AUDIO_URL) ?: return@withContext Result.failure()
        val title = inputData.getString(KEY_EPISODE_TITLE) ?: "Episode"

        try {
            setForeground(createForegroundInfo(title))
            // Update status to Downloading
            updateDownloadStatus(episodeId, 1, null)

            val url = URL(audioUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 30000
            connection.readTimeout = 30000
            connection.connect()

            if (connection.responseCode !in 200..299) {
                updateDownloadStatus(episodeId, 0, null)
                return@withContext Result.failure()
            }

            val fileName = "episode_${episodeId.hashCode()}.mp3"
            val downloadsDir = File(appContext.filesDir, "podcasts").apply { mkdirs() }
            val outputFile = File(downloadsDir, fileName)

            FileOutputStream(outputFile).use { outputStream ->
                connection.inputStream.use { inputStream ->
                    inputStream.copyTo(outputStream, 64 * 1024) // 64KB buffer for large audio files
                }
            }

            // Update status to Downloaded with the local path
            updateDownloadStatus(episodeId, 2, outputFile.absolutePath)
            
            Result.success()
        } catch (e: Exception) {
                        if (e is kotlinx.coroutines.CancellationException) throw e
            e.printStackTrace()
            android.util.Log.e("DownloadWorker", "Download failed: ${e.message}", e)
            // Revert status on failure
            updateDownloadStatus(episodeId, 0, null)
            Result.failure()
        }
    }

    private suspend fun updateDownloadStatus(episodeId: String, status: Int, path: String?) {
        episodeDao.updateDownloadStatus(episodeId, status, path)
    }

    private fun createForegroundInfo(title: String): ForegroundInfo {
        val notificationId = 1
        val channelId = "download_channel"
        
        val channel = NotificationChannel(
            channelId,
            "Downloads",
            NotificationManager.IMPORTANCE_LOW
        )
        val notificationManager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)

        val notification = Notification.Builder(appContext, channelId)
            .setContentTitle("Downloading Podcast")
            .setContentText(title)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .build()

        return ForegroundInfo(notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    }

    companion object {
        const val KEY_EPISODE_ID = "episode_id"
        const val KEY_AUDIO_URL = "audio_url"
        const val KEY_EPISODE_TITLE = "episode_title"
    }
}
