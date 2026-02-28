package com.yuval.podcasts.ui.screens

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.yuval.podcasts.ui.components.EpisodeItem
import com.yuval.podcasts.ui.viewmodel.QueueViewModel
import kotlinx.coroutines.delay
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueScreen(
    onEpisodeClick: (String) -> Unit,
    viewModel: QueueViewModel = hiltViewModel()
) {
    val queue by viewModel.queue.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()

    // Periodically update position when playing
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            viewModel.updatePosition()
            delay(1000)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp)
        ) {
            items(queue, key = { it.episode.id }) { episodeWithPodcast ->
                val onDismiss = remember(episodeWithPodcast.episode.id) {
                    { value: SwipeToDismissBoxValue ->
                        if (value == SwipeToDismissBoxValue.EndToStart || value == SwipeToDismissBoxValue.StartToEnd) {
                            viewModel.removeFromQueue(episodeWithPodcast.episode.id)
                            true
                        } else {
                            false
                        }
                    }
                }
                
                SwipeToDismissBox(
                    state = rememberSwipeToDismissBoxState(
                        confirmValueChange = onDismiss
                    ),
                    backgroundContent = {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(vertical = 4.dp),
                        )
                    },
                    content = {
                        Surface(tonalElevation = 0.dp) {
                            val clickHandler = remember(episodeWithPodcast.episode.id) { 
                                { onEpisodeClick(episodeWithPodcast.episode.id) } 
                            }
                            EpisodeItem(
                                episode = episodeWithPodcast.episode,
                                modifier = Modifier.clickable(onClick = clickHandler),
                                imageUrl = episodeWithPodcast.podcast.imageUrl,
                                trailingContent = {
                                    IconButton(onClick = { viewModel.play(episodeWithPodcast.episode) }) {
                                        Icon(Icons.Default.PlayArrow, contentDescription = "Play")
                                    }
                                }
                            )
                        }
                    }
                )
            }
        }
        
        PlaybackControls(viewModel = viewModel)
    }
}

@Composable
fun PlaybackControls(viewModel: QueueViewModel) {
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val playbackSpeed by viewModel.playbackSpeed.collectAsStateWithLifecycle()
    val currentPosition by viewModel.currentPosition.collectAsStateWithLifecycle()
    val duration by viewModel.duration.collectAsStateWithLifecycle()

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
                onValueChange = { viewModel.seekTo((it * duration).toLong()) },
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                TextButton(onClick = { viewModel.toggleSpeed() }) {
                    Text(text = if (playbackSpeed >= 2f) "2x" else "1x")
                }
                IconButton(onClick = { viewModel.seekBackward() }) {
                    Icon(Icons.Default.FastRewind, contentDescription = "-30s")
                }
                IconButton(onClick = { viewModel.playPause() }) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp)
                    )
                }
                IconButton(onClick = { viewModel.seekForward() }) {
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
