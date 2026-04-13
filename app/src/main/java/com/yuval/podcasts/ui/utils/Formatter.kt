package com.yuval.podcasts.ui.utils

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Formatter {
    private val dateFormat = ThreadLocal.withInitial { 
        SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()) 
    }

    fun formatDate(timestamp: Long): String {
        if (timestamp == 0L) return ""
        return dateFormat.get()?.format(Date(timestamp)) ?: ""
    }

    /**
     * Formats duration in seconds to "1h 2m" or "2m" format.
     */
    fun formatDurationShort(seconds: Long): String {
        if (seconds <= 0) return ""
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        return if (hours > 0) {
            "${hours}h ${minutes}m"
        } else {
            "${minutes}m"
        }
    }

    /**
     * Formats duration in milliseconds to "HH:mm:ss" or "mm:ss" format.
     */
    fun formatTime(ms: Long): String {
        val totalSeconds = ms / 1000
        val seconds = totalSeconds % 60
        val minutes = (totalSeconds / 60) % 60
        val hours = totalSeconds / 3600
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
        val totalSeconds = ms / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        return if (hours > 0) {
            "${hours}h ${minutes}m"
        } else {
            "${minutes}m"
        }
    }
}
