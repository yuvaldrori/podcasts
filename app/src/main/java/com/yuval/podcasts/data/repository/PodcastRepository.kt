package com.yuval.podcasts.data.repository

import com.yuval.podcasts.data.Constants
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
import com.yuval.podcasts.data.network.PodcastRemoteDataSource
import com.yuval.podcasts.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.CoroutineDispatcher
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
    suspend fun addLocalFile(uri: android.net.Uri): Result<Unit>
}

@Singleton
class DefaultPodcastRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val database: AppDatabase,
    private val remoteDataSource: PodcastRemoteDataSource,
    private val podcastDao: PodcastDao,
    private val episodeDao: EpisodeDao,
    private val queueDao: QueueDao,
    private val workManager: WorkManager,
    private val localMediaDataSource: LocalMediaDataSource,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher
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
        // Fetch is already dispatched to IO via remoteDataSource
        val (podcast, episodes) = remoteDataSource.fetchPodcastData(feedUrl)
        podcastDao.insertPodcast(podcast)
        episodeDao.syncNetworkEpisodes(episodes)
    }

    override suspend fun refreshAll(): Unit = withContext(ioDispatcher) {
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

    override suspend fun markAllAsPlayed(): Unit = withContext(ioDispatcher) {
        episodeDao.markAllUnplayedAsPlayed()
    }

    override suspend fun markAsPlayed(id: String): Unit = withContext(ioDispatcher) {
        episodeDao.updatePlaybackStatus(id, true, System.currentTimeMillis())
    }

    override suspend fun reorderQueue(newOrderIds: List<String>): Unit = withContext(ioDispatcher) {
        val newQueue = newOrderIds.mapIndexed { index, id ->
            QueueState(id, index)
        }
        queueDao.updateQueue(newQueue)
    }

    override suspend fun unsubscribePodcast(feedUrl: String): Unit = withContext(ioDispatcher) {
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

    override suspend fun addLocalFile(uri: android.net.Uri): Result<Unit> = withContext(ioDispatcher) {
        try {
            val localFeedUrl = Constants.LOCAL_PODCAST_FEED_URL

            // 1. Ensure the Local Podcast feed exists
            if (podcastDao.getPodcast(localFeedUrl) == null) {
                podcastDao.insertPodcast(
                    Podcast(
                        feedUrl = localFeedUrl,
                        title = "Local Files",
                        description = "Manually imported audio files",
                        imageUrl = "", 
                        website = ""
                    )
                )
            }

            // 2. Delegate file IO and extraction to LocalMediaDataSource
            val metadataResult = localMediaDataSource.copyAndExtract(uri)
            val metadata = metadataResult.getOrThrow()

            // 3. Insert Episode
            val episodeId = "local_${System.currentTimeMillis()}_${metadata.destFile.name.hashCode()}"
            episodeDao.insertEpisodes(listOf(
                Episode(
                    id = episodeId,
                    podcastFeedUrl = localFeedUrl,
                    title = metadata.title,
                    description = metadata.description,
                    audioUrl = metadata.destFile.absolutePath, 
                    imageUrl = null,
                    episodeWebLink = null, 
                    pubDate = System.currentTimeMillis(),
                    duration = metadata.durationSecs,
                    downloadStatus = 2, // 2 = Downloaded
                    localFilePath = metadata.destFile.absolutePath,
                    isPlayed = false,
                    lastPlayedPosition = 0L,
                    completedAt = null
                )
            ))

            Result.success(Unit)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }
}
