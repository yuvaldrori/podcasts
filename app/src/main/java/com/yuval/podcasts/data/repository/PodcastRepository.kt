package com.yuval.podcasts.data.repository

import android.util.Log
import com.yuval.podcasts.utils.LogManager
import com.yuval.podcasts.data.Constants
import androidx.work.*
import com.yuval.podcasts.data.db.AppDatabase
import com.yuval.podcasts.data.db.dao.EpisodeDao
import com.yuval.podcasts.data.db.dao.PodcastDao
import com.yuval.podcasts.data.db.dao.QueueDao
import com.yuval.podcasts.data.db.entity.Episode
import com.yuval.podcasts.data.db.entity.EpisodeWithPodcast
import com.yuval.podcasts.data.db.entity.Podcast
import com.yuval.podcasts.data.db.entity.QueueState
import com.yuval.podcasts.data.db.entity.Chapter
import com.yuval.podcasts.data.network.PodcastRemoteDataSource
import com.yuval.podcasts.di.IoDispatcher
import com.yuval.podcasts.work.DownloadWorker
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import androidx.room.withTransaction
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

interface PodcastRepository {
    val allPodcasts: Flow<List<Podcast>>
    val listeningQueue: Flow<List<EpisodeWithPodcast>>
    val unplayedEpisodes: Flow<List<EpisodeWithPodcast>>
    
    fun getEpisodes(feedUrl: String): Flow<List<Episode>>
    fun getEpisodeByIdFlow(id: String): Flow<Episode?>
    fun getEpisodeWithPodcastFlow(id: String): Flow<EpisodeWithPodcast?>
    fun getChapters(episodeId: String): Flow<List<Chapter>>
    fun getQueueEpisodes(): Flow<List<Episode>>
    
