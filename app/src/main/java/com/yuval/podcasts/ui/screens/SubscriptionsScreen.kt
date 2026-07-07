package com.yuval.podcasts.ui.screens

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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.yuval.podcasts.data.db.entity.Podcast
import com.yuval.podcasts.ui.components.PodcastItem
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState

import androidx.compose.ui.res.stringResource
import com.yuval.podcasts.R

import kotlinx.collections.immutable.ImmutableList
import com.yuval.podcasts.ui.components.RefreshProgressIndicator

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
        val pullToRefreshState = rememberPullToRefreshState()
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = onRefreshAll,
            state = pullToRefreshState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            indicator = {}
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                RefreshProgressIndicator(isRefreshing = isRefreshing, refreshProgress = refreshProgress)
                
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
                    item {
                        Box(
                            modifier = Modifier
                                .fillParentMaxHeight()
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(R.string.empty_subscriptions_message),
                                style = MaterialTheme.typography.bodyLarge,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                    item {
                        Spacer(modifier = Modifier.height(1.dp))
                    }
                } else {
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
}
