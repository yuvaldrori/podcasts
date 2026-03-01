package com.yuval.podcasts.data.network

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayInputStream

class RssParserCrashTest {

    private val parser = RssParser()

    @Test
    fun parse_invalidDurationString_throwsNumberFormatException() {
        val invalidDurationXml = """
            <rss version="2.0">
                <channel>
                    <title>Broken Duration Podcast</title>
                    <description>A podcast for testing broken duration</description>
                    <item>
                        <title>Episode 2</title>
                        <guid>ep2</guid>
                        <itunes:duration>invalid_string</itunes:duration>
                    </item>
                </channel>
            </rss>
        """.trimIndent()
        
        val inputStream = ByteArrayInputStream(invalidDurationXml.toByteArray())
        
        // This will crash if the code is not fixed, verifying our test fails first
        parser.parse(inputStream, "http://test.com")
    }
}
