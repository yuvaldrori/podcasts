package com.yuval.podcasts.work

import android.content.Context
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
import com.yuval.podcasts.utils.LogManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicLong


@HiltWorker
class OpmlImportWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val opmlManager: OpmlManager,
    private val repository: PodcastRepository,
    private val logManager: LogManager,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(ioDispatcher) {
        val uriString = inputData.getString(KEY_URI) ?: return@withContext Result.failure()
        val uri = uriString.toUri()

        logManager.i("OpmlImportWorker", "Starting OPML import from $uri")

        try {
            val urls = appContext.contentResolver.openInputStream(uri)?.use { stream ->
                opmlManager.parse(stream)
            } ?: return@withContext Result.failure()

            val total = urls.size
            if (total == 0) {
                logManager.w("OpmlImportWorker", "OPML file is empty or invalid")
                return@withContext Result.success()
            }

            val lastForegroundUpdate = AtomicLong(0L)

            var isForegroundAllowed = true
            try {
                setProgress(workDataOf(Constants.WORK_KEY_PROGRESS to 0, Constants.WORK_KEY_TOTAL to total))
            } catch (e: Exception) {
                logManager.w("OpmlImportWorker", "Failed to set initial progress", mapOf("error" to e.message.toString()))
            }
            try {
                setForeground(createForegroundInfo(0, total))
            } catch (e: Exception) {
                isForegroundAllowed = false
                logManager.w("OpmlImportWorker", "Failed to set initial foreground status", mapOf("error" to e.message.toString()))
            }
            
            repository.refreshPodcasts(urls) { current, totalCount ->
                try {
                    setProgress(workDataOf(Constants.WORK_KEY_PROGRESS to current, Constants.WORK_KEY_TOTAL to totalCount))
                } catch (e: Exception) {
                    logManager.w("OpmlImportWorker", "Failed to set progress", mapOf("error" to e.message.toString()))
                }
                
                val currentTime = System.currentTimeMillis()
                val lastUpdate = lastForegroundUpdate.get()
                if (isForegroundAllowed && (current == totalCount || currentTime - lastUpdate > Constants.OPML_IMPORT_PROGRESS_NOTIFICATION_THROTTLE_MS)) {
                    lastForegroundUpdate.set(currentTime)
                    try {
                        setForeground(createForegroundInfo(current, totalCount))
                    } catch (e: Exception) {
                        isForegroundAllowed = false
                        logManager.w("OpmlImportWorker", "Failed to set foreground status progress", mapOf("error" to e.message.toString()))
                    }
                }
            }

            logManager.i("OpmlImportWorker", "OPML import completed: $total podcasts processed")
            Result.success()
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            logManager.e("OpmlImportWorker", "Import failed", mapOf("error" to e.message.toString()))
            Result.failure()
        }
    }

    private fun createForegroundInfo(progress: Int, total: Int): ForegroundInfo {
        return com.yuval.podcasts.utils.WorkerNotificationHelper.createForegroundInfo(
            context = appContext,
            notificationId = Constants.NOTIFICATION_ID_IMPORT,
            channelId = Constants.NOTIFICATION_CHANNEL_ID_IMPORT,
            channelName = appContext.getString(R.string.notification_channel_imports),
            title = appContext.getString(R.string.notification_importing_title),
            contentText = appContext.getString(R.string.notification_importing_progress, progress, total),
            progress = progress,
            maxProgress = total
        )
    }

    companion object {
        const val KEY_URI = "uri"
    }
}
