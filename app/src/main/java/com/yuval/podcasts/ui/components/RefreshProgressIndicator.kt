package com.yuval.podcasts.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

private const val TRACK_COLOR_ALPHA = 0.2f

/**
 * A shared refresh progress indicator shown at the top of screens during pull-to-refresh.
 * Displays a determinate bar when [refreshProgress] is available, or an indeterminate bar
 * when [isRefreshing] is true with no progress info.
 */
@Composable
fun RefreshProgressIndicator(
    isRefreshing: Boolean,
    refreshProgress: Pair<Int, Int>?
) {
    if (refreshProgress == null && !isRefreshing) return

    val modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 8.dp)
    val color = MaterialTheme.colorScheme.primary
    val trackColor = color.copy(alpha = TRACK_COLOR_ALPHA)

    if (refreshProgress != null) {
        val (current, total) = refreshProgress
        LinearProgressIndicator(
            progress = { if (total > 0) current.toFloat() / total.toFloat() else 0f },
            modifier = modifier,
            color = color,
            trackColor = trackColor
        )
    } else {
        LinearProgressIndicator(
            modifier = modifier,
            color = color,
            trackColor = trackColor
        )
    }
}