    suspend fun getPodcast(feedUrl: String): Podcast?
    suspend fun insertPodcast(podcast: Podcast)
    suspend fun insertEpisodes(episodes: List<Episode>)
    suspend fun fetchAndStorePodcast(feedUrl: String)
    suspend fun refreshAll()
    suspend fun unsubscribePodcast(feedUrl: String)
    suspend fun markAllAsPlayed()
    suspend fun markAsPlayed(id: String)
    suspend fun updatePlaybackStatus(id: String, isPlayed: Boolean, completedAt: Long? = null)
    suspend fun updateLastPlayedPosition(id: String, position: Long)
    suspend fun updateDownloadStatus(id: String, status: Int, path: String?)
    suspend fun removeFromQueue(episodeId: String)
    suspend fun updateQueue(queue: List<QueueState>)
    suspend fun reorderQueue(newOrderIds: List<String>)
    suspend fun requeueEpisode(episode: Episode)
    suspend fun requeueMissingDownloads()
}
@Singleton
class DefaultPodcastRepository @Inject constructor(
    private val database: AppDatabase,
    private val remoteDataSource: PodcastRemoteDataSource,
    private val podcastDao: PodcastDao,
    private val episodeDao: EpisodeDao,
    private val queueDao: QueueDao,
    private val chapterDao: com.yuval.podcasts.data.db.dao.ChapterDao,
    private val workManager: WorkManager,
    private val localMediaDataSource: LocalMediaDataSource,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val logManager: LogManager
) : PodcastRepository {


    override val allPodcasts: Flow<List<Podcast>> = podcastDao.getAllPodcasts().distinctUntilChanged()
    override val listeningQueue: Flow<List<EpisodeWithPodcast>> = queueDao.getQueueEpisodesWithPodcast()

    private val networkSemaphore = Semaphore(Constants.MAX_PARALLEL_REFRESHES)
    override val unplayedEpisodes: Flow<List<EpisodeWithPodcast>> = episodeDao.getUnplayedEpisodesWithPodcast().distinctUntilChanged()

    override fun getEpisodes(feedUrl: String): Flow<List<Episode>> = episodeDao.getEpisodesForPodcast(feedUrl).distinctUntilChanged()

    override fun getEpisodeByIdFlow(id: String): Flow<Episode?> = episodeDao.getEpisodeByIdFlow(id).distinctUntilChanged()

    override fun getEpisodeWithPodcastFlow(id: String): Flow<EpisodeWithPodcast?> = episodeDao.getEpisodeWithPodcastFlow(id).distinctUntilChanged()

    override fun getChapters(episodeId: String): Flow<List<Chapter>> = chapterDao.getChaptersForEpisode(episodeId).distinctUntilChanged()

    override fun getQueueEpisodes(): Flow<List<Episode>> = queueDao.getQueueEpisodes()

    override suspend fun getPodcast(feedUrl: String): Podcast? = withContext(ioDispatcher) {
        podcastDao.getPodcast(feedUrl)
    }

    override suspend fun insertPodcast(podcast: Podcast) = withContext(ioDispatcher) {
        podcastDao.insertPodcast(podcast)
    }

    override suspend fun insertEpisodes(episodes: List<Episode>) = withContext(ioDispatcher) {
        episodeDao.insertEpisodes(episodes)
    }

    override suspend fun fetchAndStorePodcast(feedUrl: String) {
        // Fetch is already dispatched to IO via remoteDataSource
        val parsed = remoteDataSource.fetchPodcastData(feedUrl)
        
        database.withTransaction {
            podcastDao.insertPodcast(parsed.podcast)
            
            val networkEpisodes = parsed.episodes.map { it.episode }
            episodeDao.syncNetworkEpisodes(networkEpisodes)
            
            // Update chapters for all episodes in bulk
            val allChapters = parsed.episodes.flatMap { item ->
                item.chapters.map { it.copy(episodeId = item.episode.id) }
            }
            if (allChapters.isNotEmpty()) {
                val episodeIds = parsed.episodes.map { it.episode.id }
                chapterDao.updateChaptersBulk(episodeIds, allChapters)
            }
        }
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
                            Log.e("PodcastRepository", "Failed to refresh podcast: ${podcast.feedUrl}", e)
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
        episodeDao.updateLastPlayedPosition(id, 0L)
    }

    override suspend fun updatePlaybackStatus(id: String, isPlayed: Boolean, completedAt: Long?) = withContext(ioDispatcher) {
        episodeDao.updatePlaybackStatus(id, isPlayed, completedAt)
    }

    override suspend fun updateLastPlayedPosition(id: String, position: Long) = withContext(ioDispatcher) {
        episodeDao.updateLastPlayedPosition(id, position)
    }

    override suspend fun updateDownloadStatus(id: String, status: Int, path: String?) = withContext(ioDispatcher) {
        episodeDao.updateDownloadStatus(id, status, path)
    }

    override suspend fun removeFromQueue(episodeId: String) = withContext(ioDispatcher) {
        queueDao.removeFromQueue(episodeId)
    }

    override suspend fun updateQueue(queue: List<QueueState>) = withContext(ioDispatcher) {
        queueDao.updateQueue(queue)
    }

    override suspend fun reorderQueue(newOrderIds: List<String>): Unit = withContext(ioDispatcher) {
        val newQueue = newOrderIds.mapIndexed { index, id ->
            QueueState(id, index)
        }
        queueDao.updateQueue(newQueue)
    }

    override suspend fun requeueEpisode(episode: Episode) = withContext(ioDispatcher) {
        database.withTransaction {
            val currentQueue = queueDao.getQueueEpisodesSync()
            val insertionIndex = currentQueue.indexOfFirst { it.pubDate > episode.pubDate }.let {
                if (it == -1) currentQueue.size else it
            }
            
            queueDao.shiftQueuePositionsDownFrom(insertionIndex)
            queueDao.insertQueueItem(QueueState(episode.id, insertionIndex))
            
            episodeDao.updatePlaybackStatus(episode.id, isPlayed = true)
        }
    }

    override suspend fun unsubscribePodcast(feedUrl: String): Unit = withContext(ioDispatcher) {
        val episodes = episodeDao.getEpisodesForPodcastSync(feedUrl)
        val episodeIds = episodes.map { it.id }
        
        // 1. Bulk remove from queue
        queueDao.removeFromQueueBulk(episodeIds)
        
        // 2. Delete local files in parallel
        coroutineScope {
            episodes.forEach { episode ->
                episode.localFilePath?.let { path ->
                    launch {
                        val file = File(path)
                        if (file.exists()) file.delete()
                    }
                }
            }
        }
        
        // 3. Delete episodes and podcast from DB
        database.withTransaction {
            episodeDao.deleteEpisodesByPodcast(feedUrl)
            podcastDao.deletePodcast(feedUrl)
        }
    }

    override suspend fun requeueMissingDownloads(): Unit = withContext(ioDispatcher) {
        val missing = queueDao.getQueuedEpisodesNotDownloaded()
        missing.forEach { episode ->
            if (episode.isLocal) return@forEach

            val downloadData = Data.Builder()
                .putString(DownloadWorker.KEY_EPISODE_ID, episode.id)
                .putString(DownloadWorker.KEY_AUDIO_URL, episode.audioUrl)
                .putString(DownloadWorker.KEY_EPISODE_TITLE, episode.title)
                .build()

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val downloadWorkRequest = OneTimeWorkRequestBuilder<DownloadWorker>()
                .setConstraints(constraints)
                .setInputData(downloadData)
                .build()

            workManager.enqueueUniqueWork(
                "download_${episode.id}",
                ExistingWorkPolicy.KEEP,
                downloadWorkRequest
            )
        }
    }
}
