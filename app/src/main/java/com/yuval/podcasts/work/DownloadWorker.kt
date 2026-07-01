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
import com.yuval.podcasts.R
import com.yuval.podcasts.data.Constants
import com.yuval.podcasts.data.db.dao.EpisodeDao
import com.yuval.podcasts.data.db.entity.DownloadStatus
import com.yuval.podcasts.di.IoDispatcher
import com.yuval.podcasts.utils.LogManager
import com.yuval.podcasts.utils.StorageUtils
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.FileOutputStream
import java.io.IOException
import com.yuval.podcasts.data.network.await


@HiltWorker
class DownloadWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val episodeDao: EpisodeDao,
    private val okHttpClient: OkHttpClient,
    private val logManager: LogManager,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val title = inputData.getString(KEY_EPISODE_TITLE) ?: "Episode"
        return createForegroundInfo(title, 0)
    }

    override suspend fun doWork(): Result = withContext(ioDispatcher) {
        val episodeId = inputData.getString(KEY_EPISODE_ID) ?: return@withContext Result.failure()
        val audioUrl = inputData.getString(KEY_AUDIO_URL) ?: return@withContext Result.failure()
        val title = inputData.getString(KEY_EPISODE_TITLE) ?: "Episode"

        logManager.i("DownloadWorker", "Starting download for $episodeId: $audioUrl")

        try {
            setForeground(createForegroundInfo(title, 0))
            // Update status to Downloading
            updateDownloadStatus(episodeId, DownloadStatus.DOWNLOADING.value, null)

            val request = Request.Builder()
                .url(audioUrl)
                .build()

            val response = okHttpClient.newCall(request).await()
            response.use { 
                if (!response.isSuccessful) {
                    logManager.e("DownloadWorker", "Failed to download $episodeId: ${response.code}")
                    updateDownloadStatus(episodeId, DownloadStatus.NOT_DOWNLOADED.value, null)
                    return@withContext if (response.code in 400..499) Result.failure() else Result.retry()
                }

                val body = response.body
                val totalBytes = body.contentLength()
                val outputFile = StorageUtils.getFileForEpisode(appContext, episodeId)
                val partFile = File(outputFile.absolutePath + ".part")

                var lastProgressPercent = 0
                var lastProgressUpdateTime = 0L
                var lastNotifiedPercent = 0

                try {
                    FileOutputStream(partFile).use { outputStream ->
                        body.byteStream().use { inputStream ->
                            val buffer = ByteArray(Constants.DOWNLOAD_BUFFER_SIZE_BYTES)
                            var bytesRead: Int
                            var totalRead = 0L

                            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                                if (isStopped) {
                                    logManager.w("DownloadWorker", "Download stopped for $episodeId")
                                    partFile.delete()
                                    // Revert the DOWNLOADING status set above so the episode
                                    // doesn't stay stuck as "Downloading" after being stopped.
                                    updateDownloadStatus(episodeId, DownloadStatus.NOT_DOWNLOADED.value, null)
                                    return@withContext Result.failure()
                                }
                                outputStream.write(buffer, 0, bytesRead)
                                totalRead += bytesRead

                                // Update progress if total size is known
                                if (totalBytes > 0) {
                                    val progress = (totalRead * 100 / totalBytes).toInt()
                                    val currentTime = System.currentTimeMillis()

                                    // Throttle WorkManager progress updates by elapsed time, and
                                    // only when the percentage actually advances, to limit IPC.
                                    if (progress > lastProgressPercent &&
                                        currentTime - lastProgressUpdateTime > Constants.DOWNLOAD_PROGRESS_WORKER_THROTTLE_MS
                                    ) {
                                        setProgress(androidx.work.workDataOf("progress" to progress))
                                        lastProgressPercent = progress
                                        lastProgressUpdateTime = currentTime
                                    }

                                    // Update Notification based on percentage step to avoid IPC overhead
                                    if (progress >= lastNotifiedPercent + Constants.DOWNLOAD_PROGRESS_NOTIFICATION_INCREMENT_PERCENT) {
                                        setForeground(createForegroundInfo(title, progress))
                                        lastNotifiedPercent = progress
                                    }
                                }
                            }
                        }
                    }

                    if (!partFile.renameTo(outputFile)) {
                        throw IOException("Failed to rename .part file to final destination")
                    }

                    logManager.i("DownloadWorker", "Download completed for $episodeId. Saved to ${outputFile.absolutePath}")
                    // Update status to Downloaded with the local path, but only if we haven't been cancelled/removed
                    episodeDao.updateDownloadStatusAfterSuccess(episodeId, DownloadStatus.DOWNLOADED.value, outputFile.absolutePath)
                    
                    Result.success()
                } catch (e: Exception) {
                    if (partFile.exists()) {
                        partFile.delete()
                    }
                    throw e
                }
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            logManager.e("DownloadWorker", "Download failed for $episodeId", mapOf("error" to e.message.toString()))
            // Revert status on failure
            updateDownloadStatus(episodeId, DownloadStatus.NOT_DOWNLOADED.value, null)
            
            // Distinguish between transient and permanent errors
            return@withContext when (e) {
                is IOException -> Result.retry()
                else -> Result.failure()
            }
        }
    }

    private suspend fun updateDownloadStatus(episodeId: String, status: Int, path: String?) {
        episodeDao.updateDownloadStatus(episodeId, status, path)
    }

    @androidx.annotation.VisibleForTesting
    internal fun createForegroundInfo(title: String, progress: Int): ForegroundInfo {
        val channelId = "download_channel"
        val channel = NotificationChannel(
            channelId,
            appContext.getString(R.string.notification_channel_downloads),
            NotificationManager.IMPORTANCE_LOW
        )
        val notificationManager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)

        val notification = Notification.Builder(appContext, channelId)
            .setContentTitle(appContext.getString(R.string.notification_downloading_title))
            .setContentText(title)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(100, progress, progress == 0)
            .setOngoing(true)
            .build()


        return ForegroundInfo(Constants.NOTIFICATION_ID_DOWNLOAD, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    }

    companion object {
        const val KEY_EPISODE_ID = "episode_id"
        const val KEY_AUDIO_URL = "audio_url"
        const val KEY_EPISODE_TITLE = "episode_title"

        fun createWorkRequest(
            episodeId: String,
            audioUrl: String,
            title: String,
            isExpedited: Boolean = false
        ): androidx.work.OneTimeWorkRequest {
            val downloadData = androidx.work.Data.Builder()
                .putString(KEY_EPISODE_ID, episodeId)
                .putString(KEY_AUDIO_URL, audioUrl)
                .putString(KEY_EPISODE_TITLE, title)
                .build()

            val constraints = androidx.work.Constraints.Builder()
                .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
                .build()

            val builder = androidx.work.OneTimeWorkRequestBuilder<DownloadWorker>()
                .setConstraints(constraints)
                .setInputData(downloadData)

            if (isExpedited) {
                builder.setExpedited(androidx.work.OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            }

            return builder.build()
        }
    }
}

