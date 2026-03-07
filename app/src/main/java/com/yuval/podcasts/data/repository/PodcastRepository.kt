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
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
open class PodcastRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val database: AppDatabase,
    private val podcastApi: PodcastApi,
    private val rssParser: RssParser,
    
    private val podcastDao: PodcastDao,
    private val episodeDao: EpisodeDao,
    private val queueDao: QueueDao,
    private val workManager: WorkManager
) {
    open val allPodcasts: Flow<List<Podcast>> = podcastDao.getAllPodcasts().distinctUntilChanged()
    open val listeningQueue: Flow<List<EpisodeWithPodcast>> = queueDao.getQueueEpisodesWithPodcast()
    open val playHistory: Flow<List<EpisodeWithPodcast>> = episodeDao.getPlayHistory()

    // Limit concurrent network requests to prevent socket exhaustion
    private val networkSemaphore = Semaphore(10)
    open val unplayedEpisodes: Flow<List<EpisodeWithPodcast>> = episodeDao.getUnplayedEpisodesWithPodcast().distinctUntilChanged()

    open fun getEpisodes(feedUrl: String): Flow<List<Episode>> = episodeDao.getEpisodesForPodcast(feedUrl).distinctUntilChanged()

    open fun getEpisodeByIdFlow(id: String): Flow<Episode?> = episodeDao.getEpisodeByIdFlow(id).distinctUntilChanged()

    open fun getEpisodeWithPodcastFlow(id: String): Flow<EpisodeWithPodcast?> = episodeDao.getEpisodeWithPodcastFlow(id).distinctUntilChanged()

    open suspend fun fetchAndStorePodcast(feedUrl: String) {
        withContext(Dispatchers.IO) {
            val inputStream = podcastApi.fetchRss(feedUrl)
            val (podcast, episodes) = rssParser.parse(inputStream, feedUrl)
            podcastDao.insertPodcast(podcast)
            episodeDao.upsertEpisodes(episodes)
        }
    }

    open suspend fun refreshAll() {
        coroutineScope {
            val podcasts = allPodcasts.first()
            podcasts.map { podcast ->
                async {
                    networkSemaphore.withPermit {
                        try {
                            fetchAndStorePodcast(podcast.feedUrl)
                        } catch (e: Exception) {
                            if (e is kotlinx.coroutines.CancellationException) throw e
                            e.printStackTrace()
                        }
                    }
                }
            }.awaitAll()
        }
    }

    open suspend fun markAllAsPlayed() {
        episodeDao.markAllUnplayedAsPlayed()
    }

    open suspend fun markAsPlayed(id: String) {
        episodeDao.updatePlaybackStatus(id, true, System.currentTimeMillis())
    }

    open suspend fun reorderQueue(newOrderIds: List<String>) {
        val newQueue = newOrderIds.mapIndexed { index, id ->
            QueueState(id, index)
        }
        queueDao.updateQueue(newQueue)
    }

    open suspend fun unsubscribePodcast(feedUrl: String) {
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