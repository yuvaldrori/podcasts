package com.yuval.podcasts.data.network

import com.yuval.podcasts.data.db.entity.NetworkEpisode
import com.yuval.podcasts.data.db.entity.Podcast
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.InputStream

class PodcastRemoteDataSourceTest {

    @Test
    fun `fetchPodcastData downloads and parses feed correctly`() = runTest {
        val podcastApi = mockk<PodcastApi>()
        val rssParser = mockk<RssParser>()
        val mockInputStream = mockk<InputStream>(relaxed = true)
        
        val url = "http://test.com/feed"
        val expectedPodcast = Podcast(url, "Title", "Desc", "Img", "Web")
        val expectedEpisodes = listOf<NetworkEpisode>(mockk())
        
        coEvery { podcastApi.fetchRss(url) } returns mockInputStream
        every { rssParser.parse(mockInputStream, url) } returns Pair(expectedPodcast, expectedEpisodes)
        
        val dataSource = PodcastRemoteDataSource(podcastApi, rssParser, UnconfinedTestDispatcher(testScheduler))

        val result = dataSource.fetchPodcastData(url)

        assertEquals(expectedPodcast, result.first)
        assertEquals(expectedEpisodes, result.second)
    }
}