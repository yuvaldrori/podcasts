package com.yuval.podcasts.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.yuval.podcasts.data.db.entity.Episode

@Composable
fun EpisodeItem(
    episode: Episode,
    showProgress: Boolean = false,
    showPlayedMarker: Boolean = false,
    actionIcon: (@Composable () -> Unit)? = null,
    onActionClick: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
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
                
                Text(text = episode.description, maxLines = 1, style = MaterialTheme.typography.bodySmall)

                if (showProgress && episode.lastPlayedPosition > 0 && episode.duration > 0) {
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { episode.lastPlayedPosition.toFloat() / episode.duration.toFloat() },
                        modifier = Modifier.fillMaxWidth().height(2.dp)
                    )
                }
            }

            if (actionIcon != null && onActionClick != null) {
                IconButton(onClick = onActionClick) {
                    actionIcon()
                }
            }
        }
    }
}
