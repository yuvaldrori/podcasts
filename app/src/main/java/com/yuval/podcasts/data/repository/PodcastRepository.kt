package com.yuval.podcasts.data.repository

import com.yuval.podcasts.utils.LogManager
import com.yuval.podcasts.data.Constants
import androidx.work.*
import com.yuval.podcasts.data.db.AppDatabase
import com.yuval.podcasts.data.db.dao.EpisodeDao
import com.yuval.podcasts.data.db.dao.PodcastDao
import com.yuval.podcasts.data.db.dao.QueueDao
import com.yuval.podcasts.data.db.entity.DownloadStatus
import com.yuval.podcasts.data.db.entity.Episode
import com.yuval.podcasts.data.db.entity.EpisodeWithPodcast
import com.yuval.podcasts.data.db.entity.Podcast
import com.yuval.podcasts.data.db.entity.QueueState
import com.yuval.podcasts.data.db.entity.Chapter
import com.yuval.podcasts.data.network.PodcastRemoteDataSource
import com.yuval.podcasts.di.IoDispatcher
import com.yuval.podcasts.work.DownloadWorker
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.distinctUntilChanged
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
    suspend fun fetchAndStorePodcast(feedUrl: String): Int
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

    override val unplayedEpisodes: Flow<List<EpisodeWithPodcast>> = episodeDao.getUnplayedEpisodesWithPodcast().distinctUntilChanged()

    override fun getEpisodes(feedUrl: String): Flow<List<Episode>> = episodeDao.getEpisodesForPodcast(feedUrl).distinctUntilChanged()

    override fun getEpisodeByIdFlow(id: String): Flow<Episode?> = episodeDao.getEpisodeByIdFlow(id).distinctUntilChanged()

    override fun getEpisodeWithPodcastFlow(id: String): Flow<EpisodeWithPodcast?> = episodeDao.getEpisodeWithPodcastFlow(id).distinctUntilChanged()

    override fun getChapters(episodeId: String): Flow<List<Chapter>> = chapterDao.getChaptersForEpisode(episodeId).distinctUntilChanged()

    override fun getQueueEpisodes(): Flow<List<Episode>> = queueDao.getQueueEpisodes()

    override suspend fun getPodcast(feedUrl: String): Podcast? {
        return podcastDao.getPodcast(feedUrl)
    }

    override suspend fun insertPodcast(podcast: Podcast) {
        podcastDao.insertPodcast(podcast)
    }

    override suspend fun insertEpisodes(episodes: List<Episode>) {
        episodeDao.insertEpisodes(episodes)
    }

    override suspend fun fetchAndStorePodcast(feedUrl: String): Int {
        // Fetch is already dispatched to IO via remoteDataSource
        val parsed = remoteDataSource.fetchPodcastData(feedUrl)

        val newEpisodesCount = database.withTransaction {
            podcastDao.insertPodcast(parsed.podcast)

            val networkEpisodes = parsed.episodes.map { it.episode }
            val count = episodeDao.syncNetworkEpisodes(networkEpisodes)

            // Update chapters for all episodes in bulk. Always run this (even when the
            // feed now carries no chapters) so chapters removed upstream are cleared from
            // the DB rather than left stale. Inserting an empty list is a no-op.
            val episodeIds = parsed.episodes.map { it.episode.id }
            val allChapters = parsed.episodes.flatMap { item ->
                item.chapters.map { it.copy(episodeId = item.episode.id) }
            }
            chapterDao.updateChaptersBulk(episodeIds, allChapters)
            count
        }
        return newEpisodesCount
    }

    override suspend fun markAllAsPlayed(): Unit {
        episodeDao.markAllUnplayedAsPlayed()
    }

    override suspend fun markAsPlayed(id: String): Unit {
        // Single atomic update so we never persist "played" without also resetting the
        // resume position (or vice-versa) if interrupted between two separate writes.
        episodeDao.markPlayedAndResetPosition(id, System.currentTimeMillis())
    }

    override suspend fun updatePlaybackStatus(id: String, isPlayed: Boolean, completedAt: Long?) {
        episodeDao.updatePlaybackStatus(id, isPlayed, completedAt)
    }

    override suspend fun updateLastPlayedPosition(id: String, position: Long) {
        episodeDao.updateLastPlayedPosition(id, position)
    }

    override suspend fun updateDownloadStatus(id: String, status: Int, path: String?) {
        episodeDao.updateDownloadStatus(id, status, path)
    }

    override suspend fun removeFromQueue(episodeId: String): Unit = withContext(ioDispatcher) {
        val episode = episodeDao.getEpisodeById(episodeId)

        // Drop from the queue and reset download status atomically, so we never end up in an
        // inconsistent state (still queued but NOT_DOWNLOADED, or vice-versa) on a crash.
        database.withTransaction {
            queueDao.removeFromQueue(episodeId)
            episodeDao.updateDownloadStatus(episodeId, DownloadStatus.NOT_DOWNLOADED.value, null)
        }

        workManager.cancelUniqueWork("${Constants.WORK_TAG_DOWNLOAD_PREFIX}$episodeId")

        // Blocking file I/O runs on ioDispatcher (this whole function), not the caller's thread.
        episode?.localFilePath?.let { path ->
            val file = File(path)
            if (file.exists() && !file.delete()) {
                logManager.w("PodcastRepository", "Failed to delete file for episode $episodeId: $path")
            }
        }
    }

    override suspend fun updateQueue(queue: List<QueueState>) {
        queueDao.updateQueue(queue)
    }

    override suspend fun reorderQueue(newOrderIds: List<String>): Unit = withContext(ioDispatcher) {
        val newQueue = newOrderIds.mapIndexed { index, id ->
            QueueState(id, index)
        }
        queueDao.updateQueue(newQueue)
    }

    override suspend fun requeueEpisode(episode: Episode) {
        database.withTransaction {
            val currentQueue = queueDao.getQueueEpisodesSync().toMutableList()
            val insertionIndex = currentQueue.indexOfFirst { it.pubDate > episode.pubDate }.let {
                if (it == -1) currentQueue.size else it
            }
            if (!currentQueue.any { it.id == episode.id }) {
                currentQueue.add(insertionIndex, episode)
                val newQueue = currentQueue.mapIndexed { index, ep -> QueueState(ep.id, index) }
                queueDao.updateQueue(newQueue)
            }
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
            episodes.map { episode ->
                async {
                    episode.localFilePath?.let { path ->
                        val file = File(path)
                        if (file.exists()) {
                            val deleted = file.delete()
                            if (!deleted) {
                                logManager.w("PodcastRepository", "Failed to delete file for episode ${episode.id}: $path")
                            }
                        }
                    }
                }
            }.awaitAll()
        }
        
        // 3. Delete episodes and podcast from DB
        database.withTransaction {
            episodeDao.deleteEpisodesByPodcast(feedUrl)
            podcastDao.deletePodcast(feedUrl)
        }
    }

    override suspend fun requeueMissingDownloads(): Unit = withContext(ioDispatcher) {
        val queuedEpisodes = queueDao.getQueueEpisodesSync()
        queuedEpisodes.forEach { episode ->
            if (episode.isLocal) return@forEach

            val fileExists = episode.localFilePath?.let { File(it).exists() } == true
            if (episode.downloadStatus != DownloadStatus.DOWNLOADED.value || !fileExists) {
                val downloadWorkRequest = DownloadWorker.createWorkRequest(
                    episodeId = episode.id,
                    audioUrl = episode.audioUrl,
                    title = episode.title,
                    isExpedited = false
                )

                workManager.enqueueUniqueWork(
                    "${Constants.WORK_TAG_DOWNLOAD_PREFIX}${episode.id}",
                    ExistingWorkPolicy.KEEP,
                    downloadWorkRequest
                )
            }
        }
    }
}
