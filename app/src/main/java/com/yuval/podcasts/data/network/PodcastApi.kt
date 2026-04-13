package com.yuval.podcasts.data.network

import com.yuval.podcasts.data.Constants
import com.yuval.podcasts.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Singleton
class PodcastApi @Inject constructor(
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {
    suspend fun fetchRss(urlString: String): InputStream = withContext(ioDispatcher) {
        suspendCancellableCoroutine { continuation ->
            var connection: HttpURLConnection? = null
            try {
                val url = URL(urlString)
                connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = Constants.NETWORK_TIMEOUT_MS
                connection.readTimeout = Constants.NETWORK_TIMEOUT_MS

                // Register cancellation handler to disconnect network socket early
                continuation.invokeOnCancellation {
                    connection?.disconnect()
                }

                connection.connect()

                if (connection.responseCode in 200..299) {
                    continuation.resume(connection.inputStream)
                } else {
                    continuation.resumeWithException(Exception("Failed to fetch RSS. Response code: ${connection.responseCode}"))
                }
            } catch (e: Exception) {
                if (continuation.isActive) {
                    continuation.resumeWithException(e)
                }
            }
        }
    }
}
