package com.yuval.podcasts.data.repository

import com.yuval.podcasts.data.db.dao.EpisodeDao
import com.yuval.podcasts.data.db.dao.PodcastDao
import com.yuval.podcasts.data.db.entity.Episode
import com.yuval.podcasts.data.db.entity.Podcast
import com.yuval.podcasts.data.network.PodcastApi
import com.yuval.podcasts.data.network.RssParser
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PodcastRepository @Inject constructor(
    private val podcastApi: PodcastApi,
    private val rssParser: RssParser,
    private val podcastDao: PodcastDao,
    private val episodeDao: EpisodeDao
) {
    val allPodcasts: Flow<List<Podcast>> = podcastDao.getAllPodcasts()

    fun getEpisodes(feedUrl: String): Flow<List<Episode>> = episodeDao.getEpisodesForPodcast(feedUrl)

    suspend fun fetchAndStorePodcast(feedUrl: String) {
        val response = podcastApi.fetchRss(feedUrl)
        val body = response.body() ?: return
        
        val (podcast, episodes) = rssParser.parse(body.byteStream(), feedUrl)
        
        podcastDao.insertPodcast(podcast)
        episodeDao.insertEpisodes(episodes)
    }
}