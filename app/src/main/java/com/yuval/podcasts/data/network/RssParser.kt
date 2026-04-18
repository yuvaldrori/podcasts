package com.yuval.podcasts.data.network

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import com.yuval.podcasts.data.db.entity.NetworkEpisode
import com.yuval.podcasts.data.db.entity.Podcast
import com.yuval.podcasts.data.db.entity.Chapter
import com.yuval.podcasts.data.db.entity.NetworkEpisodeWithChapters
import com.yuval.podcasts.data.Constants
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RssParser @Inject constructor() {
    private val dateFormat = object : ThreadLocal<SimpleDateFormat>() {
        override fun initialValue() = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
    }

    fun parse(inputStream: InputStream, feedUrl: String): Pair<Podcast, List<NetworkEpisodeWithChapters>> {
        val factory = XmlPullParserFactory.newInstance()
        factory.isNamespaceAware = true
        val parser = factory.newPullParser()
        parser.setInput(inputStream, null)

        var podcastTitle = ""
        var podcastDescription = ""
        var podcastImageUrl = ""
        var podcastWebsite = ""
        val episodes = mutableListOf<NetworkEpisodeWithChapters>()

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG) {
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
                    name == Constants.Rss.IMAGE && ns.contains("itunes") -> {
                        // <itunes:image href="..." />
                        val itunesUrl = parser.getAttributeValue(null, Constants.Rss.IMAGE_HREF) ?: ""
                        podcastImageUrl = itunesUrl // itunes:image usually preferred if present
                        skip(parser)
                    }
                    name == Constants.Rss.ITEM -> {
                        episodes.add(readItem(parser, feedUrl))
                    }
                }
            }
            eventType = parser.next()
        }

        val podcast = Podcast(
            feedUrl = feedUrl,
            title = podcastTitle,
            description = podcastDescription,
            imageUrl = podcastImageUrl,
            website = podcastWebsite
        )
        return Pair(podcast, episodes)
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

    private fun readItem(parser: XmlPullParser, feedUrl: String): NetworkEpisodeWithChapters {
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
                        dateFormat.get()?.parse(dateStr)?.time ?: 0L
                    } catch (e: Exception) {
                        0L
                    }
                }
                name == Constants.Rss.ENCLOSURE -> {
                    audioUrl = parser.getAttributeValue(null, Constants.Rss.ENCLOSURE_URL) ?: ""
                    skip(parser)
                }
                name == Constants.Rss.DURATION -> {
                    duration = parseDuration(readText(parser))
                }
                name == Constants.Rss.IMAGE && ns.contains("itunes") -> {
                    imageUrl = parser.getAttributeValue(null, Constants.Rss.IMAGE_HREF)
                    skip(parser)
                }
                name == Constants.Rss.CHAPTERS -> {
                    chapters.addAll(readChapters(parser))
                }
                else -> skip(parser)
            }
        }

        if (id.isEmpty()) id = audioUrl

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
                    startTimeMs = parseDuration(start) * 1000L,
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
        var result = ""
        if (parser.next() == XmlPullParser.TEXT) {
            result = parser.text
            parser.nextTag()
        }
        return result
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
        if (durationStr == null) return 0L
        return try {
            val parts = durationStr.split(":")
            when (parts.size) {
                1 -> parts[0].toDouble().toLong()
                2 -> parts[0].toLong() * 60 + parts[1].toDouble().toLong()
                3 -> parts[0].toLong() * 3600 + parts[1].toLong() * 60 + parts[2].toDouble().toLong()
                else -> 0L
            }
        } catch (e: Exception) {
            0L
        }
    }
}
