package com.yuval.podcasts.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.yuval.podcasts.data.db.entity.Podcast
import com.yuval.podcasts.ui.viewmodel.FeedsViewModel

@Composable
fun FeedsScreen(
    viewModel: FeedsViewModel = hiltViewModel()
) {
    val podcasts by viewModel.podcasts.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp)
    ) {
        items(podcasts) { podcast ->
            PodcastItem(podcast = podcast) {
                // TODO: Navigate to episodes screen or show episodes inline
            }
        }
    }
}

@Composable
fun PodcastItem(podcast: Podcast, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clickable { onClick() }
    ) {
        Row(modifier = Modifier.padding(16.dp)) {
            AsyncImage(
                model = podcast.imageUrl,
                contentDescription = null,
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(text = podcast.title, style = MaterialTheme.typography.titleMedium)
                Text(text = podcast.description, maxLines = 2, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}