package com.yuval.podcasts.data.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PodcastApi @Inject constructor() {
    suspend fun fetchRss(urlString: String): InputStream = withContext(Dispatchers.IO) {
        val url = URL(urlString)
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 15000
        connection.readTimeout = 15000
        connection.connect()

        if (connection.responseCode in 200..299) {
            connection.inputStream
        } else {
            throw Exception("Failed to fetch RSS. Response code: ${connection.responseCode}")
        }
    }
}
