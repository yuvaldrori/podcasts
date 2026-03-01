package com.yuval.podcasts.data.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

class RssParserTest {

    private val parser = RssParser()

    @Test
    fun `parse valid rss returns podcast and episodes`() {
        val rss = """
            <rss version="2.0">
                <channel>
                    <title>Test Podcast</title>
                    <description>Test Description</description>
                    <link>https://example.com</link>
                    <image>
                        <url>https://example.com/image.jpg</url>
                    </image>
                    <item>
                        <title>Episode 1</title>
                        <description>Description 1</description>
                        <guid>guid1</guid>
                        <pubDate>Wed, 25 Feb 2026 12:00:00 +0000</pubDate>
                        <enclosure url="https://example.com/audio1.mp3" length="1000" type="audio/mpeg" />
                    </item>
                </channel>
            </rss>
        """.trimIndent()

        val (podcast, episodes) = parser.parse(ByteArrayInputStream(rss.toByteArray()), "https://example.com/rss")

        assertEquals("Test Podcast", podcast.title)
        assertEquals("https://example.com/rss", podcast.feedUrl)
        assertEquals(1, episodes.size)
        assertEquals("Episode 1", episodes[0].title)
        assertEquals("https://example.com/audio1.mp3", episodes[0].audioUrl)
    }

    @Test
    fun `concurrent parsing maintains thread safety for dates`() = runBlocking {
        val rss = """
            <rss version="2.0">
                <channel>
                    <title>Concurrent Test</title>
                    <item>
                        <title>Ep1</title>
                        <guid>guid1</guid>
                        <pubDate>Wed, 25 Feb 2026 12:00:00 +0000</pubDate>
                    </item>
                </channel>
            </rss>
        """.trimIndent()

        // Spawn 100 parallel threads attempting to parse the exact same date string
        // using the single shared RssParser instance.
        val deferreds = (1..100).map {
            async(Dispatchers.Default) {
                parser.parse(ByteArrayInputStream(rss.toByteArray()), "url")
            }
        }
        
        val results = deferreds.awaitAll()
        
        // Grab the Unix timestamp from the very first thread's result
        val firstTimestamp = results.first().second.first().pubDate
        assertTrue("Timestamp should be greater than 0", firstTimestamp > 0L)
        
        // Assert that ALL 100 threads calculated the exact same Unix timestamp.
        // If SimpleDateFormat was not thread-local, calendar corruption would cause
        // random threads to output different (or 0) timestamps.
        results.forEach { result ->
            val timestamp = result.second.first().pubDate
            assertEquals("Concurrent parsing corrupted the date output", firstTimestamp, timestamp)
        }
    }
}