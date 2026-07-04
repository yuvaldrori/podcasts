package com.yuval.podcasts.data.network

import org.junit.Assert.assertEquals
import org.junit.Test

class DateParserTest {

    @Test
    fun parsePubDate_validRfc1123_returnsCorrectMillis() {
        // "Tue, 03 Jun 2008 11:05:30 GMT" -> 1212491130000L
        val dateStr = "Tue, 03 Jun 2008 11:05:30 GMT"
        val millis = DateParser.parsePubDate(dateStr)
        assertEquals(1212491130000L, millis)
    }

    @Test
    fun parsePubDate_singleDigitDay_returnsCorrectMillis() {
        // "Tue, 3 Jun 2008 11:05:30 GMT" -> 1212491130000L
        val dateStr = "Tue, 3 Jun 2008 11:05:30 GMT"
        val millis = DateParser.parsePubDate(dateStr)
        assertEquals(1212491130000L, millis)
    }

    @Test
    fun parsePubDate_legacyTimezoneEst_returnsCorrectMillis() {
        // "Tue, 03 Jun 2008 11:05:30 EST" -> converted to -0500
        val dateStr = "Tue, 03 Jun 2008 11:05:30 EST"
        val millis = DateParser.parsePubDate(dateStr)
        // 11:05:30 EST -> 16:05:30 GMT -> 1212509130000L
        assertEquals(1212509130000L, millis)
    }

    @Test
    fun parsePubDate_iso8601Fallback_returnsCorrectMillis() {
        val dateStr = "2008-06-03T11:05:30Z"
        val millis = DateParser.parsePubDate(dateStr)
        assertEquals(1212491130000L, millis)
    }

    @Test
    fun parsePubDate_invalidDate_returnsZero() {
        val dateStr = "Invalid Date String"
        val millis = DateParser.parsePubDate(dateStr)
        assertEquals(0L, millis)
    }
}
