package com.yuval.podcasts.data.network

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.util.Locale

object DateParser {
    private val formatters = listOf(
        DateTimeFormatter.RFC_1123_DATE_TIME,
        
        DateTimeFormatterBuilder()
            .parseCaseInsensitive()
            .appendPattern("[EEE, ]d MMM yyyy HH:mm:ss[ zzz][ Z][ z]")
            .toFormatter(Locale.ENGLISH),
            
        DateTimeFormatterBuilder()
            .parseCaseInsensitive()
            .appendPattern("[EEE, ]d MMM yyyy HH:mm[ zzz][ Z][ z]")
            .toFormatter(Locale.ENGLISH),

        DateTimeFormatterBuilder()
            .parseCaseInsensitive()
            .appendPattern("yyyy-MM-dd'T'HH:mm:ss[XXX][X]")
            .toFormatter(Locale.ENGLISH)
    )

    fun parsePubDate(dateStr: String?): Long {
        if (dateStr.isNullOrBlank()) return 0L
        val trimmed = dateStr.trim()
        
        var sanitized = trimmed
            .replace("EST", "-0500")
            .replace("EDT", "-0400")
            .replace("CST", "-0600")
            .replace("CDT", "-0500")
            .replace("MST", "-0700")
            .replace("MDT", "-0600")
            .replace("PST", "-0800")
            .replace("PDT", "-0700")
            .replace("UT", "GMT")

        for (formatter in formatters) {
            try {
                val zonedDateTime = ZonedDateTime.parse(sanitized, formatter)
                return zonedDateTime.toInstant().toEpochMilli()
            } catch (e: Exception) {
                // Try next formatter
            }
        }
        
        try {
            return java.time.Instant.parse(sanitized).toEpochMilli()
        } catch (e: Exception) {
            // Give up
        }
        
        return 0L
    }
}
