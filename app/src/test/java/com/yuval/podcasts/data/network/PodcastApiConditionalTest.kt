package com.yuval.podcasts.data.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class PodcastApiConditionalTest {

    @Test
    fun withRssStreamConditional_when304NotModified_returnsNotModified() = runTest {
        var capturedIfNoneMatch: String? = null
        var capturedIfModifiedSince: String? = null

        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request()
                capturedIfNoneMatch = request.header("If-None-Match")
                capturedIfModifiedSince = request.header("If-Modified-Since")

                Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(304)
                    .message("Not Modified")
                    .body("".toResponseBody(null))
                    .build()
            }
            .build()

        val podcastApi = PodcastApi(client, Dispatchers.Unconfined)
        val result = podcastApi.withRssStreamConditional(
            "http://example.com/feed.xml",
            etag = "\"etag_123\"",
            lastModified = "Wed, 21 Oct 2025 07:28:00 GMT"
        ) { it }

        assertEquals("\"etag_123\"", capturedIfNoneMatch)
        assertEquals("Wed, 21 Oct 2025 07:28:00 GMT", capturedIfModifiedSince)
        assertTrue(result is RssFetchResult.NotModified)
    }

    @Test
    fun withRssStreamConditional_when200OkWithHeaders_returnsSuccessWithHeaders() = runTest {
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .addHeader("ETag", "\"new_etag_456\"")
                    .addHeader("Last-Modified", "Thu, 22 Oct 2025 08:00:00 GMT")
                    .body("<rss><channel><title>Podcast</title></channel></rss>".toResponseBody("text/xml".toMediaType()))
                    .build()
            }
            .build()

        val podcastApi = PodcastApi(client, Dispatchers.Unconfined)
        val result = podcastApi.withRssStreamConditional("http://example.com/feed.xml") { it }

        assertTrue(result is RssFetchResult.Success)
        val success = result as RssFetchResult.Success
        assertEquals("\"new_etag_456\"", success.etag)
        assertEquals("Thu, 22 Oct 2025 08:00:00 GMT", success.lastModified)
    }

    @Test
    fun withRssStreamConditional_when200OkWithoutHeaders_returnsSuccessWithNullHeaders() = runTest {
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("<rss><channel><title>Podcast</title></channel></rss>".toResponseBody("text/xml".toMediaType()))
                    .build()
            }
            .build()

        val podcastApi = PodcastApi(client, Dispatchers.Unconfined)
        val result = podcastApi.withRssStreamConditional("http://example.com/feed.xml") { it }

        assertTrue(result is RssFetchResult.Success)
        val success = result as RssFetchResult.Success
        assertNull(success.etag)
        assertNull(success.lastModified)
    }

    @Test(expected = IOException::class)
    fun withRssStreamConditional_when404_throwsIOException() = runTest {
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(404)
                    .message("Not Found")
                    .body("Not Found".toResponseBody("text/plain".toMediaType()))
                    .build()
            }
            .build()

        val podcastApi = PodcastApi(client, Dispatchers.Unconfined)
        podcastApi.withRssStreamConditional("http://example.com/feed.xml") { it }
    }
}
