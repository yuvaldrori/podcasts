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
                  <outline text="My Favorite Podcast" type="rss" xmlUrl="https://example.com/feed.xml" htmlUrl="https://example.com" />
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
