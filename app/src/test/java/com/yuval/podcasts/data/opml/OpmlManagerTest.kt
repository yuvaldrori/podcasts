package com.yuval.podcasts.data.opml

import com.yuval.podcasts.data.db.entity.Podcast
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

@RunWith(RobolectricTestRunner::class)
class OpmlManagerTest {

    private val opmlManager = OpmlManager()

    @Test
    fun parse_validOpml_returnsUrls() {
        val opmlContent = """
            <?xml version="1.0" encoding="UTF-8"?>
            <opml version="2.0">
              <head>
                <title>Podcasts</title>
              </head>
              <body>
                <outline text="Podcasts">
                  <outline text="My Favorite Podcast" type="rss" xmlUrl="http://example.com/feed.xml" htmlUrl="http://example.com" />
                  <outline text="Another Podcast" type="rss" xmlUrl="https://test.com/rss" htmlUrl="https://test.com" />
                </outline>
              </body>
            </opml>
        """.trimIndent()
        val inputStream = ByteArrayInputStream(opmlContent.toByteArray())

        val urls = opmlManager.parse(inputStream)

        assertEquals(2, urls.size)
        assertEquals("https://example.com/feed.xml", urls[0])
        assertEquals("https://test.com/rss", urls[1])
    }

    @Test
    fun parse_withInvalidSchemes_filtersThem() {
        val opmlContent = """
            <?xml version="1.0" encoding="UTF-8"?>
            <opml version="2.0">
              <body>
                <outline text="Podcasts">
                  <outline text="Safe" type="rss" xmlUrl="https://example.com/feed.xml" />
                  <outline text="Malicious" type="rss" xmlUrl="javascript:alert('evil')" />
                  <outline text="File" type="rss" xmlUrl="file:///etc/passwd" />
                </outline>
              </body>
            </opml>
        """.trimIndent()
        val inputStream = ByteArrayInputStream(opmlContent.toByteArray())

        val urls = opmlManager.parse(inputStream)

        assertEquals(1, urls.size)
        assertEquals("https://example.com/feed.xml", urls[0])
    }

    @Test
    fun parse_malformedOpml_doesNotThrowAndReturnsPartialResults() {
        // Valid opening with one good subscription, then truncated/garbage XML.
        val opmlContent = """
            <?xml version="1.0" encoding="UTF-8"?>
            <opml version="2.0">
              <body>
                <outline text="Good" type="rss" xmlUrl="https://example.com/feed.xml" />
                <outline text="Broken" type="rss" xmlUrl="https://broken.com/feed
        """.trimIndent()
        val inputStream = ByteArrayInputStream(opmlContent.toByteArray())

        // Must not throw on malformed XML; the already-parsed subscription is returned.
        val urls = opmlManager.parse(inputStream)

        assertEquals(listOf("https://example.com/feed.xml"), urls)
    }

    @Test
    fun parse_withDoctypeEntity_doesNotCrash() {
        // An OPML carrying a DOCTYPE/entity declaration must not crash the parser
        // (DOCTYPE processing is disabled for XXE hardening).
        val opmlContent = """
            <?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE opml [ <!ENTITY foo "bar"> ]>
            <opml version="2.0">
              <body>
                <outline text="Safe" type="rss" xmlUrl="https://example.com/feed.xml" />
              </body>
            </opml>
        """.trimIndent()
        val inputStream = ByteArrayInputStream(opmlContent.toByteArray())

        val urls = opmlManager.parse(inputStream)

        assertEquals(listOf("https://example.com/feed.xml"), urls)
    }

    @Test
    fun export_validPodcasts_returnsCorrectOpml() {
        val podcasts = listOf(
            Podcast(
                feedUrl = "https://example.com/feed.xml",
                title = "My Favorite Podcast",
                description = "A great podcast",
                imageUrl = "https://example.com/image.jpg",
                website = "https://example.com"
            )
        )
        val outputStream = ByteArrayOutputStream()

        opmlManager.export(podcasts, outputStream)

        val exportedContent = outputStream.toString("UTF-8")
        
        // Simple string checks
        assert(exportedContent.contains("""xmlUrl="https://example.com/feed.xml""""))
        assert(exportedContent.contains("""text="My Favorite Podcast""""))
        assert(exportedContent.contains("""htmlUrl="https://example.com""""))
        assert(exportedContent.contains("<opml version=\"2.0\">"))
    }
}
