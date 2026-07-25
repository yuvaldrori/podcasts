package com.yuval.podcasts.media

import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import com.yuval.podcasts.data.Constants
import com.yuval.podcasts.data.db.dao.EpisodeDao
import com.yuval.podcasts.data.repository.SettingsRepository
import com.yuval.podcasts.domain.usecase.RemoveEpisodeUseCase
import com.yuval.podcasts.utils.LogManager
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal class PlaybackServicePlayerListener(
    private val removeEpisodeUseCase: RemoveEpisodeUseCase,
    private val episodeDao: EpisodeDao,
    private val settingsRepository: SettingsRepository,
    private val logManager: LogManager,
    private val serviceScope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val mainDispatcher: CoroutineDispatcher = Dispatchers.Main,
    private val getCurrentPlayer: () -> Player,
    private val setupLoudnessEnhancer: (Int) -> Unit = {},
    private val onSavePosition: (String?, Long?) -> Unit = { _, _ -> },
    initialCurrentlyPlayingId: String? = null
) : Player.Listener {

    var currentlyPlayingId: String? = initialCurrentlyPlayingId
    var lastResumedId: String? = null

    override fun onAudioSessionIdChanged(audioSessionId: Int) {
        setupLoudnessEnhancer(audioSessionId)
    }

    override fun onPositionDiscontinuity(
        oldPosition: Player.PositionInfo,
        newPosition: Player.PositionInfo,
        reason: Int
    ) {
        val oldMediaId = oldPosition.mediaItem?.mediaId
        val newMediaId = newPosition.mediaItem?.mediaId
        if (oldMediaId != null) {
            if (oldMediaId != newMediaId) {
                onSavePosition(oldMediaId, oldPosition.positionMs)
            } else if (reason == Player.DISCONTINUITY_REASON_SEEK) {
                onSavePosition(oldMediaId, newPosition.positionMs)
            }
        }
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        if (!isPlaying) {
            val player = getCurrentPlayer()
            if (player.playbackState != Player.STATE_ENDED) {
                onSavePosition(null, null)
            }
        }
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        if (playbackState == Player.STATE_ENDED) {
            val lastId = currentlyPlayingId
            logManager.i("PlaybackService", "Playback ended. lastId=$lastId")
            if (lastId != null) {
                serviceScope.launch(ioDispatcher) {
                    removeEpisodeUseCase(lastId, markAsPlayed = true)
                }
                currentlyPlayingId = null
            }
        }
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        val lastId = currentlyPlayingId
        logManager.i("PlaybackService", "Media item transition. lastId=$lastId, newMediaId=${mediaItem?.mediaId}, reason=$reason")

        val isAutoTransition = reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO
        val isRepeatTransition = reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT

        if (lastId != null && (isAutoTransition || isRepeatTransition)) {
            if (mediaItem == null || lastId != mediaItem.mediaId || isRepeatTransition) {
                serviceScope.launch(ioDispatcher) {
                    removeEpisodeUseCase(lastId, markAsPlayed = true)
                }
            }
        }

        if (mediaItem != null) {
            currentlyPlayingId = mediaItem.mediaId
            serviceScope.launch(ioDispatcher) {
                settingsRepository.saveLastPlayedEpisodeId(mediaItem.mediaId)
            }

            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO ||
                reason == Player.MEDIA_ITEM_TRANSITION_REASON_SEEK ||
                reason == Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED
            ) {
                if (lastResumedId != mediaItem.mediaId) {
                    lastResumedId = mediaItem.mediaId
                    serviceScope.launch(ioDispatcher) {
                        val episode = episodeDao.getEpisodeById(mediaItem.mediaId)
                        if (episode != null && episode.lastPlayedPosition > 0) {
                            withContext(mainDispatcher) {
                                val player = getCurrentPlayer()
                                val currentItem = player.currentMediaItem
                                val isSameItem = currentItem == null || currentItem.mediaId.isNullOrEmpty() || currentItem.mediaId == mediaItem.mediaId
                                if (isSameItem && player.currentPosition < Constants.SEEK_POSITION_RESTORATION_THRESHOLD_MS) {
                                    player.seekTo(episode.lastPlayedPosition)
                                }
                            }
                        }
                    }
                }
            }
        } else {
            currentlyPlayingId = null
        }
    }

    override fun onPlayerError(error: PlaybackException) {
        val player = getCurrentPlayer()
        logManager.e(
            "PlaybackService", "Player error: ${error.message}", mapOf(
                "errorCode" to error.errorCodeName,
                "mediaId" to (player.currentMediaItem?.mediaId ?: "none")
            )
        )
    }
}
