package com.yuval.podcasts.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
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

        try {
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

    companion object {
        const val KEY_EPISODE_ID = "episode_id"
        const val KEY_AUDIO_URL = "audio_url"
    }
}
