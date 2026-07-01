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

@Singleton
class PodcastApi @Inject constructor(
    private val okHttpClient: OkHttpClient,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {
    suspend fun <T> withRssStream(urlString: String, block: (InputStream) -> T): T = withContext(ioDispatcher) {
        val request = Request.Builder()
            .url(urlString)
            .build()

        okHttpClient.newCall(request).await().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Unexpected code $response")
            }
            block(response.body.byteStream())
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
