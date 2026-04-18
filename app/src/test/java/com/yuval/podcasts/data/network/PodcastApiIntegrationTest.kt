package com.yuval.podcasts.data.network

import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.Assert.assertTrue
import org.junit.Test

class PodcastApiIntegrationTest {

    @Test
    fun fetchRss_respectsCancellation() = runTest {
        val okHttpClient = OkHttpClient()
        val api = PodcastApi(okHttpClient, kotlinx.coroutines.Dispatchers.IO)
        var cancelled = false

        val job = launch {
            try {
                // Using a known large/slow feed to ensure we catch it inflight
                api.fetchRss("https://feeds.npr.org/510289/podcast.xml")
            } catch (e: Exception) {
                // When using okhttp, cancellation manifests as an IOException
                if (e is CancellationException || e is java.io.IOException || e.cause is CancellationException) {
                    cancelled = true
                }
            }
        }

        // Allow some time for the network connection to start
        kotlinx.coroutines.delay(100)
        job.cancel()
        job.join()

        assertTrue("Fetch should have been cancelled or aborted via network disconnect", cancelled)
    }
}
