package com.yuval.podcasts.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yuval.podcasts.R
import com.yuval.podcasts.data.db.entity.DownloadStatus
import com.yuval.podcasts.data.db.entity.Episode
import kotlin.time.Duration.Companion.seconds
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
                PodcastCover(model = imageUrl)
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
                    val status = DownloadStatus.fromInt(episode.downloadStatus)
                    val downloadIcon = when (status) {
                        DownloadStatus.DOWNLOADING -> Icons.Default.HourglassTop
                        DownloadStatus.DOWNLOADED -> Icons.Default.CheckCircle
                        DownloadStatus.NOT_DOWNLOADED -> Icons.Default.CloudDownload
                    }
                    val downloadColor = when (status) {
                        DownloadStatus.DOWNLOADING -> MaterialTheme.colorScheme.tertiary
                        DownloadStatus.DOWNLOADED -> MaterialTheme.colorScheme.primary
                        DownloadStatus.NOT_DOWNLOADED -> MaterialTheme.colorScheme.outline
                    }
                    val downloadDesc = when (status) {
                        DownloadStatus.DOWNLOADING -> stringResource(R.string.downloading)
                        DownloadStatus.DOWNLOADED -> stringResource(R.string.downloaded)
                        DownloadStatus.NOT_DOWNLOADED -> stringResource(R.string.not_downloaded)
                    }
                    
                    if (status == DownloadStatus.DOWNLOADING) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .size(14.dp)
                                .semantics { contentDescription = downloadDesc },
                            strokeWidth = 2.dp,
                            color = downloadColor
                        )
                    } else {
                        Icon(
                            imageVector = downloadIcon,
                            contentDescription = downloadDesc,
                            modifier = Modifier.size(14.dp),
                            tint = downloadColor
                        )
                    }
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
                        progress = { episode.progress },
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
