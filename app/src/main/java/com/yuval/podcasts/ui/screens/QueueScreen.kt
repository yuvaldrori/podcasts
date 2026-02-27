package com.yuval.podcasts.ui.screens

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
import androidx.hilt.navigation.compose.hiltViewModel
import com.yuval.podcasts.data.db.entity.Episode
import com.yuval.podcasts.ui.components.EpisodeItem
import com.yuval.podcasts.ui.viewmodel.QueueViewModel

@Composable
fun QueueScreen(
    viewModel: QueueViewModel = hiltViewModel()
) {
    val queue by viewModel.queue.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val playbackSpeed by viewModel.playbackSpeed.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp)
        ) {
            items(queue) { episode ->
                EpisodeItem(
                    episode = episode,
                    trailingContent = {
                        IconButton(onClick = { viewModel.play(episode) }) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Play")
                        }
                    }
                )
            }
        }
        
        PlaybackControls(
            isPlaying = isPlaying,
            playbackSpeed = playbackSpeed,
            onPlayPause = { viewModel.playPause() },
            onSeekBackward = { viewModel.seekBackward() },
            onSeekForward = { viewModel.seekForward() },
            onToggleSpeed = { viewModel.toggleSpeed() }
        )
    }
}

@Composable
fun PlaybackControls(
    isPlaying: Boolean,
    playbackSpeed: Float,
    onPlayPause: () -> Unit,
    onSeekBackward: () -> Unit,
    onSeekForward: () -> Unit,
    onToggleSpeed: () -> Unit
) {
    Surface(tonalElevation = 8.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
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

