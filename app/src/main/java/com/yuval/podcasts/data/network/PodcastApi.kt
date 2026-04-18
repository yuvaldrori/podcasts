package com.yuval.podcasts.data.network

import com.yuval.podcasts.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.suspendCancellableCoroutine
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
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Singleton
class PodcastApi @Inject constructor(
    private val okHttpClient: OkHttpClient,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {
    suspend fun fetchRss(urlString: String): InputStream = withContext(ioDispatcher) {
        val request = Request.Builder()
            .url(urlString)
            .build()

        suspendCancellableCoroutine { continuation ->
            val call = okHttpClient.newCall(request)
            
            continuation.invokeOnCancellation {
                call.cancel()
            }

            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) {
                        continuation.resumeWithException(e)
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    if (!response.isSuccessful) {
                        continuation.resumeWithException(IOException("Unexpected code $response"))
                        return
                    }

                    val body = response.body
                    if (body == null) {
                        continuation.resumeWithException(IOException("Empty response body"))
                        return
                    }

                    continuation.resume(body.byteStream())
                }
            })
        }
    }
}
