package com.yuval.podcasts.data.opml

import android.util.Xml
import com.yuval.podcasts.data.db.entity.Podcast
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.io.OutputStream

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OpmlManager @Inject constructor() {

    fun parse(inputStream: InputStream): List<String> {
        val urls = mutableListOf<String>()
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        // Disable DOCTYPE/entity processing to harden against XXE in untrusted OPML files.
        try {
            parser.setFeature("http://xmlpull.org/v1/doc/features.html#process-docdecl", false)
        } catch (e: Exception) {
            // Some parsers may not support this feature; we still attempt it for safety.
        }
        parser.setInput(inputStream, null)

        // Parse defensively: a malformed/truncated OPML should yield whatever subscriptions
        // were successfully read rather than crashing the whole import.
        try {
            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG && parser.name == "outline") {
                    val xmlUrl = parser.getAttributeValue(null, "xmlUrl")
                    if (xmlUrl != null && (xmlUrl.startsWith("http://") || xmlUrl.startsWith("https://"))) {
                        try {
                            java.net.URL(xmlUrl).toURI()
                            urls.add(xmlUrl)
                        } catch (e: Exception) {
                            // Ignore invalid URLs
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            // Malformed XML past this point: return what we have so far.
        }
        return urls
    }

    fun export(podcasts: List<Podcast>, outputStream: OutputStream) {
        val serializer = Xml.newSerializer()
        serializer.setOutput(outputStream, "UTF-8")
        serializer.startDocument("UTF-8", true)
        serializer.startTag(null, "opml")
        serializer.attribute(null, "version", "2.0")
        
        serializer.startTag(null, "head")
        serializer.startTag(null, "title")
        serializer.text("Podcast Subscriptions")
        serializer.endTag(null, "title")
        serializer.endTag(null, "head")
        
        serializer.startTag(null, "body")
        serializer.startTag(null, "outline")
        serializer.attribute(null, "text", "Podcasts")
        
        podcasts.forEach { podcast ->
            serializer.startTag(null, "outline")
            serializer.attribute(null, "type", "rss")
            serializer.attribute(null, "text", podcast.title)
            serializer.attribute(null, "xmlUrl", podcast.feedUrl)
            serializer.attribute(null, "htmlUrl", podcast.website)
            serializer.endTag(null, "outline")
        }
        
        serializer.endTag(null, "outline")
        serializer.endTag(null, "body")
        serializer.endTag(null, "opml")
        serializer.endDocument()
        serializer.flush()
    }
}