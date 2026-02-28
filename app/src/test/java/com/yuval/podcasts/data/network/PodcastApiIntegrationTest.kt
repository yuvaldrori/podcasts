package com.yuval.podcasts.data.network

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.InputStreamReader

class PodcastApiIntegrationTest {

    @Test
    fun fetchRealRss_returnsXml() = runTest {
        val podcastApi = PodcastApi()

        // Using a reliable public RSS feed for integration testing
        val feedUrl = "https://feeds.npr.org/500005/podcast.xml" // NPR News Now
        
        val inputStream = podcastApi.fetchRss(feedUrl)
        val content = InputStreamReader(inputStream).readText()

        assertTrue(content.contains("<rss") || content.contains("<feed"))
        assertTrue(content.contains("<channel>"))
    }
}
