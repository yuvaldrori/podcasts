package com.yuval.podcasts.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yuval.podcasts.data.Constants
import com.yuval.podcasts.data.db.entity.Episode
import com.yuval.podcasts.data.repository.PodcastRepository
import com.yuval.podcasts.media.PlayerManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlin.time.Duration.Companion.seconds

data class PlayerUiState(
    val isPlaying: Boolean = false,
    val isConnected: Boolean = false,
    val currentPosition: Long = 0L,
    val duration: Long = 0L,
    val playbackSpeed: Float = 1.0f,
    val currentEpisode: Episode? = null
)

private data class PlaybackStatus(
    val isPlaying: Boolean,
    val isConnected: Boolean,
    val currentPosition: Long,
    val duration: Long,
    val playbackSpeed: Float
)

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val repository: PodcastRepository,
    private val playerManager: PlayerManager,
    private val enqueueEpisodeUseCase: com.yuval.podcasts.domain.usecase.EnqueueEpisodeUseCase,
    @param:com.yuval.podcasts.di.IoDispatcher private val ioDispatcher: kotlinx.coroutines.CoroutineDispatcher,
    @param:com.yuval.podcasts.di.MainDispatcher private val mainDispatcher: kotlinx.coroutines.CoroutineDispatcher
) : ViewModel() {

    private val playbackStatusFlow = combine(
        playerManager.isPlaying,
        playerManager.isInitialized,
        playerManager.currentPosition,
        playerManager.duration,
        playerManager.playbackSpeed
    ) { isPlaying, isInitialized, currentPosition, duration, playbackSpeed ->
        PlaybackStatus(isPlaying, isInitialized, currentPosition, duration, playbackSpeed)
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val currentEpisodeFlow = playerManager.currentMediaId.flatMapLatest { id ->
        if (id == null) flowOf(null) else repository.getEpisodeByIdFlow(id)
    }

    val uiState: StateFlow<PlayerUiState> = combine(
        playbackStatusFlow,
        currentEpisodeFlow
    ) { status, currentEpisode ->
        val finalDuration = if (status.duration > 0) status.duration else (currentEpisode?.duration?.seconds?.inWholeMilliseconds ?: 0L)
        
        PlayerUiState(
            isPlaying = status.isPlaying,
            isConnected = status.isConnected,
            currentPosition = status.currentPosition,
            duration = finalDuration,
            playbackSpeed = status.playbackSpeed,
            currentEpisode = currentEpisode
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(Constants.FLOW_STOP_TIMEOUT_MS),
        initialValue = PlayerUiState()
    )

    init {
        playerManager.initialize()
    }

    fun playPause() {
        playerManager.togglePlayPause()
    }

    fun play(episode: Episode, startPositionMs: Long = episode.lastPlayedPosition) {
        viewModelScope.launch(ioDispatcher) {
            val uri = episode.playableUri
            if (episode.localFilePath != null) {
                verifyAndEnqueueLocalFile(episode)
            }
            val podcast = repository.getPodcast(episode.podcastFeedUrl)
            val finalImageUrl = episode.imageUrl ?: podcast?.imageUrl
            withContext(mainDispatcher) {
                playerManager.play(
                    mediaId = episode.id,
                    uri = uri,
                    title = episode.title,
                    imageUrl = finalImageUrl,
                    startPositionMs = startPositionMs,
                    artist = podcast?.title ?: episode.podcastFeedUrl,
                    albumTitle = podcast?.title
                )
            }
        }
    }

    fun playQueue(episodes: List<Episode>, startIndex: Int, startPositionMs: Long = 0L) {
        viewModelScope.launch(ioDispatcher) {
            episodes.forEach { episode ->
                verifyAndEnqueueLocalFile(episode)
            }
            withContext(mainDispatcher) {
                playerManager.playQueue(episodes, startIndex, startPositionMs)
            }
        }
    }

    fun seekTo(position: Long) {
        playerManager.seekTo(position)
    }

    fun setSpeed(speed: Float) {
        playerManager.setPlaybackSpeed(speed)
    }

    fun toggleSpeed() {
        val currentSpeed = playerManager.playbackSpeed.value
        val newSpeed = if (currentSpeed >= PLAYBACK_SPEED_FAST) PLAYBACK_SPEED_NORMAL else PLAYBACK_SPEED_FAST
        setSpeed(newSpeed)
    }

    fun seekForward() {
        playerManager.seekForward()
    }

    fun seekBackward() {
        playerManager.seekBackward()
    }

    fun seekToChapter(episode: Episode, chapter: com.yuval.podcasts.data.db.entity.Chapter) {
        // Only an in-place seek if this chapter's episode is the one currently loaded;
        // otherwise start that episode from the chapter offset instead of seeking whatever
        // happens to be playing.
        if (playerManager.currentMediaId.value == episode.id) {
            playerManager.seekTo(chapter.startTimeMs)
        } else {
            play(episode, startPositionMs = chapter.startTimeMs)
        }
    }

    private suspend fun verifyAndEnqueueLocalFile(episode: Episode) {
        if (episode.localFilePath != null && !java.io.File(episode.localFilePath).exists()) {
            enqueueEpisodeUseCase(episode)
        }
    }

    companion object {
        private const val PLAYBACK_SPEED_NORMAL = 1.0f
        private const val PLAYBACK_SPEED_FAST = 2.0f
    }
}
