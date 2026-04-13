package com.yuval.podcasts.ui.utils

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class FormatterTest {

    @Test
    fun testFormatDate() {
        // Set fixed timestamp for testing (e.g., Oct 15, 2023)
        val calendar = Calendar.getInstance(TimeZone.getDefault())
        calendar.set(2023, Calendar.OCTOBER, 15)
        val timestamp = calendar.timeInMillis
        
        val result = Formatter.formatDate(timestamp)
        assertEquals("Oct 15, 2023", result)
    }

    @Test
    fun testFormatDateZero() {
        assertEquals("", Formatter.formatDate(0L))
    }

    @Test
    fun testFormatDurationShort() {
        assertEquals("1h 30m", Formatter.formatDurationShort(5400L)) // 1.5h
        assertEquals("45m", Formatter.formatDurationShort(2700L)) // 45m
        assertEquals("5m", Formatter.formatDurationShort(300L)) // 5m
        assertEquals("", Formatter.formatDurationShort(0L))
        assertEquals("", Formatter.formatDurationShort(-10L))
    }

    @Test
    fun testFormatTime() {
        assertEquals("1:30:00", Formatter.formatTime(5400000L)) // 1h 30m
        assertEquals("45:00", Formatter.formatTime(2700000L)) // 45m
        assertEquals("05:00", Formatter.formatTime(300000L)) // 5m
        assertEquals("00:01", Formatter.formatTime(1000L)) // 1s
        assertEquals("00:00", Formatter.formatTime(0L))
    }

    @Test
    fun testFormatRemainingTime() {
        assertEquals("2h 15m", Formatter.formatRemainingTime(8100000L)) // 2h 15m
        assertEquals("10m", Formatter.formatRemainingTime(600000L)) // 10m
        assertEquals("0m", Formatter.formatRemainingTime(30000L)) // 30s -> rounds down to 0m per logic
    }
}
