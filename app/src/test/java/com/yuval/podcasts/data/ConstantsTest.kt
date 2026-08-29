package com.yuval.podcasts.data

import org.junit.Assert.assertTrue
import org.junit.Test

class ConstantsTest {

    @Test
    fun audioMimeTypes_containsEssentialAudioFormatsAndFallback() {
        val types = Constants.AUDIO_MIME_TYPES.toList()
        assertTrue("Should include audio/*", types.contains("audio/*"))
        assertTrue("Should include audio/mpeg", types.contains("audio/mpeg"))
        assertTrue("Should include audio/mp3", types.contains("audio/mp3"))
        assertTrue("Should include application/octet-stream", types.contains("application/octet-stream"))
        assertTrue("Should include */*", types.contains(Constants.MIME_TYPE_ALL))
    }

    @Test
    fun opmlMimeTypes_containsOpmlAndXmlFormatsAndFallback() {
        val types = Constants.OPML_MIME_TYPES.toList()
        assertTrue("Should include text/x-opml", types.contains(Constants.MIME_TYPE_OPML))
        assertTrue("Should include text/xml", types.contains("text/xml"))
        assertTrue("Should include application/xml", types.contains("application/xml"))
        assertTrue("Should include text/plain", types.contains(Constants.MIME_TYPE_TEXT_PLAIN))
        assertTrue("Should include */*", types.contains(Constants.MIME_TYPE_ALL))
    }
}
