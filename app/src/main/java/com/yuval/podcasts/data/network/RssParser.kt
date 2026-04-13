package com.yuval.podcasts.data.network

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import com.yuval.podcasts.data.db.entity.NetworkEpisode
import com.yuval.podcasts.data.db.entity.Podcast
import com.yuval.podcasts.data.db.entity.Chapter
import com.yuval.podcasts.data.db.entity.NetworkEpisodeWithChapters
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Locale

class RssParser {
    private val dateFormat = java.lang.ThreadLocal.withInitial { 
        SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.US) 
    }

    fun parse(inputStream: InputStream, feedUrl: String): Pair<Podcast, List<NetworkEpisodeWithChapters>> {
        val factory = XmlPullParserFactory.newInstance()
        factory.isNamespaceAware = true
        val parser = factory.newPullParser()
        parser.setInput(inputStream, null)
        parser.nextTag()
        return readRss(parser, feedUrl)
    }

    private fun readRss(parser: XmlPullParser, feedUrl: String): Pair<Podcast, List<NetworkEpisodeWithChapters>> {
        var podcastTitle = ""
        var podcastDescription = ""
        var podcastImageUrl = ""
        var podcastWebsite = ""
        val episodes = mutableListOf<NetworkEpisodeWithChapters>()

        parser.require(XmlPullParser.START_TAG, null, "rss")
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.eventType != XmlPullParser.START_TAG) continue
            if (parser.name == "channel") {
                while (parser.next() != XmlPullParser.END_TAG) {
                    if (parser.eventType != XmlPullParser.START_TAG) continue
                    val tagName = parser.name
                    when (tagName) {
                        "title" -> podcastTitle = readText(parser)
                        "description" -> podcastDescription = readText(parser)
                        "link" -> podcastWebsite = readText(parser)
                        "image" -> {
                            // itunes:image uses 'href' attribute in namespace-aware mode
                            val href = parser.getAttributeValue(null, "href")
                            if (href != null) {
                                podcastImageUrl = href
                                skip(parser)
                            } else {
                                // Standard RSS <image> has a nested <url> tag
                                podcastImageUrl = readImage(parser)
                            }
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

    private fun readItem(parser: XmlPullParser, feedUrl: String): NetworkEpisodeWithChapters {
        var id = ""
        var title = ""
        var description = ""
        var episodeWebLink: String? = null
        var audioUrl = ""
        var imageUrl: String? = null
        var pubDate = 0L
        var duration = 0L
        val chapters = mutableListOf<Chapter>()

        parser.require(XmlPullParser.START_TAG, null, "item")
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.eventType != XmlPullParser.START_TAG) continue
            val tagName = parser.name
            when (tagName) {
                "title" -> title = readText(parser)
                "description" -> description = readText(parser)
                "link" -> episodeWebLink = readText(parser)
                "guid" -> id = readText(parser)
                "image" -> {
                    val href = parser.getAttributeValue(null, "href")
                    if (href != null) imageUrl = href
                    skip(parser)
                }
                "pubDate" -> {
                    val dateStr = readText(parser)
                    pubDate = try {
                        dateFormat.get()?.parse(dateStr)?.time ?: 0L
                    } catch (e: Exception) {
                        if (e is kotlinx.coroutines.CancellationException) throw e
                        0L
                    }
                }
                "enclosure" -> {
                    audioUrl = parser.getAttributeValue(null, "url") ?: ""
                    skip(parser)
                }
                "duration" -> {
                    val durationStr = readText(parser)
                    duration = parseDuration(durationStr)
                }
                "chapters" -> {
                    chapters.addAll(readPodloveChapters(parser, id.ifEmpty { audioUrl }))
                }
                else -> skip(parser)
            }
        }
        if (id.isEmpty()) id = audioUrl

        return NetworkEpisodeWithChapters(
            episode = NetworkEpisode(
                id = id,
                podcastFeedUrl = feedUrl,
                title = title,
                description = description,
                audioUrl = audioUrl,
                imageUrl = imageUrl,
                episodeWebLink = episodeWebLink,
                pubDate = pubDate,
                duration = duration
            ),
            chapters = chapters
        )
    }

    private fun readPodloveChapters(parser: XmlPullParser, episodeId: String): List<Chapter> {
        val chapters = mutableListOf<Chapter>()
        // We are at <psc:chapters> or <chapters>
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.eventType != XmlPullParser.START_TAG) continue
            if (parser.name == "chapter") {
                val start = parser.getAttributeValue(null, "start") ?: "0"
                val title = parser.getAttributeValue(null, "title") ?: ""
                val href = parser.getAttributeValue(null, "href")
                val image = parser.getAttributeValue(null, "image")
                
                chapters.add(Chapter(
                    episodeId = episodeId,
                    title = title,
                    startTimeMs = parseDuration(start) * 1000L,
                    url = href,
                    imageUrl = image
                ))
                skip(parser)
            } else {
                skip(parser)
            }
        }
        return chapters
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
                XmlPullParser.END_DOCUMENT -> return // Break to avoid infinite loops on malformed XML
            }
        }
    }

    private fun parseDuration(durationStr: String): Long {
        return try {
            val parts = durationStr.split(":")
            when (parts.size) {
                1 -> {
                    // Could be "seconds" or "seconds.milliseconds"
                    parts[0].toDouble().toLong()
                }
                2 -> parts[0].toLong() * 60 + parts[1].toDouble().toLong()
                3 -> parts[0].toLong() * 3600 + parts[1].toLong() * 60 + parts[2].toDouble().toLong()
                else -> 0L
            }
        } catch (e: NumberFormatException) {
            0L
        } catch (e: Exception) {
                        if (e is kotlinx.coroutines.CancellationException) throw e
            0L
        }
    }
}
