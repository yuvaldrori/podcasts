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
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PodcastRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val database: AppDatabase,
    private val podcastApi: PodcastApi,
    private val rssParser: RssParser,
    
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