package com.yuval.podcasts.data.network

import org.junit.Assert.assertEquals
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
}