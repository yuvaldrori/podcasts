package com.yuval.podcasts.work

import android.content.Context
import androidx.annotation.VisibleForTesting
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.yuval.podcasts.R
import com.yuval.podcasts.data.Constants
import com.yuval.podcasts.data.db.dao.EpisodeDao
import com.yuval.podcasts.data.db.entity.DownloadStatus
import com.yuval.podcasts.data.network.await
import com.yuval.podcasts.data.repository.DownloadProgressTracker
import com.yuval.podcasts.di.IoDispatcher
import com.yuval.podcasts.utils.LogManager
import com.yuval.podcasts.utils.StorageUtils
import com.yuval.podcasts.utils.WorkerNotificationHelper
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.atomic.AtomicLong

@HiltWorker
class DownloadWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val episodeDao: EpisodeDao,
    private val okHttpClient: OkHttpClient,
    private val logManager: LogManager,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val downloadProgressTracker: DownloadProgressTracker
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val episodeId = inputData.getString(KEY_EPISODE_ID) ?: ""
        val title = inputData.getString(KEY_EPISODE_TITLE) ?: "Episode"
        return createForegroundInfo(episodeId, title, 0)
    }

    override suspend fun doWork(): Result = withContext(ioDispatcher) {
        val episodeId = inputData.getString(KEY_EPISODE_ID) ?: return@withContext Result.failure()
        val audioUrl = inputData.getString(KEY_AUDIO_URL) ?: return@withContext Result.failure()
        val title = inputData.getString(KEY_EPISODE_TITLE) ?: "Episode"

        logManager.i("DownloadWorker", "Starting download for $episodeId: $audioUrl")

        val progressReporter = WorkerProgressReporter(
            workerTag = "DownloadWorker",
            logManager = logManager,
            setForegroundFn = { setForeground(it) },
            setProgressFn = { setProgress(it) }
        )

        try {
            progressReporter.setInitialForeground { createForegroundInfo(episodeId, title, 0) }
            episodeDao.updateDownloadStatus(episodeId, DownloadStatus.DOWNLOADING.value, null)

            val request = Request.Builder()
                .url(audioUrl)
                .build()

            okHttpClient.newCall(request).await().use { response ->
                if (!response.isSuccessful) {
                    logManager.e("DownloadWorker", "Failed to download $episodeId: ${response.code}")
                    revertDownloadState(episodeId)
                    return@withContext if (response.code in 400..499) Result.failure() else Result.retry()
                }

                val body = response.body
                val totalBytes = body.contentLength()
                val outputFile = StorageUtils.getFileForEpisode(appContext, episodeId)
                val partFile = File(outputFile.absolutePath + ".part")

                var lastUiProgressPercent = 0
                var lastUiUpdateTime = 0L
                var lastWorkerProgressPercent = 0
                var lastWorkerUpdateTime = 0L
                var lastNotifiedPercent = 0
                var lastNotifiedTime = System.currentTimeMillis()

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
                                    revertDownloadState(episodeId)
                                    return@withContext Result.failure()
                                }
                                outputStream.write(buffer, 0, bytesRead)
                                totalRead += bytesRead

                                if (totalBytes > 0) {
                                    val progress = (totalRead * 100 / totalBytes).toInt()
                                    val currentTime = System.currentTimeMillis()

                                    if (progress > lastUiProgressPercent &&
                                        currentTime - lastUiUpdateTime >= Constants.DOWNLOAD_PROGRESS_UI_THROTTLE_MS
                                    ) {
                                        downloadProgressTracker.updateProgress(episodeId, progress)
                                        lastUiProgressPercent = progress
                                        lastUiUpdateTime = currentTime
                                    }

                                    if (progress > lastWorkerProgressPercent &&
                                        currentTime - lastWorkerUpdateTime >= Constants.DOWNLOAD_PROGRESS_WORKER_THROTTLE_MS
                                    ) {
                                        progressReporter.setProgress(workDataOf(Constants.WORK_KEY_PROGRESS to progress))
                                        lastWorkerProgressPercent = progress
                                        lastWorkerUpdateTime = currentTime
                                    }

                                    if (progress >= lastNotifiedPercent + Constants.DOWNLOAD_PROGRESS_NOTIFICATION_INCREMENT_PERCENT &&
                                        currentTime - lastNotifiedTime >= Constants.DOWNLOAD_PROGRESS_NOTIFICATION_THROTTLE_MS &&
                                        tryAcquireNotificationSlot(currentTime)
                                    ) {
                                        progressReporter.setForegroundIfAllowed {
                                            createForegroundInfo(episodeId, title, progress)
                                        }
                                        lastNotifiedPercent = progress
                                        lastNotifiedTime = currentTime
                                    }
                                }
                            }
                        }
                    }

                    if (!partFile.renameTo(outputFile)) {
                        throw IOException("Failed to rename .part file to final destination")
                    }

                    logManager.i("DownloadWorker", "Download completed for $episodeId. Saved to ${outputFile.absolutePath}")
                    episodeDao.updateDownloadStatusAfterSuccess(
                        episodeId,
                        DownloadStatus.DOWNLOADED.value,
                        outputFile.absolutePath
                    )
                    downloadProgressTracker.clearProgress(episodeId)

                    Result.success()
                } catch (e: Exception) {
                    if (partFile.exists()) {
                        partFile.delete()
                    }
                    throw e
                }
            }
        } catch (e: CancellationException) {
            withContext(NonCancellable) {
                revertDownloadState(episodeId)
            }
            throw e
        } catch (e: Exception) {
            logManager.e("DownloadWorker", "Download failed for $episodeId", mapOf("error" to e.message.toString()))
            revertDownloadState(episodeId)

            return@withContext when (e) {
                is IOException -> {
                    if (runAttemptCount >= MAX_RETRY_COUNT) {
                        logManager.e("DownloadWorker", "Download failed after $MAX_RETRY_COUNT attempts", mapOf("error" to e.message.toString()))
                        Result.failure()
                    } else {
                        Result.retry()
                    }
                }
                else -> Result.failure()
            }
        }
    }

    private suspend fun revertDownloadState(episodeId: String) {
        episodeDao.updateDownloadStatus(episodeId, DownloadStatus.NOT_DOWNLOADED.value, null)
        downloadProgressTracker.clearProgress(episodeId)
    }

    @VisibleForTesting
    internal fun createForegroundInfo(episodeId: String, title: String, progress: Int): ForegroundInfo {
        val notificationId = Constants.NOTIFICATION_ID_DOWNLOAD + (episodeId.hashCode() and NOTIFICATION_ID_HASH_MASK)
        return WorkerNotificationHelper.createForegroundInfo(
            context = appContext,
            notificationId = notificationId,
            channelId = Constants.NOTIFICATION_CHANNEL_ID_DOWNLOAD,
            channelName = appContext.getString(R.string.notification_channel_downloads),
            title = appContext.getString(R.string.notification_downloading_title),
            contentText = title,
            progress = progress,
            maxProgress = NOTIFICATION_PROGRESS_MAX
        )
    }

    companion object {
        const val KEY_EPISODE_ID = "episode_id"
        const val KEY_AUDIO_URL = "audio_url"
        const val KEY_EPISODE_TITLE = "episode_title"

        private const val MAX_RETRY_COUNT = 3
        private const val NOTIFICATION_ID_HASH_MASK = 0x00ffffff
        private const val NOTIFICATION_PROGRESS_MAX = 100

        private val lastGlobalNotificationMs = AtomicLong(0L)

        internal fun tryAcquireNotificationSlot(
            nowMs: Long,
            minIntervalMs: Long = Constants.DOWNLOAD_PROGRESS_NOTIFICATION_GLOBAL_MIN_INTERVAL_MS
        ): Boolean {
            while (true) {
                val prev = lastGlobalNotificationMs.get()
                if (nowMs - prev < minIntervalMs) return false
                if (lastGlobalNotificationMs.compareAndSet(prev, nowMs)) return true
            }
        }

        @VisibleForTesting
        internal fun resetNotificationRateLimit() {
            lastGlobalNotificationMs.set(0L)
        }

        fun createWorkRequest(
            episodeId: String,
            audioUrl: String,
            title: String,
            isExpedited: Boolean = false
        ): OneTimeWorkRequest {
            val downloadData = Data.Builder()
                .putString(KEY_EPISODE_ID, episodeId)
                .putString(KEY_AUDIO_URL, audioUrl)
                .putString(KEY_EPISODE_TITLE, title)
                .build()

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val builder = OneTimeWorkRequestBuilder<DownloadWorker>()
                .setConstraints(constraints)
                .setInputData(downloadData)

            if (isExpedited) {
                builder.setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            }

            return builder.build()
        }
    }
}
