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
        val parsed = parser.parse(inputStream, "http://test.com")
        
        assertEquals("Test Podcast", parsed.podcast.title)
        assertEquals(1, parsed.episodes.size)
        assertEquals("ep1", parsed.episodes[0].episode.id)
        assertEquals(3900L, parsed.episodes[0].episode.duration)
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
        val parsed = parser.parse(inputStream, "http://test.com")
        
        assertEquals("http://test.com/podcast.jpg", parsed.podcast.imageUrl)
        assertEquals(1, parsed.episodes.size)
        assertEquals("http://test.com/episode1.jpg", parsed.episodes[0].episode.imageUrl)
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
        val parsed = parser.parse(inputStream, "http://test.com")
        
        assertEquals("http://test.com/podcast_std.jpg", parsed.podcast.imageUrl)
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
        val parsed = parser.parse(inputStream, "http://test.com")
        
        // Since itunes:image usually preferred if present
        assertEquals("http://test.com/itunes.jpg", parsed.podcast.imageUrl)
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
        val parsed = parser.parse(inputStream, "http://test.com")
        
        assertEquals(2, parsed.episodes.size)
        assertEquals(0L, parsed.episodes[0].episode.duration)
        assertEquals(754L, parsed.episodes[1].episode.duration)
    }

    @Test
    fun parse_withSchemelessUrls_fixesThem() {
        val xml = """
            <rss version="2.0" xmlns:itunes="http://www.itunes.com/dtds/podcast-1.0.dtd">
                <channel>
                    <title>Schemeless Test</title>
                    <itunes:image href="pbcdn1.podbean.com/imglogo/cover.jpg" />
                    <item>
                        <title>Episode 1</title>
                        <enclosure url="epgb.podbean.com/907ade4f.mp3" length="1234" type="audio/mpeg" />
                        <itunes:image href="//epgb.podbean.com/img.jpg" />
                        <guid>ep1</guid>
                    </item>
                </channel>
            </rss>
        """.trimIndent()
        
        val inputStream = ByteArrayInputStream(xml.toByteArray())
        val parsed = parser.parse(inputStream, "http://test.com")
        
        assertEquals("https://pbcdn1.podbean.com/imglogo/cover.jpg", parsed.podcast.imageUrl)
        assertEquals("https://epgb.podbean.com/907ade4f.mp3", parsed.episodes[0].episode.audioUrl)
        assertEquals("https://epgb.podbean.com/img.jpg", parsed.episodes[0].episode.imageUrl)
    }

    @Test
    fun parse_withCDataAndEntities_extractsFullText() {
        val xml = """
            <rss version="2.0">
                <channel>
                    <title><![CDATA[CDATA Title]]></title>
                    <description>Description with &amp; entity</description>
                    <item>
                        <title>Split<![CDATA[ CDATA]]> Title</title>
                        <description><![CDATA[Multiple ]]>events<![CDATA[ merged]]></description>
                        <guid>ep1</guid>
                    </item>
                </channel>
            </rss>
        """.trimIndent()
        
        val inputStream = ByteArrayInputStream(xml.toByteArray())
        val parsed = parser.parse(inputStream, "http://test.com")
        
        assertEquals("CDATA Title", parsed.podcast.title)
        assertEquals("Description with & entity", parsed.podcast.description)
        assertEquals("Split CDATA Title", parsed.episodes[0].episode.title)
        assertEquals("Multiple events merged", parsed.episodes[0].episode.description)
    }
}
