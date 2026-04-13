package com.yuval.podcasts.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yuval.podcasts.data.db.entity.Episode
import java.util.Locale
import androidx.compose.ui.res.stringResource
import com.yuval.podcasts.R
import com.yuval.podcasts.ui.utils.Formatter

@Composable
fun UnifiedPlayer(
    currentEpisode: Episode?,
    isPlaying: Boolean,
    playbackSpeed: Float,
    isConnected: Boolean,
    currentPosition: Long,
    duration: Long,
    onToggleSpeed: () -> Unit,
    onSeekBackward: () -> Unit,
    onPlayPause: () -> Unit,
    onSeekForward: () -> Unit,
    onSeekTo: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        tonalElevation = 8.dp,
        shadowElevation = 8.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp)
        ) {
            // Top Row: Title and Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Episode Title
                Text(
                    text = currentEpisode?.title ?: stringResource(R.string.not_playing),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(end = 8.dp)
                )

                // Controls
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End
                ) {
                    CastButton()
                    
                    TextButton(
                        onClick = onToggleSpeed,
                        enabled = isConnected,
                        contentPadding = PaddingValues(0.dp),
                        modifier = Modifier.defaultMinSize(minWidth = 36.dp, minHeight = 36.dp)
                    ) {
                        val speedText = if (playbackSpeed % 1f == 0f) playbackSpeed.toInt().toString() else playbackSpeed.toString()
                        Text(text = stringResource(R.string.playback_speed_format, speedText), style = MaterialTheme.typography.labelLarge)
                    }
                    IconButton(
                        onClick = onSeekBackward,
                        enabled = isConnected,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(Icons.Default.FastRewind, contentDescription = null, modifier = Modifier.size(28.dp))
                    }
                    IconButton(
                        onClick = onPlayPause,
                        enabled = isConnected,
                        modifier = Modifier.size(56.dp)
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) stringResource(R.string.pause) else stringResource(R.string.play),
                            modifier = Modifier.size(40.dp)
                        )
                    }
                    IconButton(
                        onClick = onSeekForward,
                        enabled = isConnected,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(Icons.Default.FastForward, contentDescription = null, modifier = Modifier.size(28.dp))
                    }
                }
            }

            // Bottom Row: Progress Slider and Times
            Column(modifier = Modifier.fillMaxWidth()) {
                val currentProgress = if (duration > 0) currentPosition.toFloat() / duration.toFloat() else 0f
                Slider(
                    value = currentProgress,
                    onValueChange = { onSeekTo((it * duration).toLong()) },
                    modifier = Modifier.fillMaxWidth().height(32.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = Formatter.formatTime(currentPosition),
                        style = MaterialTheme.typography.labelSmall
                    )
                    Text(
                        text = Formatter.formatTime(duration),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
}
