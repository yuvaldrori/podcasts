package com.yuval.podcasts.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.yuval.podcasts.data.db.entity.EpisodeWithPodcast
import com.yuval.podcasts.ui.components.EpisodeItem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

import androidx.compose.ui.res.stringResource
import com.yuval.podcasts.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    history: List<EpisodeWithPodcast>,
    onNavigateToEpisode: (String) -> Unit,
    onEnqueueEpisode: (EpisodeWithPodcast) -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.history_title)) }
            )
        },
        modifier = modifier
    ) { paddingValues ->
        if (history.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.no_play_history),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                items(
                    items = history,
                    key = { it.episode.id }
                ) { episodeWithPodcast ->
                    val clickHandler = remember(episodeWithPodcast.episode.id) { { onNavigateToEpisode(episodeWithPodcast.episode.id) } }
                    EpisodeItem(
                        episode = episodeWithPodcast.episode,
                        modifier = Modifier.clickable(onClick = clickHandler),
                        imageUrl = episodeWithPodcast.podcast.imageUrl,
                        trailingContent = {
                            androidx.compose.material3.IconButton(onClick = { onEnqueueEpisode(episodeWithPodcast) }) {
                                androidx.compose.material3.Icon(
                                    androidx.compose.material.icons.Icons.Default.Add,
                                    contentDescription = stringResource(R.string.add_to_queue)
                                )
                            }
                        }
                    )
                }
            }
        }
    }
}
