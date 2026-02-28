package com.yuval.podcasts.ui.screens
import androidx.lifecycle.compose.collectAsStateWithLifecycle

import androidx.compose.foundation.clickable
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
    onEpisodeClick: (String) -> Unit,
    viewModel: FeedsViewModel = hiltViewModel()
) {
    val episodes by viewModel.getEpisodesForPodcast(feedUrl).collectAsStateWithLifecycle(emptyList())

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
                    modifier = Modifier.clickable { onEpisodeClick(episode.id) },
                    imageUrl = episode.imageUrl,
                    showProgress = true,
                    showPlayedMarker = true,
                    trailingContent = {
                        IconButton(onClick = { viewModel.addToQueue(episode) }) {
                            Icon(Icons.Default.Add, contentDescription = "Add to Queue")
                        }
                    }
                )
            }
        }
    }
}
