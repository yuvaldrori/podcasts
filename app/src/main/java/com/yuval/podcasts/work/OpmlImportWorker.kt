package com.yuval.podcasts.work

import android.content.Context
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
import com.yuval.podcasts.utils.WorkerNotificationHelper
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

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

            val progressReporter = WorkerProgressReporter(
                workerTag = "OpmlImportWorker",
                logManager = logManager,
                setForegroundFn = { setForeground(it) },
                setProgressFn = { setProgress(it) }
            )
            progressReporter.setProgress(workDataOf(Constants.WORK_KEY_PROGRESS to 0, Constants.WORK_KEY_TOTAL to total))
            progressReporter.setInitialForeground { createForegroundInfo(0, total) }

            repository.refreshPodcasts(urls) { current, totalCount ->
                progressReporter.reportCountProgress(
                    current = current,
                    total = totalCount,
                    throttleMs = Constants.OPML_IMPORT_PROGRESS_NOTIFICATION_THROTTLE_MS,
                    createForegroundInfo = ::createForegroundInfo
                )
            }

            logManager.i("OpmlImportWorker", "OPML import completed: $total podcasts processed")
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logManager.e("OpmlImportWorker", "Import failed", mapOf("error" to e.message.toString()))
            Result.failure()
        }
    }

    private fun createForegroundInfo(progress: Int, total: Int): ForegroundInfo {
        return WorkerNotificationHelper.createForegroundInfo(
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
