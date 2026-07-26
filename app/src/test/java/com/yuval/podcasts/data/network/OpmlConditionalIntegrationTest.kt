package com.yuval.podcasts.data.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.FileInputStream
import java.util.concurrent.TimeUnit
import javax.xml.parsers.DocumentBuilderFactory

class OpmlConditionalIntegrationTest {

    @Test
    fun testOpmlSubscriptionsWithProductionPodcastApi() = runTest {
        val opmlFile = File("/home/yuval/podcasts/podcasts.opml (5)")
        assertTrue("OPML file must exist", opmlFile.exists())

        // Parse URLs from OPML using standard XML parser
        val factory = DocumentBuilderFactory.newInstance()
        val builder = factory.newDocumentBuilder()
        val doc = builder.parse(FileInputStream(opmlFile))
        val nodes = doc.getElementsByTagName("outline")

        val feeds = mutableListOf<Pair<String, String>>()
        for (i in 0 until nodes.length) {
            val node = nodes.item(i)
            val xmlUrl = node.attributes?.getNamedItem("xmlUrl")?.nodeValue
            val title = node.attributes?.getNamedItem("text")?.nodeValue ?: "Unknown"
            if (!xmlUrl.isNullOrBlank()) {
                feeds.add(title to xmlUrl)
            }
        }

        println("Found ${feeds.size} subscription feeds in OPML.\n")

        val client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
        val podcastApi = PodcastApi(client, Dispatchers.IO)

        var totalChecked = 0
        var count304 = 0
        var count200 = 0
        var countErrors = 0

        for ((title, feedUrl) in feeds) {
            totalChecked++
            try {
                // 1. Initial Request (Fetch ETag / Last-Modified via production PodcastApi)
                var etag: String? = null
                var lastModified: String? = null

                podcastApi.withRssStreamConditional(feedUrl) { result ->
                    if (result is RssFetchResult.Success) {
                        etag = result.etag
                        lastModified = result.lastModified
                    }
                }

                // 2. Second Conditional Request using stored ETag / Last-Modified via PodcastApi
                var returned304 = false
                podcastApi.withRssStreamConditional(feedUrl, etag = etag, lastModified = lastModified) { condResult ->
                    when (condResult) {
                        is RssFetchResult.NotModified -> returned304 = true
                        is RssFetchResult.Success -> returned304 = false
                    }
                }

                if (returned304) {
                    count304++
                    println("✅ [304 Not Modified] $title (ETag: $etag, Last-Modified: $lastModified)")
                } else {
                    count200++
                    println("ℹ️ [200 OK Fallback]  $title (ETag: $etag, Last-Modified: $lastModified)")
                }
            } catch (e: Exception) {
                countErrors++
                println("⚠️ [ERROR]              $title: ${e.message}")
            }
        }

        println("\n==========================================")
        println("Production PodcastApi Kotlin Integration Results:")
        println("Total Feeds Tested: $totalChecked")
        println("Returned 304 Not Modified: $count304 (${String.format("%.1f", count304 * 100.0 / totalChecked)}%)")
        println("Returned 200 OK Fallback:  $count200")
        println("Errors:                    $countErrors")
        println("==========================================")

        assertTrue("At least 80% of feeds should support 304 Not Modified", count304 >= totalChecked * 0.8)
    }
}
