package com.yuval.podcasts.ui.utils

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

object Formatter {
    fun formatDate(timestamp: Long): String {
        if (timestamp == 0L) return ""
        val dateFormatter = DateTimeFormatter.ofPattern("MMM dd, yyyy", Locale.getDefault())
            .withZone(ZoneId.systemDefault())
        return dateFormatter.format(Instant.ofEpochMilli(timestamp))
    }

    /**
     * Formats duration in seconds to "1h 2m" or "2m" format.
     */
    fun formatDurationShort(seconds: Long): String {
        if (seconds <= 0) return ""
        val duration = seconds.seconds
        return formatHoursMinutes(duration.inWholeHours, duration.inWholeMinutes % 60)
    }

    /**
     * Formats duration in milliseconds to "HH:mm:ss" or "mm:ss" format.
     */
    fun formatTime(ms: Long): String {
        val duration = ms.milliseconds
        val hours = duration.inWholeHours
        val minutes = duration.inWholeMinutes % 60
        val seconds = duration.inWholeSeconds % 60
        return if (hours > 0) {
            String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
        }
    }

    /**
     * Formats remaining queue time in milliseconds.
     */
    fun formatRemainingTime(ms: Long): String {
        if (ms <= 0) return ""
        val duration = ms.milliseconds
        return formatHoursMinutes(duration.inWholeHours, duration.inWholeMinutes % 60)
    }

    private fun formatHoursMinutes(hours: Long, minutes: Long): String {
        return if (hours > 0) {
            "${hours}h ${minutes}m"
        } else {
            "${minutes}m"
        }
    }
}
