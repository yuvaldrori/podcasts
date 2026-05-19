package com.yuval.podcasts.data.network

import com.yuval.podcasts.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PodcastApi @Inject constructor(
    private val okHttpClient: OkHttpClient,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {
    suspend fun <T> withRssStream(urlString: String, block: (InputStream) -> T): T = withContext(ioDispatcher) {
        val request = Request.Builder()
            .url(urlString)
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Unexpected code $response")
            }
            block(response.body.byteStream())
        }
    }
}
