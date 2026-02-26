package com.yuval.podcasts.data.network

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

class PodcastApiIntegrationTest {

    @Test
    fun `test fetching and parsing Oxide and Friends RSS feed`() = runBlocking {
        // Setup
        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl("https://example.com/") // Base URL is ignored because we use @Url
            .client(okHttpClient)
            .build()

        val podcastApi = retrofit.create(PodcastApi::class.java)
        val rssParser = RssParser()
        val feedUrl = "https://feeds.transistor.fm/oxide-and-friends"

        // Execute Network Request
        val response = podcastApi.fetchRss(feedUrl)
        
        // Assert Network Success
        assertTrue("Network request should be successful", response.isSuccessful)
        assertNotNull("Response body should not be null", response.body())

        // Execute Parser
        val inputStream = response.body()!!.byteStream()
        val (podcast, episodes) = rssParser.parse(inputStream, feedUrl)

        // Assert Parsing Success
        assertEquals("https://feeds.transistor.fm/oxide-and-friends", podcast.feedUrl)
        assertTrue("Podcast title should contain Oxide and Friends", podcast.title.contains("Oxide and Friends"))
        
        // Validate episodes exist and have expected data fields
        assertTrue("Episodes list should not be empty", episodes.isNotEmpty())
        
        // Check the first episode to ensure all mapping worked
        val firstEpisode = episodes.first()
        assertTrue("Episode title should not be empty", firstEpisode.title.isNotEmpty())
        assertTrue("Episode audio URL should not be empty", firstEpisode.audioUrl.isNotEmpty())
        assertNotNull("Episode ID (guid) should not be null", firstEpisode.id)
    }
}
