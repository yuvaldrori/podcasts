package com.yuval.podcasts.ui.screens
import androidx.lifecycle.compose.collectAsStateWithLifecycle

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.yuval.podcasts.data.db.entity.Podcast
import com.yuval.podcasts.ui.viewmodel.FeedsViewModel

@Composable
fun SubscriptionsScreen(
    viewModel: FeedsViewModel = hiltViewModel(),
    onPodcastClick: (String) -> Unit
) {
    val podcasts by viewModel.podcasts.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp)
    ) {
        items(
            items = podcasts,
            key = { it.feedUrl }
        ) { podcast ->
            var expanded by remember { mutableStateOf(false) }
            val handlePodcastClick = remember(podcast.feedUrl) { { onPodcastClick(podcast.feedUrl) } }

            PodcastItem(
                podcast = podcast,
                onClick = handlePodcastClick,
                trailingContent = {
                    Box {
                        IconButton(onClick = { expanded = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Options", modifier = Modifier.size(24.dp))
                        }
                        DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Unsubscribe") },
                                onClick = {
                                    expanded = false
                                    viewModel.unsubscribePodcast(podcast.feedUrl)
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Delete, contentDescription = null)
                                }
                            )
                        }
                    }
                }
            )
        }
    }
}

@Composable
fun PodcastItem(podcast: Podcast, onClick: () -> Unit, trailingContent: @Composable () -> Unit = {}) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = podcast.imageUrl,
                contentDescription = null,
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = podcast.title, style = MaterialTheme.typography.titleMedium)
                Text(text = podcast.description, maxLines = 2, style = MaterialTheme.typography.bodySmall)
            }
            trailingContent()
        }
    }
}