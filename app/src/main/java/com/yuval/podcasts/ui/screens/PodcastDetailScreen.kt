package com.yuval.podcasts.ui.screens
import androidx.lifecycle.compose.collectAsStateWithLifecycle

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.yuval.podcasts.data.db.entity.Episode
import com.yuval.podcasts.ui.components.EpisodeItem
import com.yuval.podcasts.ui.viewmodel.FeedsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PodcastDetailScreen(
    feedUrl: String,
    onBack: () -> Unit,
    onEpisodeClick: (String) -> Unit,
    viewModel: FeedsViewModel = hiltViewModel()
) {
    val episodesFlow = remember(feedUrl) { viewModel.getEpisodesForPodcast(feedUrl) }
    val episodes by episodesFlow.collectAsStateWithLifecycle(emptyList())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Episodes") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp)
        ) {
            items(
                items = episodes,
                key = { it.id }
            ) { episode ->
                val clickHandler = remember(episode.id) { { onEpisodeClick(episode.id) } }
                EpisodeItem(
                    episode = episode,
                    modifier = Modifier.clickable(onClick = clickHandler),
                    imageUrl = episode.imageUrl,
                    showProgress = true,
                    showPlayedMarker = true,
                    trailingContent = {
                        val onAddClick = remember(episode.id) { { viewModel.addToQueue(episode) } }
                        IconButton(onClick = onAddClick) {
                            Icon(Icons.Default.Add, contentDescription = "Add to Queue")
                        }
                    }
                )
            }
        }
    }
}
