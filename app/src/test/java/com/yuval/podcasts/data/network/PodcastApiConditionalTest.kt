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
        var capturedIfNoneMatch: String? = "sentinel"
        var capturedIfModifiedSince: String? = "sentinel"
        val expectedXml = "<rss><channel><title>Podcast</title></channel></rss>"
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request()
                capturedIfNoneMatch = request.header("If-None-Match")
                capturedIfModifiedSince = request.header("If-Modified-Since")
                Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .addHeader("ETag", "\"new_etag_456\"")
                    .addHeader("Last-Modified", "Thu, 22 Oct 2025 08:00:00 GMT")
                    .body(expectedXml.toResponseBody("text/xml".toMediaType()))
                    .build()
            }
            .build()

        val podcastApi = PodcastApi(client, Dispatchers.Unconfined)
        var capturedBody: String? = null
        var capturedEtag: String? = null
        var capturedLastModified: String? = null

        podcastApi.withRssStreamConditional("http://example.com/feed.xml") { result ->
            assertTrue(result is RssFetchResult.Success)
            val success = result as RssFetchResult.Success
            capturedEtag = success.etag
            capturedLastModified = success.lastModified
            capturedBody = success.stream.bufferedReader().readText()
        }

        assertNull(capturedIfNoneMatch)
        assertNull(capturedIfModifiedSince)
        assertEquals("\"new_etag_456\"", capturedEtag)
        assertEquals("Thu, 22 Oct 2025 08:00:00 GMT", capturedLastModified)
        assertEquals(expectedXml, capturedBody)
    }

    @Test
    fun withRssStreamConditional_when200OkWithoutHeaders_returnsSuccessWithNullHeaders() = runTest {
        var capturedIfNoneMatch: String? = "sentinel"
        var capturedIfModifiedSince: String? = "sentinel"
        val expectedXml = "<rss><channel><title>Podcast</title></channel></rss>"
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request()
                capturedIfNoneMatch = request.header("If-None-Match")
                capturedIfModifiedSince = request.header("If-Modified-Since")
                Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(expectedXml.toResponseBody("text/xml".toMediaType()))
                    .build()
            }
            .build()

        val podcastApi = PodcastApi(client, Dispatchers.Unconfined)
        var capturedBody: String? = null
        var capturedEtag: String? = "sentinel"
        var capturedLastModified: String? = "sentinel"

        podcastApi.withRssStreamConditional("http://example.com/feed.xml") { result ->
            assertTrue(result is RssFetchResult.Success)
            val success = result as RssFetchResult.Success
            capturedEtag = success.etag
            capturedLastModified = success.lastModified
            capturedBody = success.stream.bufferedReader().readText()
        }

        assertNull(capturedIfNoneMatch)
        assertNull(capturedIfModifiedSince)
        assertNull(capturedEtag)
        assertNull(capturedLastModified)
        assertEquals(expectedXml, capturedBody)
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
        podcastApi.withRssStreamConditional("http://example.com/feed.xml") {}
    }
}
