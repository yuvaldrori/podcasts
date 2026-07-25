package com.yuval.podcasts.data.network

import com.yuval.podcasts.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

sealed interface RssFetchResult {
    object NotModified : RssFetchResult
    data class Success(
        val stream: InputStream,
        val etag: String?,
        val lastModified: String?
    ) : RssFetchResult
}

@Singleton
class PodcastApi @Inject constructor(
    private val okHttpClient: OkHttpClient,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {
    suspend fun <T> withRssStreamConditional(
        urlString: String,
        etag: String? = null,
        lastModified: String? = null,
        block: suspend (RssFetchResult) -> T
    ): T = withContext(ioDispatcher) {
        val requestBuilder = Request.Builder().url(urlString)
        if (!etag.isNullOrBlank()) {
            requestBuilder.header("If-None-Match", etag)
        }
        if (!lastModified.isNullOrBlank()) {
            requestBuilder.header("If-Modified-Since", lastModified)
        }

        okHttpClient.newCall(requestBuilder.build()).await().use { response ->
            if (response.code == 304) {
                block(RssFetchResult.NotModified)
            } else if (response.isSuccessful) {
                val newEtag = response.header("ETag")
                val newLastModified = response.header("Last-Modified")
                block(RssFetchResult.Success(response.body.byteStream(), newEtag, newLastModified))
            } else {
                throw IOException("Unexpected code $response")
            }
        }
    }

    suspend fun <T> withRssStream(urlString: String, block: suspend (InputStream) -> T): T =
        withRssStreamConditional(urlString) { result ->
            when (result) {
                is RssFetchResult.Success -> block(result.stream)
                // This branch should be unreachable: withRssStream sends no conditional headers,
                // so a server should never respond with 304. Treat it as a programming error.
                is RssFetchResult.NotModified -> throw IllegalStateException("Received 304 Not Modified on an unconditional request to $urlString")
            }
        }
}

/**
 * Extension function to await the result of an OkHttp [Call] in a coroutine.
 */
internal suspend fun Call.await(): Response {
    return suspendCancellableCoroutine { continuation ->
        enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) {
                // If the coroutine was already cancelled, resume is a no-op and the caller's
                // `use {}` never runs — close the response here to avoid leaking the connection.
                continuation.resume(response) { _, _, _ -> response.close() }
            }

            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isCancelled) return
                continuation.resumeWithException(e)
            }
        })

        continuation.invokeOnCancellation {
            cancel()
        }
    }
}
