package com.yuval.podcasts.data.repository

import android.content.Context
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.yuval.podcasts.data.db.dao.EpisodeDao
import com.yuval.podcasts.data.db.dao.PodcastDao
import com.yuval.podcasts.data.db.dao.QueueDao
import com.yuval.podcasts.data.db.entity.Episode
import com.yuval.podcasts.data.db.entity.Podcast
import com.yuval.podcasts.data.db.entity.QueueState
import com.yuval.podcasts.data.network.PodcastApi
import com.yuval.podcasts.data.network.RssParser
import com.yuval.podcasts.data.opml.OpmlManager
import com.yuval.podcasts.work.DownloadWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PodcastRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val podcastApi: PodcastApi,
    private val rssParser: RssParser,
    private val opmlManager: OpmlManager,
    private val podcastDao: PodcastDao,
    private val episodeDao: EpisodeDao,
    private val queueDao: QueueDao
) {
    val allPodcasts: Flow<List<Podcast>> = podcastDao.getAllPodcasts()
    val listeningQueue: Flow<List<Episode>> = queueDao.getQueueEpisodes()

    fun getEpisodes(feedUrl: String): Flow<List<Episode>> = episodeDao.getEpisodesForPodcast(feedUrl)

    suspend fun fetchAndStorePodcast(feedUrl: String) {
        val response = podcastApi.fetchRss(feedUrl)
        val body = response.body() ?: return
        
        val (podcast, episodes) = rssParser.parse(body.byteStream(), feedUrl)
        
        podcastDao.insertPodcast(podcast)
        episodeDao.insertEpisodes(episodes)
    }

    suspend fun importOpml(inputStream: InputStream) {
        val urls = opmlManager.parse(inputStream)
        urls.forEach { url ->
            try {
                fetchAndStorePodcast(url)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    suspend fun exportOpml(outputStream: OutputStream) {
        val podcasts = allPodcasts.first()
        opmlManager.export(podcasts, outputStream)
    }

    suspend fun backupDatabase(outputStream: OutputStream) {
        val dbFile = context.getDatabasePath("podcasts_db")
        val walFile = context.getDatabasePath("podcasts_db-wal")
        val shmFile = context.getDatabasePath("podcasts_db-shm")

        java.util.zip.ZipOutputStream(outputStream).use { zipOut ->
            listOf(dbFile, walFile, shmFile).forEach { file ->
                if (file.exists()) {
                    zipOut.putNextEntry(java.util.zip.ZipEntry(file.name))
                    file.inputStream().use { it.copyTo(zipOut) }
                    zipOut.closeEntry()
                }
            }
        }
    }

    suspend fun restoreDatabase(inputStream: InputStream) {
        java.util.zip.ZipInputStream(inputStream).use { zipIn ->
            var entry = zipIn.nextEntry
            while (entry != null) {
                val dbFile = context.getDatabasePath(entry.name)
                dbFile.outputStream().use { zipIn.copyTo(it) }
                zipIn.closeEntry()
                entry = zipIn.nextEntry
            }
        }
    }

    suspend fun enqueueEpisode(episode: Episode) {
        val currentQueue = queueDao.getQueue().first()
        val newPosition = if (currentQueue.isEmpty()) 0 else currentQueue.maxOf { it.position } + 1
        
        queueDao.updateQueue(listOf(QueueState(episode.id, newPosition)))

        // Trigger background download
        val downloadData = Data.Builder()
            .putString(DownloadWorker.KEY_EPISODE_ID, episode.id)
            .putString(DownloadWorker.KEY_AUDIO_URL, episode.audioUrl)
            .build()

        val downloadWorkRequest = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(downloadData)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            "download_${episode.id}",
            androidx.work.ExistingWorkPolicy.KEEP,
            downloadWorkRequest
        )
    }
}