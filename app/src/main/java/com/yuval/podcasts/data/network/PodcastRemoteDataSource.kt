package com.yuval.podcasts.data.network

import com.yuval.podcasts.data.db.entity.NetworkEpisode
import com.yuval.podcasts.data.db.entity.Podcast
import com.yuval.podcasts.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PodcastRemoteDataSource @Inject constructor(
    private val podcastApi: PodcastApi,
    private val rssParser: RssParser,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {
    /**
     * Fetches the RSS feed from the network and parses it into application data models.
     * This execution is safely moved to the injected IO dispatcher.
     */
    suspend fun fetchPodcastData(feedUrl: String): Pair<Podcast, List<NetworkEpisode>> = withContext(ioDispatcher) {
        val inputStream = podcastApi.fetchRss(feedUrl)
        inputStream.use {
            rssParser.parse(it, feedUrl)
        }
    }
}
