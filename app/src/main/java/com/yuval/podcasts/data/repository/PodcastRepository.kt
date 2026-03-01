package com.yuval.podcasts.data.repository

import android.content.Context
import androidx.work.Data
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.yuval.podcasts.data.db.AppDatabase
import com.yuval.podcasts.data.db.dao.EpisodeDao
import com.yuval.podcasts.data.db.dao.PodcastDao
import com.yuval.podcasts.data.db.dao.QueueDao
import com.yuval.podcasts.data.db.entity.Episode
import com.yuval.podcasts.data.db.entity.EpisodeWithPodcast
import com.yuval.podcasts.data.db.entity.Podcast
import com.yuval.podcasts.data.db.entity.QueueState
import com.yuval.podcasts.data.network.PodcastApi
import com.yuval.podcasts.data.network.RssParser
import com.yuval.podcasts.data.opml.OpmlManager
import com.yuval.podcasts.work.DownloadWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.distinctUntilChanged
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.system.exitProcess

@Singleton
class PodcastRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val database: AppDatabase,
    private val podcastApi: PodcastApi,
    private val rssParser: RssParser,
    private val opmlManager: OpmlManager,
    private val podcastDao: PodcastDao,
    private val episodeDao: EpisodeDao,
    private val queueDao: QueueDao,
    private val workManager: WorkManager
) {
    val allPodcasts: Flow<List<Podcast>> = podcastDao.getAllPodcasts().distinctUntilChanged()
    val listeningQueue: Flow<List<EpisodeWithPodcast>> = queueDao.getQueueEpisodesWithPodcast().distinctUntilChanged()
    val unplayedEpisodes: Flow<List<EpisodeWithPodcast>> = episodeDao.getUnplayedEpisodesWithPodcast().distinctUntilChanged()

    fun getEpisodes(feedUrl: String): Flow<List<Episode>> = episodeDao.getEpisodesForPodcast(feedUrl).distinctUntilChanged()

    fun getEpisodeByIdFlow(id: String): Flow<Episode?> = episodeDao.getEpisodeByIdFlow(id).distinctUntilChanged()

    fun getEpisodeWithPodcastFlow(id: String): Flow<EpisodeWithPodcast?> = episodeDao.getEpisodeWithPodcastFlow(id).distinctUntilChanged()

    suspend fun fetchAndStorePodcast(feedUrl: String) {
        try {
            withContext(Dispatchers.IO) {
                val inputStream = podcastApi.fetchRss(feedUrl)
                val (podcast, episodes) = rssParser.parse(inputStream, feedUrl)
                podcastDao.insertPodcast(podcast)
                episodeDao.upsertEpisodes(episodes)
            }
        } catch (e: Throwable) {
            e.printStackTrace()
            // Ignore parse errors or OOMs for a single bad feed
        }
    }

    suspend fun refreshAll() = coroutineScope {
        val podcasts = allPodcasts.first()
        podcasts.map { podcast ->
            async {
                try {
                    fetchAndStorePodcast(podcast.feedUrl)
                } catch (e: Throwable) {
                    e.printStackTrace()
                }
            }
        }.awaitAll()
    }

    suspend fun importOpml(inputStream: InputStream) = coroutineScope {
        val urls = opmlManager.parse(inputStream)
        urls.map { url ->
            async {
                try {
                    fetchAndStorePodcast(url)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }.awaitAll()
    }

    suspend fun exportOpml(outputStream: OutputStream) {
        val podcasts = allPodcasts.first()
        opmlManager.export(podcasts, outputStream)
    }

    suspend fun backupDatabase(outputStream: OutputStream): Nothing = withContext(Dispatchers.IO) {
        // Force SQLite to write all WAL changes into the main DB file
        database.query("PRAGMA wal_checkpoint(FULL)", null).use { it.moveToFirst() }
        database.close()
        
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
        
        // Force restart after backup since we closed the DB
        exitProcess(0)
    }

    suspend fun restoreDatabase(inputStream: InputStream): Nothing = withContext(Dispatchers.IO) {
        // Close the database to release file locks
        database.close()
        
        java.util.zip.ZipInputStream(inputStream).use { zipIn ->
            var entry = zipIn.nextEntry
            while (entry != null) {
                val dbFile = context.getDatabasePath(entry.name)
                dbFile.outputStream().use { zipIn.copyTo(it) }
                zipIn.closeEntry()
                entry = zipIn.nextEntry
            }
        }
        
        // Force an app restart to reload the new database file cleanly
        exitProcess(0)
    }


    suspend fun markAllAsPlayed() {
        episodeDao.markAllUnplayedAsPlayed()
    }

    suspend fun markAsPlayed(id: String) {
        episodeDao.updatePlaybackStatus(id, true)
    }

    suspend fun reorderQueue(newOrderIds: List<String>) {
        val newQueue = newOrderIds.mapIndexed { index, id ->
            QueueState(id, index)
        }
        queueDao.updateQueue(newQueue)
    }

    suspend fun unsubscribePodcast(feedUrl: String) {
        // 1. Get all episodes for this podcast
        val episodes = episodeDao.getEpisodesForPodcastSync(feedUrl)
        
        // 2. Remove all downloaded files and queue entries
        episodes.forEach { episode ->
            queueDao.removeFromQueue(episode.id)
            episode.localFilePath?.let { path ->
                val file = File(path)
                if (file.exists()) file.delete()
            }
        }
        
        // 3. Delete the episodes from DB
        episodeDao.deleteEpisodesByPodcast(feedUrl)
        
        // 4. Delete the podcast from DB
        podcastDao.deletePodcast(feedUrl)
    }
}