package com.yuval.podcasts.data.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
        
        // This should not crash, it should just map the duration to 0
        val (podcast, episodes) = parser.parse(inputStream, "http://test.com")
        
        assertEquals("Broken Duration Podcast", podcast.title)
        assertEquals(2, episodes.size)
        assertEquals("ep2", episodes[0].episode.id)
        assertEquals(0L, episodes[0].episode.duration) // Should default to 0 on "invalid_string"
        assertEquals("ep3", episodes[1].episode.id)
        assertEquals(754L, episodes[1].episode.duration) // Should parse 12:34.5 as 754s
    }
}
