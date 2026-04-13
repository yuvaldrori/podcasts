package com.yuval.podcasts.media

import android.os.Bundle
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import com.yuval.podcasts.data.repository.SettingsRepository
import com.yuval.podcasts.domain.usecase.RemoveEpisodeUseCase
import io.mockk.*
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import javax.inject.Inject

@RunWith(RobolectricTestRunner::class)
class PlaybackServiceCustomCommandTest {

    private lateinit var exoPlayer: ExoPlayer
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var service: PlaybackService

    @Before
    fun setup() {
        exoPlayer = mockk(relaxed = true)
        settingsRepository = mockk(relaxed = true)
        service = PlaybackService()
        
        // Inject mocks into the service
        service.exoPlayer = exoPlayer
        service.castPlayer = mockk(relaxed = true)
        service.episodeDao = mockk(relaxed = true)
        service.queueDao = mockk(relaxed = true)
        service.removeEpisodeUseCase = mockk(relaxed = true)
        service.settingsRepository = settingsRepository
        service.ioDispatcher = Dispatchers.Unconfined
        service.mainDispatcher = Dispatchers.Unconfined
        
        // Initialize private currentPlayer
        val field = PlaybackService::class.java.getDeclaredField("currentPlayer")
        field.isAccessible = true
        field.set(service, exoPlayer)
    }

    @Test
    fun onCustomCommand_REWIND_10_seeksBackward() = runTest {
        val callback = getCallback(service)
        val session = mockk<MediaSession>(relaxed = true)
        val controller = mockk<MediaSession.ControllerInfo>(relaxed = true)
        val command = SessionCommand("REWIND_10", Bundle.EMPTY)
        
        every { exoPlayer.currentPosition } returns 50000L
        
        callback.onCustomCommand(session, controller, command, Bundle.EMPTY)
        
        verify { exoPlayer.seekTo(40000L) }
    }

    @Test
    fun onCustomCommand_SKIP_30_seeksForward() = runTest {
        val callback = getCallback(service)
        val session = mockk<MediaSession>(relaxed = true)
        val controller = mockk<MediaSession.ControllerInfo>(relaxed = true)
        val command = SessionCommand("SKIP_30", Bundle.EMPTY)
        
        every { exoPlayer.currentPosition } returns 50000L
        every { exoPlayer.duration } returns 100000L
        
        callback.onCustomCommand(session, controller, command, Bundle.EMPTY)
        
        verify { exoPlayer.seekTo(80000L) }
    }

    private fun getCallback(service: PlaybackService): MediaSession.Callback {
        val field = PlaybackService::class.java.getDeclaredField("mediaSessionCallback")
        field.isAccessible = true
        return field.get(service) as MediaSession.Callback
    }
}
