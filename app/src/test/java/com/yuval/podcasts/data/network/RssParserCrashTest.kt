package com.yuval.podcasts.data.network

import org.junit.Assert.fail
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

class RssParserCrashTest {

    private val parser = RssParser()

    /**
     * When the RSS feed XML is malformed (unclosed tag), the parser must throw an exception
     * wrapping the XML parse error — not silently return partial data or crash with an
     * unchecked platform exception.
     */
    @Test
    fun parse_malformedXml_throwsExceptionInsteadOfCrashing() {
        val malformedXml = """
            <rss version="2.0">
                <channel>
                    <title>Malformed</channel>
                </title>
            </rss>
        """.trimIndent()

        val inputStream = ByteArrayInputStream(malformedXml.toByteArray())

        try {
            parser.parse(inputStream, "http://test.com")
            fail("Expected an exception for malformed XML, but parse() returned normally")
        } catch (e: Exception) {
            // Success — we caught the error instead of letting it crash
            assertTrue(
                "Exception message should mention parse failure, got: ${e.message}",
                e.message?.contains("Failed to parse RSS feed") == true
            )
        }
    }
}
