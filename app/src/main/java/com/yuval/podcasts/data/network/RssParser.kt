package com.yuval.podcasts.data.network

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import com.yuval.podcasts.data.db.entity.Episode
import com.yuval.podcasts.data.db.entity.Podcast
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Locale

class RssParser {
    private val dateFormat = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.US)

    fun parse(inputStream: InputStream, feedUrl: String): Pair<Podcast, List<Episode>> {
        val factory = XmlPullParserFactory.newInstance()
        factory.isNamespaceAware = false
        val parser = factory.newPullParser()
        parser.setInput(inputStream, null)
        parser.nextTag()
        return readRss(parser, feedUrl)
    }

    private fun readRss(parser: XmlPullParser, feedUrl: String): Pair<Podcast, List<Episode>> {
        var podcastTitle = ""
        var podcastDescription = ""
        var podcastImageUrl = ""
        var podcastWebsite = ""
        val episodes = mutableListOf<Episode>()

        parser.require(XmlPullParser.START_TAG, null, "rss")
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.eventType != XmlPullParser.START_TAG) continue
            if (parser.name == "channel") {
                while (parser.next() != XmlPullParser.END_TAG) {
                    if (parser.eventType != XmlPullParser.START_TAG) continue
                    when (parser.name) {
                        "title" -> podcastTitle = readText(parser)
                        "description" -> podcastDescription = readText(parser)
                        "link" -> podcastWebsite = readText(parser)
                        "image" -> podcastImageUrl = readImage(parser)
                        "itunes:image" -> {
                            val href = parser.getAttributeValue(null, "href")
                            if (href != null) podcastImageUrl = href
                            skip(parser)
                        }
                        "item" -> episodes.add(readItem(parser, feedUrl))
                        else -> skip(parser)
                    }
                }
            } else {
                skip(parser)
            }
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

    private fun readItem(parser: XmlPullParser, feedUrl: String): Episode {
        var id = ""
        var title = ""
        var description = ""
        var audioUrl = ""
        var imageUrl: String? = null
        var pubDate = 0L
        var duration = 0L

        parser.require(XmlPullParser.START_TAG, null, "item")
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.eventType != XmlPullParser.START_TAG) continue
            when (parser.name) {
                "title" -> title = readText(parser)
                "description" -> description = readText(parser)
                "guid" -> id = readText(parser)
                "itunes:image" -> {
                    val href = parser.getAttributeValue(null, "href")
                    if (href != null) imageUrl = href
                    skip(parser)
                }
                "pubDate" -> {
                    val dateStr = readText(parser)
                    pubDate = try {
                        dateFormat.parse(dateStr)?.time ?: 0L
                    } catch (e: Exception) {
                        0L
                    }
                }
                "enclosure" -> {
                    audioUrl = parser.getAttributeValue(null, "url") ?: ""
                    skip(parser)
                }
                "itunes:duration" -> {
                    val durationStr = readText(parser)
                    duration = parseDuration(durationStr)
                }
                else -> skip(parser)
            }
        }
        if (id.isEmpty()) id = audioUrl

        return Episode(
            id = id,
            podcastFeedUrl = feedUrl,
            title = title,
            description = description,
            audioUrl = audioUrl,
            imageUrl = imageUrl,
            pubDate = pubDate,
            duration = duration,
            downloadStatus = 0,
            localFilePath = null
        )
    }

    private fun readImage(parser: XmlPullParser): String {
        var url = ""
        parser.require(XmlPullParser.START_TAG, null, "image")
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.eventType != XmlPullParser.START_TAG) continue
            if (parser.name == "url") {
                url = readText(parser)
            } else {
                skip(parser)
            }
        }
        return url
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

    private fun parseDuration(durationStr: String): Long {
        return try {
            val parts = durationStr.split(":")
            when (parts.size) {
                1 -> parts[0].toLong()
                2 -> parts[0].toLong() * 60 + parts[1].toLong()
                3 -> parts[0].toLong() * 3600 + parts[1].toLong() * 60 + parts[2].toLong()
                else -> 0L
            }
        } catch (e: Exception) {
            0L
        }
    }
}