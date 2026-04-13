package com.yuval.podcasts.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.yuval.podcasts.data.db.entity.Podcast
import com.yuval.podcasts.ui.components.PodcastItem

import androidx.compose.ui.res.stringResource
import com.yuval.podcasts.R

import kotlinx.collections.immutable.ImmutableList

@Composable
fun SubscriptionsScreen(
    podcasts: ImmutableList<Podcast>,
    onPodcastClick: (String) -> Unit,
    onUnsubscribe: (String) -> Unit
) {
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
                            Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.options), modifier = Modifier.size(24.dp))
                        }
                        DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.unsubscribe)) },
                                onClick = {
                                    expanded = false
                                    onUnsubscribe(podcast.feedUrl)
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
