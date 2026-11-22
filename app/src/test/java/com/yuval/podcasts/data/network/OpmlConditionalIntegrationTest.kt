package com.yuval.podcasts.data.network

import com.yuval.podcasts.data.opml.OpmlManager
import com.yuval.podcasts.ui.utils.HtmlUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayInputStream

@RunWith(RobolectricTestRunner::class)
class OpmlConditionalIntegrationTest {

    private val sampleOpml = """
        <?xml version="1.0" encoding="UTF-8"?>
        <opml version="2.0">
          <body>
            <outline text="Feed With ETag" type="rss" xmlUrl="https://feeds.test/etag.xml" />
            <outline text="Feed With Last-Modified" type="rss" xmlUrl="https://feeds.test/lastmod.xml" />
            <outline text="Feed Without Caching" type="rss" xmlUrl="https://feeds.test/nocache.xml" />
          </body>
        </opml>
    """.trimIndent()

    private val sampleFeeds = mapOf(
        "https://feeds.test/etag.xml" to """
            <rss version="2.0" xmlns:psc="http://podlove.org/simple-chapters">
              <channel>
                <title>The &lt;b&gt;ETag&lt;/b&gt; &amp;amp; Show</title>
                <description>&lt;p&gt;Clean &lt;i&gt;channel&lt;/i&gt;&amp;nbsp;summary&lt;/p&gt;</description>
                <item>
                  <title>Episode &lt;em&gt;1&lt;/em&gt;</title>
                  <description>&lt;p&gt;Episode &lt;a href="https://example.com"&gt;link&lt;/a&gt;&lt;/p&gt;</description>
                  <guid>etag-ep-1</guid>
                  <enclosure url="https://feeds.test/ep1.mp3" type="audio/mpeg" />
                  <psc:chapters version="1.2">
                    <psc:chapter start="00:00:00" title="&lt;b&gt;Intro&lt;/b&gt;" />
                  </psc:chapters>
                </item>
              </channel>
            </rss>
        """.trimIndent(),
        "https://feeds.test/lastmod.xml" to """
            <rss version="2.0">
              <channel>
                <title>LastMod Show</title>
                <description>Plain &amp; clean description</description>
                <item>
                  <title>LastMod Ep</title>
                  <description>&lt;div&gt;Shownotes&amp;nbsp;here&lt;/div&gt;</description>
                  <guid>lm-ep-1</guid>
                  <enclosure url="https://feeds.test/ep2.mp3" type="audio/mpeg" />
                </item>
              </channel>
            </rss>
        """.trimIndent(),
        "https://feeds.test/nocache.xml" to """
            <rss version="2.0">
              <channel>
                <title>NoCache Show</title>
                <description>Always fresh</description>
                <item>
                  <title>Fresh Ep</title>
                  <description>Simple text</description>
                  <guid>nc-ep-1</guid>
                  <enclosure url="https://feeds.test/ep3.mp3" type="audio/mpeg" />
                </item>
              </channel>
            </rss>
        """.trimIndent()
    )

    private fun buildLocalClient(): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val request = chain.request()
            val url = request.url.toString()
            val xml = sampleFeeds[url] ?: error("Unexpected URL: $url")

