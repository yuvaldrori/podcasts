package com.yuval.podcasts.ui.screens

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
import com.yuval.podcasts.data.db.entity.Episode
import com.yuval.podcasts.ui.components.EpisodeItem

import androidx.compose.ui.res.stringResource
import com.yuval.podcasts.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PodcastDetailScreen(
    episodes: List<Episode>,
    onBack: () -> Unit,
    onEpisodeClick: (String) -> Unit,
    onAddToQueue: (Episode) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.episodes_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
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
                        IconButton(onClick = { onAddToQueue(episode) }) {
                            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_to_queue))
                        }
                    }
                )
            }
        }
    }
}
