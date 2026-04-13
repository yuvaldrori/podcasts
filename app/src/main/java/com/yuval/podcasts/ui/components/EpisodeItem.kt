package com.yuval.podcasts.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import coil.compose.AsyncImage
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.yuval.podcasts.R
import com.yuval.podcasts.data.db.entity.Episode
import com.yuval.podcasts.ui.utils.Formatter

@Composable
fun EpisodeItem(
    episode: Episode,
    modifier: Modifier = Modifier,
    imageUrl: String? = null,
    showProgress: Boolean = false,
    showPlayedMarker: Boolean = false,
    containerColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    trailingContent: (@Composable () -> Unit)? = null
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = containerColor),
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (imageUrl != null) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = stringResource(R.string.podcast_cover),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
                Spacer(modifier = Modifier.width(8.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (showPlayedMarker && episode.isPlayed) {
                        Text(
                            text = stringResource(R.string.played_marker),
                            color = MaterialTheme.colorScheme.secondary,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                    Text(
                        text = episode.title, 
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    val downloadIcon = when (episode.downloadStatus) {
                        1 -> Icons.Default.HourglassTop
                        2 -> Icons.Default.CheckCircle
                        else -> Icons.Default.CloudDownload
                    }
                    val downloadColor = when (episode.downloadStatus) {
                        1 -> MaterialTheme.colorScheme.tertiary
                        2 -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.outline
                    }
                    
                    Icon(
                        imageVector = downloadIcon,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = downloadColor
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    
                    Text(
                        text = buildString {
                            append(Formatter.formatDate(episode.pubDate))
                            if (episode.duration > 0) {
                                append(" • ")
                                append(Formatter.formatDurationShort(episode.duration))
                            }
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = episode.description, 
                        maxLines = 1, 
                        style = MaterialTheme.typography.bodySmall, 
                        modifier = Modifier.weight(1f),
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (showProgress && episode.lastPlayedPosition > 0 && episode.duration > 0) {
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { episode.lastPlayedPosition.toFloat() / (episode.duration.toFloat() * 1000f).coerceAtLeast(1f) },
                        modifier = Modifier.fillMaxWidth().height(2.dp)
                    )
                }
            }

            if (trailingContent != null) {
                trailingContent()
            }
        }
    }
}
