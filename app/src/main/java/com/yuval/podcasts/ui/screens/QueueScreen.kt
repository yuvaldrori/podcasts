package com.yuval.podcasts.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.yuval.podcasts.data.db.entity.Episode
import com.yuval.podcasts.ui.components.EpisodeItem
import com.yuval.podcasts.ui.viewmodel.QueueViewModel
import kotlinx.coroutines.delay
import org.burnoutcrew.reorderable.ReorderableItem
import org.burnoutcrew.reorderable.detectReorderAfterLongPress
import org.burnoutcrew.reorderable.rememberReorderableLazyListState
import org.burnoutcrew.reorderable.reorderable
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueScreen(
    onEpisodeClick: (String) -> Unit,
    viewModel: QueueViewModel = hiltViewModel()
) {
    val queue by viewModel.queue.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val playbackSpeed by viewModel.playbackSpeed.collectAsState()
    val currentPosition by viewModel.currentPosition.collectAsState()
    val duration by viewModel.duration.collectAsState()

    // Periodically update position when playing
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            viewModel.updatePosition()
            delay(1000)
        }
    }

    val state = rememberReorderableLazyListState(onMove = { from, to ->
        viewModel.reorderItem(from.index, to.index)
    })

    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = state.listState,
            modifier = Modifier
                .weight(1f)
                .reorderable(state)
                .detectReorderAfterLongPress(state),
            contentPadding = PaddingValues(16.dp)
        ) {
            items(queue, key = { it.episode.id }) { episodeWithPodcast ->
                ReorderableItem(state, key = episodeWithPodcast.episode.id) { isDragging ->
                    val elevation = if (isDragging) 8.dp else 0.dp
                    
                    SwipeToDismissBox(
                        state = rememberSwipeToDismissBoxState(
                            confirmValueChange = { value ->
                                if (value == SwipeToDismissBoxValue.EndToStart || value == SwipeToDismissBoxValue.StartToEnd) {
                                    viewModel.removeFromQueue(episodeWithPodcast.episode.id)
                                    true
                                } else {
                                    false
                                }
                            }
                        ),
                        backgroundContent = {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(vertical = 4.dp),
                            ) {
                                // Background colors handled by Box depending on direction
                            }
                        },
                        content = {
                            Surface(tonalElevation = elevation) {
                                EpisodeItem(
                                    episode = episodeWithPodcast.episode,
                                    modifier = Modifier.clickable { onEpisodeClick(episodeWithPodcast.episode.id) },
                                    imageUrl = episodeWithPodcast.podcast.imageUrl,
                                    trailingContent = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            IconButton(onClick = { viewModel.play(episodeWithPodcast.episode) }) {
                                                Icon(Icons.Default.PlayArrow, contentDescription = "Play")
                                            }
                                            Icon(
                                                Icons.Default.DragHandle,
                                                contentDescription = "Reorder",
                                                modifier = Modifier.padding(start = 8.dp)
                                            )
                                        }
                                    }
                                )
                            }
                        }
                    )
                }
            }
        }
        
        PlaybackControls(
            isPlaying = isPlaying,
            playbackSpeed = playbackSpeed,
            currentPosition = currentPosition,
            duration = duration,
            onPlayPause = { viewModel.playPause() },
            onSeekBackward = { viewModel.seekBackward() },
            onSeekForward = { viewModel.seekForward() },
            onToggleSpeed = { viewModel.toggleSpeed() },
            onSeekTo = { viewModel.seekTo(it) }
        )
    }
}

@Composable
fun PlaybackControls(
    isPlaying: Boolean,
    playbackSpeed: Float,
    currentPosition: Long,
    duration: Long,
    onPlayPause: () -> Unit,
    onSeekBackward: () -> Unit,
    onSeekForward: () -> Unit,
    onToggleSpeed: () -> Unit,
    onSeekTo: (Long) -> Unit
) {
    Surface(tonalElevation = 8.dp) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = formatTime(currentPosition), style = MaterialTheme.typography.bodySmall)
                Text(text = formatTime(duration), style = MaterialTheme.typography.bodySmall)
            }
            
            val progress = if (duration > 0) currentPosition.toFloat() / duration.toFloat() else 0f
            Slider(
                value = progress,
                onValueChange = { onSeekTo((it * duration).toLong()) },
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                TextButton(onClick = onToggleSpeed) {
                    Text(text = if (playbackSpeed >= 2f) "2x" else "1x")
                }
                IconButton(onClick = onSeekBackward) {
                    Icon(Icons.Default.FastRewind, contentDescription = "-30s")
                }
                IconButton(onClick = onPlayPause) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp)
                    )
                }
                IconButton(onClick = onSeekForward) {
                    Icon(Icons.Default.FastForward, contentDescription = "+30s")
                }
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

