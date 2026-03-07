package com.yuval.podcasts.data.repository

import android.content.Context
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

interface PodcastRepository {
    val allPodcasts: Flow<List<Podcast>>
    val listeningQueue: Flow<List<EpisodeWithPodcast>>
    val playHistory: Flow<List<EpisodeWithPodcast>>
    val unplayedEpisodes: Flow<List<EpisodeWithPodcast>>
    
    fun getEpisodes(feedUrl: String): Flow<List<Episode>>
    fun getEpisodeByIdFlow(id: String): Flow<Episode?>
    fun getEpisodeWithPodcastFlow(id: String): Flow<EpisodeWithPodcast?>
    
    suspend fun fetchAndStorePodcast(feedUrl: String)
    suspend fun refreshAll()
    suspend fun unsubscribePodcast(feedUrl: String)
    suspend fun markAllAsPlayed()
    suspend fun markAsPlayed(id: String)
    suspend fun reorderQueue(newOrderIds: List<String>)
}

@Singleton
class DefaultPodcastRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val database: AppDatabase,
    private val podcastApi: PodcastApi,
    private val rssParser: RssParser,
    private val podcastDao: PodcastDao,
    private val episodeDao: EpisodeDao,
    private val queueDao: QueueDao,
    private val workManager: WorkManager
) : PodcastRepository {

    override val allPodcasts: Flow<List<Podcast>> = podcastDao.getAllPodcasts().distinctUntilChanged()
    override val listeningQueue: Flow<List<EpisodeWithPodcast>> = queueDao.getQueueEpisodesWithPodcast()
    override val playHistory: Flow<List<EpisodeWithPodcast>> = episodeDao.getPlayHistory()

    private val networkSemaphore = Semaphore(10)
    override val unplayedEpisodes: Flow<List<EpisodeWithPodcast>> = episodeDao.getUnplayedEpisodesWithPodcast().distinctUntilChanged()

    override fun getEpisodes(feedUrl: String): Flow<List<Episode>> = episodeDao.getEpisodesForPodcast(feedUrl).distinctUntilChanged()

    override fun getEpisodeByIdFlow(id: String): Flow<Episode?> = episodeDao.getEpisodeByIdFlow(id).distinctUntilChanged()

    override fun getEpisodeWithPodcastFlow(id: String): Flow<EpisodeWithPodcast?> = episodeDao.getEpisodeWithPodcastFlow(id).distinctUntilChanged()

    override suspend fun fetchAndStorePodcast(feedUrl: String) {
        withContext(Dispatchers.IO) {
            val inputStream = podcastApi.fetchRss(feedUrl)
            val (podcast, episodes) = rssParser.parse(inputStream, feedUrl)
            podcastDao.insertPodcast(podcast)
            episodeDao.upsertEpisodes(episodes)
        }
    }

    override suspend fun refreshAll() {
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

    override suspend fun markAllAsPlayed() {
        episodeDao.markAllUnplayedAsPlayed()
    }

    override suspend fun markAsPlayed(id: String) {
        episodeDao.updatePlaybackStatus(id, true, System.currentTimeMillis())
    }

    override suspend fun reorderQueue(newOrderIds: List<String>) {
        val newQueue = newOrderIds.mapIndexed { index, id ->
            QueueState(id, index)
        }
        queueDao.updateQueue(newQueue)
    }

    override suspend fun unsubscribePodcast(feedUrl: String) {
        val episodes = episodeDao.getEpisodesForPodcastSync(feedUrl)
        episodes.forEach { episode ->
            queueDao.removeFromQueue(episode.id)
            episode.localFilePath?.let { path ->
                val file = File(path)
                if (file.exists()) file.delete()
            }
        }
        episodeDao.deleteEpisodesByPodcast(feedUrl)
        podcastDao.deletePodcast(feedUrl)
    }
}
