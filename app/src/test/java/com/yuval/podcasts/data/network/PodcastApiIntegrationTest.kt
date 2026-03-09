package com.yuval.podcasts.data.network

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URL
import javax.net.ssl.HttpsURLConnection

class PodcastApiIntegrationTest {

    @Test
    fun fetchRss_respectsCancellation() = runTest {
        val testDispatcher = kotlinx.coroutines.Dispatchers.IO
        val api = PodcastApi(testDispatcher)
        var cancelled = false

        val job = launch(testDispatcher) {
            try {
                // Using a known large/slow feed to ensure we catch it inflight
                api.fetchRss("https://feeds.npr.org/510289/podcast.xml")
            } catch (e: Exception) {
                if (e is CancellationException) {
                    cancelled = true
                }
            }
        }

        // Allow real time for the network connection to start
        kotlinx.coroutines.delay(200)
        job.cancel()
        job.join()

        assertTrue("Fetch should have been cancelled", cancelled)
    }
}
