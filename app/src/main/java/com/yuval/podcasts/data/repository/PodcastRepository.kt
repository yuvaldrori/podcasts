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
import com.yuval.podcasts.work.DownloadWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PodcastRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val podcastApi: PodcastApi,
    private val rssParser: RssParser,
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

        WorkManager.getInstance(context).enqueue(downloadWorkRequest)
    }
}