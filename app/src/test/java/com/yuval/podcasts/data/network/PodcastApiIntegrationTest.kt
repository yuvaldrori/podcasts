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
        val api = PodcastApi(kotlinx.coroutines.Dispatchers.IO)
        var cancelled = false

        val job = launch {
            try {
                // Using a known large/slow feed to ensure we catch it inflight
                api.fetchRss("https://feeds.npr.org/510289/podcast.xml")
            } catch (e: Exception) {
                // When using suspendCancellableCoroutine, the cancellation might manifest as 
                // a CancellationException OR a socket exception due to the disconnect() in invokeOnCancellation
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
