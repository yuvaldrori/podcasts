package com.yuval.podcasts.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.yuval.podcasts.data.db.entity.Episode
import com.yuval.podcasts.ui.components.EpisodeItem
import com.yuval.podcasts.ui.viewmodel.FeedsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewEpisodesScreen(
    viewModel: FeedsViewModel = hiltViewModel()
) {
    val episodes by viewModel.unplayedEpisodes.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New Episodes") },
                actions = {
                    IconButton(onClick = { viewModel.dismissAll() }) {
                        Icon(Icons.Default.Clear, contentDescription = "Dismiss All")
                    }
                    IconButton(onClick = { viewModel.refreshAll() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp)
        ) {
            items(episodes) { episode ->
                EpisodeItem(
                    episode = episode,
                    trailingContent = {
                        Row {
                            IconButton(onClick = { viewModel.dismissEpisode(episode) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Dismiss")
                            }
                            IconButton(onClick = { viewModel.addToQueue(episode) }) {
                                Icon(Icons.Default.Add, contentDescription = "Add to Queue")
                            }
                        }
                    }
                )
            }
        }
    }
}
