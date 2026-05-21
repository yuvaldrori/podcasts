package com.yuval.podcasts.data.network

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import com.yuval.podcasts.data.db.entity.NetworkEpisode
import com.yuval.podcasts.data.db.entity.Podcast
import com.yuval.podcasts.data.db.entity.Chapter
import com.yuval.podcasts.data.db.entity.NetworkEpisodeWithChapters
import com.yuval.podcasts.data.db.entity.ParsedPodcast
import com.yuval.podcasts.data.Constants
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@Singleton
class RssParser @Inject constructor() {
    private val dateFormatter = java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME

    fun parse(inputStream: InputStream, feedUrl: String): ParsedPodcast = try {
        val factory = XmlPullParserFactory.newInstance()
        factory.isNamespaceAware = true
        // Harden against XXE vulnerabilities
        try {
            factory.setFeature("http://xmlpull.org/v1/doc/features.html#process-docdecl", false)
        } catch (e: Exception) {
            // Some parsers might not support this feature, but we try anyway for security
        }
        val parser = factory.newPullParser()
        parser.setInput(inputStream, null)

        var podcastTitle = ""
        var podcastDescription = ""
        var podcastImageUrl = ""
        var podcastWebsite = ""
        val episodes = mutableListOf<NetworkEpisodeWithChapters>()

        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType != XmlPullParser.START_TAG) continue
            
            val ns = parser.namespace
            val name = parser.name

