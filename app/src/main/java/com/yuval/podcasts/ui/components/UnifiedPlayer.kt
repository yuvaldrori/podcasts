package com.yuval.podcasts.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yuval.podcasts.R
import com.yuval.podcasts.data.Constants
import com.yuval.podcasts.ui.utils.Formatter
import com.yuval.podcasts.ui.viewmodel.PlayerUiState

data class PlayerActions(
    val onToggleSpeed: () -> Unit,
    val onSeekBackward: () -> Unit,
    val onPlayPause: () -> Unit,
    val onSeekForward: () -> Unit,
    val onSeekTo: (Long) -> Unit
)

@Composable
fun UnifiedPlayer(
    uiState: PlayerUiState,
    actions: PlayerActions,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.navigationBarsPadding(),
        tonalElevation = 8.dp,
        shadowElevation = 8.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp)
        ) {
            // Top Row: Title and Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Episode Title
                Text(
                    text = uiState.currentEpisode?.title ?: stringResource(R.string.not_playing),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(end = 8.dp)
                )

                // Controls
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End
                ) {
                    val themeColor = MaterialTheme.colorScheme.primary
                    CastButton(tint = themeColor)
                    
                    val speedLabel = stringResource(R.string.playback_speed)
                    TextButton(
                        onClick = actions.onToggleSpeed,
                        enabled = uiState.isConnected,
                        contentPadding = PaddingValues(0.dp),
                        modifier = Modifier
                            .defaultMinSize(minWidth = Constants.PLAYER_BUTTON_MIN_SIZE_DP.dp, minHeight = Constants.PLAYER_BUTTON_MIN_SIZE_DP.dp)
                            .semantics { contentDescription = speedLabel },
                        colors = ButtonDefaults.textButtonColors(contentColor = themeColor)
                    ) {
                        val speedText = if (uiState.playbackSpeed % 1f == 0f) uiState.playbackSpeed.toInt().toString() else uiState.playbackSpeed.toString()
                        Text(text = stringResource(R.string.playback_speed_format, speedText), style = MaterialTheme.typography.labelLarge)
                    }
                    IconButton(
                        onClick = actions.onSeekBackward,
                        enabled = uiState.isConnected,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FastRewind, 
                            contentDescription = stringResource(R.string.fast_rewind), 
                            modifier = Modifier.size(28.dp),
                            tint = themeColor
                        )
                    }
                    IconButton(
                        onClick = actions.onPlayPause,
                        enabled = uiState.isConnected,
                        modifier = Modifier.size(Constants.MINI_PLAYER_HEIGHT_DP.dp)
                    ) {
                        Icon(
                            imageVector = if (uiState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (uiState.isPlaying) stringResource(R.string.pause) else stringResource(R.string.play),
                            modifier = Modifier.size(Constants.PLAYER_ART_SIZE_DP.dp),
                            tint = themeColor
                        )
                    }
                    IconButton(
                        onClick = actions.onSeekForward,
                        enabled = uiState.isConnected,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FastForward, 
                            contentDescription = stringResource(R.string.fast_forward), 
                            modifier = Modifier.size(28.dp),
                            tint = themeColor
                        )
                    }
                }
            }

            // Bottom Row: Progress Slider and Times
            Column(modifier = Modifier.fillMaxWidth()) {
                var sliderValue by remember { mutableFloatStateOf(0f) }
                var isDragging by remember { mutableStateOf(false) }
                
                val currentProgress = if (uiState.duration > 0) uiState.currentPosition.toFloat() / uiState.duration.toFloat() else 0f
                
                LaunchedEffect(uiState.currentPosition, uiState.duration) {
                    if (!isDragging) {
                        sliderValue = currentProgress
                    }
                }

                Slider(
                    value = sliderValue,
                    onValueChange = { 
                        isDragging = true
                        sliderValue = it 
                    },
                    onValueChangeFinished = {
                        isDragging = false
                        actions.onSeekTo((sliderValue * uiState.duration).toLong())
                    },
                    modifier = Modifier.fillMaxWidth().height(32.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val displayPosition = if (isDragging) (sliderValue * uiState.duration).toLong() else uiState.currentPosition
                    Text(
                        text = Formatter.formatTime(displayPosition),
                        style = MaterialTheme.typography.labelSmall
                    )
                    Text(
                        text = Formatter.formatTime(uiState.duration),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
}
