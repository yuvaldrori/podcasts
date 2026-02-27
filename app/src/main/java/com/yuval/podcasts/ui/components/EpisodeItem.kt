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
import coil.compose.AsyncImage
import com.yuval.podcasts.data.db.entity.Episode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun EpisodeItem(
    episode: Episode,
    modifier: Modifier = Modifier,
    imageUrl: String? = null,
    showProgress: Boolean = false,
    showPlayedMarker: Boolean = false,
    trailingContent: (@Composable () -> Unit)? = null
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (imageUrl != null) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = "Podcast Cover",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
                Spacer(modifier = Modifier.width(12.dp))
            }
            
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (showPlayedMarker && episode.isPlayed) {
                        Text(
                            text = "[Played] ",
                            color = MaterialTheme.colorScheme.secondary,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                    Text(text = episode.title, style = MaterialTheme.typography.titleSmall)
                }
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val downloadIcon = when (episode.downloadStatus) {
                        1 -> Icons.Default.HourglassTop // Downloading
                        2 -> Icons.Default.CheckCircle // Downloaded
                        else -> Icons.Default.CloudDownload // Not Downloaded
                    }
                    val downloadColor = when (episode.downloadStatus) {
                        1 -> MaterialTheme.colorScheme.tertiary
                        2 -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.outline
                    }
                    
                    Icon(
                        imageVector = downloadIcon,
                        contentDescription = "Download Status",
                        modifier = Modifier.size(14.dp),
                        tint = downloadColor
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    
                    Text(
                        text = formatDate(episode.pubDate),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = episode.description, maxLines = 1, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                }

                if (showProgress && episode.lastPlayedPosition > 0 && episode.duration > 0) {
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { episode.lastPlayedPosition.toFloat() / episode.duration.toFloat() },
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

private fun formatDate(timestamp: Long): String {
    if (timestamp == 0L) return ""
    val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
