package com.yuval.podcasts.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
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
import androidx.compose.material.icons.filled.Pause

@Composable
fun QueueScreen(
    viewModel: QueueViewModel = hiltViewModel()
) {
    val queue by viewModel.queue.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp)
        ) {
            items(queue) { episode ->
                EpisodeItem(
                    episode = episode,
                    actionIcon = { Icon(Icons.Default.PlayArrow, contentDescription = "Play") },
                    onActionClick = { viewModel.play(episode) }
                )
            }
        }
        
        PlaybackControls(
            isPlaying = isPlaying,
            onPlayPause = { viewModel.playPause() }
        )
    }
}

@Composable
fun PlaybackControls(
    isPlaying: Boolean,
    onPlayPause: () -> Unit
) {
    Surface(tonalElevation = 8.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            IconButton(onClick = onPlayPause) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = null
                )
            }
        }
    }
}