            when (url) {
                "https://feeds.test/etag.xml" -> {
                    val expectedEtag = "\"v1-etag\""
                    if (request.header("If-None-Match") == expectedEtag) {
                        Response.Builder()
                            .request(request).protocol(Protocol.HTTP_1_1)
                            .code(304).message("Not Modified")
                            .body("".toResponseBody(null)).build()
                    } else {
                        Response.Builder()
                            .request(request).protocol(Protocol.HTTP_1_1)
                            .code(200).message("OK")
                            .addHeader("ETag", expectedEtag)
                            .body(xml.toResponseBody("application/xml".toMediaType())).build()
                    }
                }
                "https://feeds.test/lastmod.xml" -> {
                    val expectedLm = "Wed, 21 Oct 2025 07:28:00 GMT"
                    if (request.header("If-Modified-Since") == expectedLm) {
                        Response.Builder()
                            .request(request).protocol(Protocol.HTTP_1_1)
                            .code(304).message("Not Modified")
                            .body("".toResponseBody(null)).build()
                    } else {
                        Response.Builder()
                            .request(request).protocol(Protocol.HTTP_1_1)
                            .code(200).message("OK")
                            .addHeader("Last-Modified", expectedLm)
                            .body(xml.toResponseBody("application/xml".toMediaType())).build()
                    }
                }
                else -> {
                    Response.Builder()
                        .request(request).protocol(Protocol.HTTP_1_1)
                        .code(200).message("OK")
                        .body(xml.toResponseBody("application/xml".toMediaType())).build()
                }
            }
        }
        .build()

    @Test
    fun opmlSubscriptions_conditionalRequestsReturn304WhenHeadersMatch() = runTest {
        val opmlManager = OpmlManager()
        val feedUrls = ByteArrayInputStream(sampleOpml.toByteArray()).use { opmlManager.parse(it) }
        assertEquals(3, feedUrls.size)

        val podcastApi = PodcastApi(buildLocalClient(), Dispatchers.Unconfined)
        var count304 = 0
        var count200Fallback = 0

        for (feedUrl in feedUrls) {
            var etag: String? = null
            var lastModified: String? = null

            podcastApi.withRssStreamConditional(feedUrl) { result ->
                assertTrue(result is RssFetchResult.Success)
                val success = result as RssFetchResult.Success
                etag = success.etag
                lastModified = success.lastModified
            }

            podcastApi.withRssStreamConditional(feedUrl, etag = etag, lastModified = lastModified) { condResult ->
                when (condResult) {
                    is RssFetchResult.NotModified -> count304++
                    is RssFetchResult.Success -> count200Fallback++
                }
            }
        }

        assertEquals(2, count304)
        assertEquals(1, count200Fallback)
    }

    @Test
    fun opmlFeeds_parsedWithSanitizedMetadataAndRawEpisodeDescriptionHtml() = runTest {
        val opmlManager = OpmlManager()
        val feedUrls = ByteArrayInputStream(sampleOpml.toByteArray()).use { opmlManager.parse(it) }
        assertEquals(3, feedUrls.size)

        val podcastApi = PodcastApi(buildLocalClient(), Dispatchers.Unconfined)
        val parser = RssParser()
        val htmlTagRegex = Regex("</?[a-zA-Z][a-zA-Z0-9]*\\b[^>]*>")

        var verifiedCount = 0
        for (feedUrl in feedUrls) {
            podcastApi.withRssStreamConditional(feedUrl) { result ->
                assertTrue(result is RssFetchResult.Success)
                val parsed = parser.parse((result as RssFetchResult.Success).stream, feedUrl)
                verifiedCount++

                assertFalse(htmlTagRegex.containsMatchIn(parsed.podcast.title))
                assertFalse(htmlTagRegex.containsMatchIn(parsed.podcast.description))
                assertFalse(parsed.podcast.description.contains("&nbsp;"))

                for (epWithChapters in parsed.episodes) {
                    assertFalse(htmlTagRegex.containsMatchIn(epWithChapters.episode.title))
                    val previewDesc = HtmlUtils.stripHtml(epWithChapters.episode.description)
                    assertFalse(htmlTagRegex.containsMatchIn(previewDesc))
                    for (ch in epWithChapters.chapters) {
                        assertFalse(htmlTagRegex.containsMatchIn(ch.title))
                    }
                }

                if (feedUrl == "https://feeds.test/etag.xml") {
                    assertEquals("The ETag & Show", parsed.podcast.title)
                    assertEquals("Clean channel summary", parsed.podcast.description)
                    // Episode description keeps HTML links for EpisodeDetailScreen
                    assertTrue(parsed.episodes[0].episode.description.contains("<a href=\"https://example.com\">link</a>"))
                    assertEquals("Intro", parsed.episodes[0].chapters[0].title)
                }
            }
        }

        assertEquals(3, verifiedCount)
    }
}
