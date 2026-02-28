package com.yuval.podcasts.data.network

import org.junit.Test
import java.io.ByteArrayInputStream

class RssParserCrashTest {
    @Test
    fun testCrash() {
        val xml = """
            <rss>
                <channel>
                    <item>
                        <title>Test</title>
                        <enclosure url="test.mp3" length="0" type="audio/mpeg" />
                        <description>Hello</description>
                    </item>
                </channel>
            </rss>
        """.trimIndent()
        RssParser().parse(ByteArrayInputStream(xml.toByteArray()), "test")
    }
}
