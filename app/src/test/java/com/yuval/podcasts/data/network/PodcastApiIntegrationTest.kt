package com.yuval.podcasts.data.network

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetSocketAddress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class PodcastApiIntegrationTest {

    @Test
    fun fetchRss_respectsCancellation() = runBlocking {
        val requestReceived = CountDownLatch(1)
        val releaseServer = CountDownLatch(1)
        val executor = Executors.newSingleThreadExecutor()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.executor = executor
        server.createContext("/slow.xml") { exchange ->
            requestReceived.countDown()
            // Hold the HTTP response open until the client cancels (or timeout guard fires)
            releaseServer.await(5, TimeUnit.SECONDS)
            try {
                exchange.close()
            } catch (_: Exception) {
            }
        }
        server.start()

        try {
            val port = server.address.port
            val okHttpClient = OkHttpClient.Builder()
                .readTimeout(10, TimeUnit.SECONDS)
                .build()
            val api = PodcastApi(okHttpClient, Dispatchers.IO)
            var sawCancellation = false

            val job = launch(Dispatchers.Default) {
                try {
                    api.withRssStreamConditional("http://127.0.0.1:$port/slow.xml") { result ->
                        if (result is RssFetchResult.Success) {
                            result.stream.read()
                        }
                    }
                } catch (e: CancellationException) {
                    sawCancellation = true
                    throw e
                }
            }

            assertTrue(
                "Local test server should have received the in-flight HTTP request",
                requestReceived.await(5, TimeUnit.SECONDS)
            )
            job.cancel()
            job.join()

            assertTrue("In-flight fetch should have been cancelled via CancellationException", sawCancellation)
        } finally {
            releaseServer.countDown()
            server.stop(0)
            executor.shutdownNow()
        }
    }
}
