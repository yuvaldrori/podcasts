package com.yuval.podcasts.media

import androidx.media3.common.Player

/**
 * Extension functions for [Player] to provide common, bounded media operations.
 */

fun Player.seekForwardBounded(ms: Long) {
    val newPosition = (currentPosition + ms).coerceAtMost(duration.coerceAtLeast(0L))
    seekTo(newPosition)
}

fun Player.seekBackwardBounded(ms: Long) {
    val newPosition = (currentPosition - ms).coerceAtLeast(0L)
    seekTo(newPosition)
}
