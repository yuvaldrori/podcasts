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
    suspend fun fetchRss(urlString: String): InputStream = withContext(ioDispatcher) {
        val request = Request.Builder()
            .url(urlString)
            .build()

        val response = okHttpClient.newCall(request).execute()
        if (!response.isSuccessful) {
            response.close()
            throw IOException("Unexpected code $response")
        }

        response.body.byteStream()
    }
}
