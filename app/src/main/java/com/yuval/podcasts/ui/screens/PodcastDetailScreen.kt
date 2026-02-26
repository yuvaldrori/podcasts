package com.yuval.podcasts.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
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
fun PodcastDetailScreen(
    feedUrl: String,
    viewModel: FeedsViewModel = hiltViewModel()
) {
    val episodes by viewModel.getEpisodesForPodcast(feedUrl).collectAsState(emptyList())

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Episodes") })
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp)
        ) {
            items(episodes) { episode ->
                EpisodeItem(
                    episode = episode,
                    showProgress = true,
                    showPlayedMarker = true,
                    actionIcon = { Icon(Icons.Default.Add, contentDescription = "Add to Queue") },
                    onActionClick = { viewModel.addToQueue(episode) }
                )
            }
        }
    }
}
