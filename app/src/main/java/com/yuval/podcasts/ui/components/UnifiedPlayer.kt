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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yuval.podcasts.ui.viewmodel.QueueViewModel
import java.util.Locale

@Composable
fun UnifiedPlayer(
    viewModel: QueueViewModel,
    modifier: Modifier = Modifier
) {
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val playbackSpeed by viewModel.playbackSpeed.collectAsStateWithLifecycle()
    val currentPosition by viewModel.currentPosition.collectAsStateWithLifecycle()
    val duration by viewModel.duration.collectAsStateWithLifecycle()
    val currentEpisode by viewModel.currentlyPlayingEpisode.collectAsStateWithLifecycle()

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
                    text = currentEpisode?.title ?: "Not Playing",
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
                    TextButton(
                        onClick = { viewModel.toggleSpeed() },
                        contentPadding = PaddingValues(0.dp),
                        modifier = Modifier.defaultMinSize(minWidth = 36.dp, minHeight = 36.dp)
                    ) {
                        Text(text = if (playbackSpeed >= 2f) "2x" else "1x", style = MaterialTheme.typography.labelLarge)
                    }
                    IconButton(
                        onClick = { viewModel.seekBackward() },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Default.FastRewind, contentDescription = "-30s", modifier = Modifier.size(20.dp))
                    }
                    IconButton(
                        onClick = { viewModel.playPause() },
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    IconButton(
                        onClick = { viewModel.seekForward() },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Default.FastForward, contentDescription = "+30s", modifier = Modifier.size(20.dp))
                    }
                }
            }

            // Bottom Row: Progress Slider and Times
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset(y = (-8).dp), // Pull the slider up slightly to save space
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = formatTime(currentPosition), style = MaterialTheme.typography.labelSmall)
                
                val progress = if (duration > 0) currentPosition.toFloat() / duration.toFloat() else 0f
                Slider(
                    value = progress,
                    onValueChange = { viewModel.seekTo((it * duration).toLong()) },
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp).height(24.dp)
                )
                
                Text(text = formatTime(duration), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    if (ms < 0) return "00:00"
    val seconds = (ms / 1000) % 60
    val minutes = (ms / (1000 * 60)) % 60
    val hours = ms / (1000 * 60 * 60)
    return if (hours > 0) {
        String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}
