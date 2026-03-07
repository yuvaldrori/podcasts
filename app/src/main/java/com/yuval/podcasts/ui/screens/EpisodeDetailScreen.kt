package com.yuval.podcasts.ui.screens
import androidx.lifecycle.compose.collectAsStateWithLifecycle

import android.content.Intent
import android.text.Html
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import coil.compose.AsyncImage
import com.yuval.podcasts.ui.viewmodel.EpisodeDetailViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EpisodeDetailScreen(
    onBack: () -> Unit,
    viewModel: EpisodeDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Episode Details", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        val data = uiState.episodeWithPodcast
        if (uiState.isLoading || data == null) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top
                ) {
                    val imageUrl = data.episode.imageUrl ?: data.podcast.imageUrl
                    AsyncImage(
                        model = imageUrl,
                        contentDescription = "Podcast Cover",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(120.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = data.episode.title,
                            style = MaterialTheme.typography.titleLarge
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = data.podcast.title,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = formatLongDate(data.episode.pubDate),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (!uiState.isInQueue && !data.episode.isPlayed) {
                        FilledIconButton(
                            onClick = { 
                                viewModel.addToQueue(data.episode)
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Add to Queue")
                        }
                    }
                    
                    OutlinedIconButton(
                        onClick = {
                            val shareIntent = Intent.createChooser(Intent().apply {
                                action = Intent.ACTION_SEND
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TITLE, data.episode.title)
                                putExtra(Intent.EXTRA_TEXT, "${data.episode.title}\n${data.episode.audioUrl}")
                            }, "Share Episode")
                            context.startActivity(shareIntent)
                        },
                        modifier = Modifier.weight(if (!uiState.isInQueue && !data.episode.isPlayed) 1f else 0.5f)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "Share")
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
                
                // Use fromHtml to parse basic HTML tags often found in podcast RSS descriptions
                val parsedDescription = remember(data.episode.description) {
                    Html.fromHtml(data.episode.description, Html.FROM_HTML_MODE_COMPACT).toString()
                }
                Text(
                    text = parsedDescription,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }
}

private fun formatLongDate(timestamp: Long): String {
    if (timestamp == 0L) return ""
    val dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale.getDefault())
    return dateFormat.format(Date(timestamp))
}
