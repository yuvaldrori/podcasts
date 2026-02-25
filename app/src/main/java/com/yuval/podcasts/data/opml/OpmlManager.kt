package com.yuval.podcasts.data.opml

import android.util.Xml
import com.yuval.podcasts.data.db.entity.Podcast
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.io.OutputStream

class OpmlManager {

    fun parse(inputStream: InputStream): List<String> {
        val urls = mutableListOf<String>()
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(inputStream, null)
        
        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG && parser.name == "outline") {
                val xmlUrl = parser.getAttributeValue(null, "xmlUrl")
                if (xmlUrl != null) {
                    urls.add(xmlUrl)
                }
            }
            eventType = parser.next()
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
    }
}