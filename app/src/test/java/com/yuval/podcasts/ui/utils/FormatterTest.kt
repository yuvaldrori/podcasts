package com.yuval.podcasts.ui.utils

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

class FormatterTest {

    private lateinit var originalLocale: Locale

    @Before
    fun setup() {
        originalLocale = Locale.getDefault()
        Locale.setDefault(Locale.US)
    }

    @After
    fun teardown() {
        Locale.setDefault(originalLocale)
    }

    @Test
    fun testFormatDate() {
        val timestamp = LocalDate.of(2023, 10, 15)
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

        val result = Formatter.formatDate(timestamp)
        assertEquals("Oct 15, 2023", result)
    }

    @Test
    fun testFormatDateZero() {
        assertEquals("", Formatter.formatDate(0L))
    }

    @Test
    fun testFormatDurationShort() {
        assertEquals("1h 30m", Formatter.formatDurationShort(5400L))
        assertEquals("45m", Formatter.formatDurationShort(2700L))
        assertEquals("5m", Formatter.formatDurationShort(300L))
        assertEquals("", Formatter.formatDurationShort(0L))
        assertEquals("", Formatter.formatDurationShort(-10L))
    }

    @Test
    fun testFormatTime() {
        assertEquals("1:30:00", Formatter.formatTime(5400000L))
        assertEquals("45:00", Formatter.formatTime(2700000L))
        assertEquals("05:00", Formatter.formatTime(300000L))
        assertEquals("00:01", Formatter.formatTime(1000L))
        assertEquals("00:00", Formatter.formatTime(0L))
    }

    @Test
    fun testFormatRemainingTime() {
        assertEquals("2h 15m", Formatter.formatRemainingTime(8100000L))
        assertEquals("10m", Formatter.formatRemainingTime(600000L))
        assertEquals("0m", Formatter.formatRemainingTime(30000L))
    }
}
