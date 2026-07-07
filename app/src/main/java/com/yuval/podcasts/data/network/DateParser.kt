package com.yuval.podcasts.data.network

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.util.Locale

object DateParser {
    private val TIMEZONE_OFFSETS = mapOf(
        "EST" to "-0500",
        "EDT" to "-0400",
        "CST" to "-0600",
        "CDT" to "-0500",
        "MST" to "-0700",
        "MDT" to "-0600",
        "PST" to "-0800",
        "PDT" to "-0700",
        "UT" to "GMT"
    )

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
        for ((abbrev, offset) in TIMEZONE_OFFSETS) {
            sanitized = sanitized.replace(abbrev, offset)
        }

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
