package com.yuval.podcasts.data.network

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayInputStream

class RssParserTest {

    private val parser = RssParser()

    @Test
    fun parse_validFeed_returnsPodcastAndEpisodes() {
        val validXml = """
            <rss version="2.0" xmlns:itunes="http://www.itunes.com/dtds/podcast-1.0.dtd">
                <channel>
                    <title>Test Podcast</title>
                    <description>A podcast for testing</description>
                    <item>
                        <title>Episode 1</title>
                        <guid>ep1</guid>
                        <itunes:duration>01:05:00</itunes:duration>
                    </item>
                </channel>
            </rss>
        """.trimIndent()
        
        val inputStream = ByteArrayInputStream(validXml.toByteArray())
        val (podcast, episodes) = parser.parse(inputStream, "http://test.com")
        
        assertEquals("Test Podcast", podcast.title)
        assertEquals(1, episodes.size)
        assertEquals("ep1", episodes[0].episode.id)
        assertEquals(3900L, episodes[0].episode.duration)
    }

    @Test
    fun parse_withItunesImages_extractsUrlsCorrectly() {
        val xml = """
            <rss version="2.0" xmlns:itunes="http://www.itunes.com/dtds/podcast-1.0.dtd">
                <channel>
                    <title>iTunes Image Test</title>
                    <itunes:image href="http://test.com/podcast.jpg" />
                    <item>
                        <title>Episode 1</title>
                        <itunes:image href="http://test.com/episode1.jpg" />
                        <guid>ep1</guid>
                    </item>
                </channel>
            </rss>
        """.trimIndent()
        
        val inputStream = ByteArrayInputStream(xml.toByteArray())
        val (podcast, episodes) = parser.parse(inputStream, "http://test.com")
        
        assertEquals("http://test.com/podcast.jpg", podcast.imageUrl)
        assertEquals(1, episodes.size)
        assertEquals("http://test.com/episode1.jpg", episodes[0].episode.imageUrl)
    }

    @Test
    fun parse_withStandardRssImages_extractsUrlsCorrectly() {
        val xml = """
            <rss version="2.0">
                <channel>
                    <title>Standard Image Test</title>
                    <image>
                        <url>http://test.com/podcast_std.jpg</url>
                        <title>Standard Image Test</title>
                        <link>http://test.com</link>
                    </image>
                    <item>
                        <title>Episode 1</title>
                        <guid>ep1</guid>
                    </item>
                </channel>
            </rss>
        """.trimIndent()
        
        val inputStream = ByteArrayInputStream(xml.toByteArray())
        val (podcast, _) = parser.parse(inputStream, "http://test.com")
        
        assertEquals("http://test.com/podcast_std.jpg", podcast.imageUrl)
    }

    @Test
    fun parse_withMixedImageTags_prefersItunesImage() {
        // This tests the logic order in the parser
        val xml = """
            <rss version="2.0" xmlns:itunes="http://www.itunes.com/dtds/podcast-1.0.dtd">
                <channel>
                    <title>Mixed Image Test</title>
                    <image>
                        <url>http://test.com/standard.jpg</url>
                    </image>
                    <itunes:image href="http://test.com/itunes.jpg" />
                    <item>
                        <title>Episode 1</title>
                        <guid>ep1</guid>
                    </item>
                </channel>
            </rss>
        """.trimIndent()
        
        val inputStream = ByteArrayInputStream(xml.toByteArray())
        val (podcast, _) = parser.parse(inputStream, "http://test.com")
        
        // Since itunes:image comes after in this XML, and both are named "image" in namespace mode,
        // the last one found might win, or the one with href wins.
        assertEquals("http://test.com/itunes.jpg", podcast.imageUrl)
    }

    @Test
    fun parse_invalidDurationString_doesNotCrash_defaultsToZero() {
        val invalidDurationXml = """
            <rss version="2.0" xmlns:itunes="http://www.itunes.com/dtds/podcast-1.0.dtd">
                <channel>
                    <title>Broken Duration Podcast</title>
                    <description>A podcast for testing broken duration</description>
                    <item>
                        <title>Episode 2</title>
                        <guid>ep2</guid>
                        <itunes:duration>invalid_string</itunes:duration>
                    </item>
                    <item>
                        <title>Episode 3</title>
                        <guid>ep3</guid>
                        <itunes:duration>12:34.5</itunes:duration>
                    </item>
                </channel>
            </rss>
        """.trimIndent()
        
        val inputStream = ByteArrayInputStream(invalidDurationXml.toByteArray())
        val (_, episodes) = parser.parse(inputStream, "http://test.com")
        
        assertEquals(2, episodes.size)
        assertEquals(0L, episodes[0].episode.duration)
        assertEquals(754L, episodes[1].episode.duration)
    }
}
