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
import com.yuval.podcasts.data.Constants
import com.yuval.podcasts.data.db.dao.EpisodeDao
import com.yuval.podcasts.di.IoDispatcher
import com.yuval.podcasts.utils.StorageUtils
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.FileOutputStream
import java.io.IOException

@HiltWorker
class DownloadWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val episodeDao: EpisodeDao,
    private val okHttpClient: OkHttpClient,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val title = inputData.getString(KEY_EPISODE_TITLE) ?: "Episode"
        return createForegroundInfo(title)
    }

    override suspend fun doWork(): Result = withContext(ioDispatcher) {
        val episodeId = inputData.getString(KEY_EPISODE_ID) ?: return@withContext Result.failure()
        val audioUrl = inputData.getString(KEY_AUDIO_URL) ?: return@withContext Result.failure()
        val title = inputData.getString(KEY_EPISODE_TITLE) ?: "Episode"

        try {
            setForeground(createForegroundInfo(title))
            // Update status to Downloading
            updateDownloadStatus(episodeId, 1, null)

            val request = Request.Builder()
                .url(audioUrl)
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                updateDownloadStatus(episodeId, 0, null)
                return@withContext Result.failure()
            }

            val body = response.body ?: throw IOException("Empty response body")
            val outputFile = StorageUtils.getFileForEpisode(appContext, episodeId)

            FileOutputStream(outputFile).use { outputStream ->
                body.byteStream().use { inputStream ->
                    inputStream.copyTo(outputStream, 64 * 1024) // 64KB buffer for large audio files
                }
            }

            // Update status to Downloaded with the local path
            updateDownloadStatus(episodeId, 2, outputFile.absolutePath)
            
            Result.success()
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            android.util.Log.e("DownloadWorker", "Download failed: ${e.message}", e)
            // Revert status on failure
            updateDownloadStatus(episodeId, 0, null)
            Result.retry() // Retry for transient network issues
        }
    }

    private suspend fun updateDownloadStatus(episodeId: String, status: Int, path: String?) {
        episodeDao.updateDownloadStatus(episodeId, status, path)
    }

    private fun createForegroundInfo(title: String): ForegroundInfo {
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

        return ForegroundInfo(Constants.NOTIFICATION_ID_DOWNLOAD, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    }

    companion object {
        const val KEY_EPISODE_ID = "episode_id"
        const val KEY_AUDIO_URL = "audio_url"
        const val KEY_EPISODE_TITLE = "episode_title"
    }
}
