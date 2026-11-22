package com.yuval.podcasts.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yuval.podcasts.R
import com.yuval.podcasts.data.db.entity.Podcast
import com.yuval.podcasts.ui.components.PodcastItem
import com.yuval.podcasts.ui.components.RefreshableContent
import com.yuval.podcasts.ui.components.emptyStateItem
import kotlinx.collections.immutable.ImmutableList

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionsScreen(
    podcasts: ImmutableList<Podcast>,
    onPodcastClick: (String) -> Unit,
    onUnsubscribe: (String) -> Unit,
    isRefreshing: Boolean = false,
    refreshProgress: Pair<Int, Int>? = null,
    onRefreshAll: () -> Unit = {},
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.episodes_title)) }
            )
        }
    ) { padding ->
        RefreshableContent(
            isRefreshing = isRefreshing,
            refreshProgress = refreshProgress,
            onRefresh = onRefreshAll,
            modifier = Modifier.padding(padding)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(
                    top = 16.dp,
                    bottom = contentPadding.calculateBottomPadding() + 16.dp,
                    start = 16.dp,
                    end = 16.dp
                )
            ) {
                if (podcasts.isEmpty()) {
                    emptyStateItem(R.string.empty_subscriptions_message)
                } else {
                    items(
                        items = podcasts,
                        key = { it.feedUrl }
                    ) { podcast ->
                        var expanded by remember { mutableStateOf(false) }
                        val handlePodcastClick = remember(podcast.feedUrl, onPodcastClick) { { onPodcastClick(podcast.feedUrl) } }

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
                                                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.unsubscribe))
                                            }
                                        )
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}
