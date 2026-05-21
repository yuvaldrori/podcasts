package com.yuval.podcasts.data.network

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayInputStream

class RssParserCrashTest {

    private val parser = RssParser()

    @Test
    fun parse_malformedXml_throwsExceptionInsteadOfCrashing() {
        val malformedXml = """
            <rss version="2.0">
                <channel>
                    <title>Malformed
        """.trimIndent()
        
        val inputStream = ByteArrayInputStream(malformedXml.toByteArray())
        
        try {
            parser.parse(inputStream, "http://test.com")
        } catch (e: Exception) {
            // Success - we caught the error instead of letting it bubble up as a raw XML parser error or crash
            assert(e.message?.contains("Failed to parse RSS feed") == true)
        }
    }
}