            when {
                name == Constants.Rss.TITLE && ns.isEmpty() -> {
                    if (podcastTitle.isEmpty()) podcastTitle = readText(parser)
                }
                name == Constants.Rss.DESCRIPTION && ns.isEmpty() -> {
                    if (podcastDescription.isEmpty()) podcastDescription = readText(parser)
                }
                name == Constants.Rss.LINK && ns.isEmpty() -> {
                    if (podcastWebsite.isEmpty()) podcastWebsite = readText(parser)
                }
                name == Constants.Rss.IMAGE && ns.isEmpty() -> {
                    // Standard RSS <image><url>...</url></image>
                    val stdUrl = readPodcastImage(parser)
                    if (podcastImageUrl.isEmpty()) podcastImageUrl = stdUrl
                }
                name == Constants.Rss.IMAGE && ns?.contains("itunes") == true -> {
                    // <itunes:image href="..." />
                    val rawUrl = parser.getAttributeValue(null, Constants.Rss.IMAGE_HREF) ?: ""
                    val itunesUrl = sanitizeUrl(rawUrl, feedUrl)
                    if (itunesUrl.isNotEmpty()) {
                        podcastImageUrl = itunesUrl
                    }
                }
                name == Constants.Rss.ITEM -> {
                    readItem(parser, feedUrl)?.let { episodes.add(it) }
                }
            }
        }

        val podcast = Podcast(
            feedUrl = feedUrl,
            title = podcastTitle,
            description = podcastDescription,
            imageUrl = podcastImageUrl,
            website = podcastWebsite
        )
        ParsedPodcast(podcast, episodes)
    } catch (e: Exception) {
        throw Exception("Failed to parse RSS feed: ${e.message}", e)
    }

    private fun sanitizeUrl(url: String, baseUrl: String): String {
        return when {
            url.isBlank() -> ""
            url.startsWith("http") -> url
            url.startsWith("//") -> "https:$url"
            // If it looks like a schemeless absolute URL (e.g. "example.com/image.jpg")
            // We assume it's absolute if it contains a dot before any slash
            url.contains(".") && (!url.contains("/") || url.indexOf(".") < url.indexOf("/")) -> "https://$url"
            else -> try {
                java.net.URI(baseUrl).resolve(url).toString()
            } catch (e: Exception) {
                ""
            }
        }
    }

    private fun readPodcastImage(parser: XmlPullParser): String {
        var imageUrl = ""
        parser.require(XmlPullParser.START_TAG, null, Constants.Rss.IMAGE)
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.eventType != XmlPullParser.START_TAG) continue
            when (parser.name) {
                Constants.Rss.URL -> imageUrl = readText(parser)
                else -> skip(parser)
            }
        }
        return imageUrl
    }

    private fun readItem(parser: XmlPullParser, feedUrl: String): NetworkEpisodeWithChapters? {
        parser.require(XmlPullParser.START_TAG, null, Constants.Rss.ITEM)
        var id = ""
        var title = ""
        var description = ""
        var episodeWebLink = ""
        var audioUrl = ""
        var imageUrl: String? = null
        var pubDate = 0L
        var duration = 0L
        val chapters = mutableListOf<Chapter>()

        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.eventType != XmlPullParser.START_TAG) continue
            val ns = parser.namespace
            val name = parser.name
            
            when {
                name == Constants.Rss.TITLE -> title = readText(parser)
                name == Constants.Rss.DESCRIPTION -> description = readText(parser)
                name == Constants.Rss.LINK -> episodeWebLink = readText(parser)
                name == Constants.Rss.GUID -> id = readText(parser)
                name == Constants.Rss.PUB_DATE -> {
                    val dateStr = readText(parser)
                    pubDate = try {
                        java.time.ZonedDateTime.parse(dateStr, dateFormatter).toInstant().toEpochMilli()
                    } catch (e: Exception) {
                        0L
                    }
                }
                name == Constants.Rss.ENCLOSURE -> {
                    val rawUrl = parser.getAttributeValue(null, Constants.Rss.ENCLOSURE_URL) ?: ""
                    audioUrl = sanitizeUrl(rawUrl, feedUrl)
                    skip(parser)
                }
                name == Constants.Rss.DURATION -> {
                    duration = parseDuration(readText(parser))
                }
                name == Constants.Rss.IMAGE && ns?.contains("itunes") == true -> {
                    val rawImg = parser.getAttributeValue(null, Constants.Rss.IMAGE_HREF)
                    imageUrl = rawImg?.let { sanitizeUrl(it, feedUrl) }
                    skip(parser)
                }
                name == Constants.Rss.CHAPTERS -> {
                    chapters.addAll(readChapters(parser))
                }
                else -> skip(parser)
            }
        }

        if (id.isEmpty()) id = audioUrl
        if (id.isEmpty()) {
            // Last resort: hash of title and date to avoid collision
            id = java.util.UUID.nameUUIDFromBytes((title + pubDate).toByteArray()).toString()
        }

        val episode = NetworkEpisode(
            id = id,
            podcastFeedUrl = feedUrl,
            title = title,
            description = description,
            audioUrl = audioUrl,
            imageUrl = imageUrl,
            episodeWebLink = if (episodeWebLink.isEmpty()) null else episodeWebLink,
            pubDate = pubDate,
            duration = duration
        )
        
        return NetworkEpisodeWithChapters(episode, chapters)
    }

    private fun readChapters(parser: XmlPullParser): List<Chapter> {
        val chapters = mutableListOf<Chapter>()
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.eventType != XmlPullParser.START_TAG) continue
            if (parser.name == Constants.Rss.CHAPTER) {
                val start = parser.getAttributeValue(null, Constants.Rss.CHAPTER_START) ?: "0"
                val title = parser.getAttributeValue(null, Constants.Rss.CHAPTER_TITLE) ?: ""
                chapters.add(Chapter(
                    episodeId = "", // Filled by caller/Dao
                    title = title,
                    startTimeMs = parseDuration(start).seconds.inWholeMilliseconds,
                    imageUrl = parser.getAttributeValue(null, Constants.Rss.CHAPTER_IMAGE),
                    url = parser.getAttributeValue(null, Constants.Rss.CHAPTER_HREF)
                ))
                skip(parser)
            } else {
                skip(parser)
            }
        }
        return chapters
    }

    private fun readText(parser: XmlPullParser): String {
        val result = StringBuilder()
        while (parser.next() == XmlPullParser.TEXT || parser.eventType == XmlPullParser.CDSECT) {
            result.append(parser.text)
        }
        // After reading all text events, we should be at END_TAG
        return result.toString().trim()
    }

    private fun skip(parser: XmlPullParser) {
        if (parser.eventType != XmlPullParser.START_TAG) {
            throw IllegalStateException()
        }
        var depth = 1
        while (depth != 0) {
            when (parser.next()) {
                XmlPullParser.END_TAG -> depth--
                XmlPullParser.START_TAG -> depth++
            }
        }
    }

    private fun parseDuration(durationStr: String?): Long {
        if (durationStr.isNullOrBlank()) return 0L
        return try {
            val parts = durationStr.split(":")
            if (parts.size > 3) return 0L // Invalid format
            
            var totalDuration = Duration.ZERO
            for (part in parts) {
                val value = part.toDoubleOrNull()?.toLong() ?: 0L
                totalDuration = (totalDuration * 60) + value.seconds
            }
            totalDuration.inWholeSeconds
        } catch (e: Exception) {
            0L
        }
    }
}
