package com.yuval.podcasts.media

import androidx.media3.common.Player

/**
 * Extension functions for [Player] to provide common, bounded media operations.
 */

fun Player.seekForwardBounded(ms: Long) {
    val dur = duration
    if (dur <= 0 || dur == androidx.media3.common.C.TIME_UNSET) {
        return
    }
    val newPosition = (currentPosition + ms).coerceAtMost(dur)
    seekTo(newPosition)
}

fun Player.seekBackwardBounded(ms: Long) {
    val newPosition = (currentPosition - ms).coerceAtLeast(0L)
    seekTo(newPosition)
}
