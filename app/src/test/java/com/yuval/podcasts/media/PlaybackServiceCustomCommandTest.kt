package com.yuval.podcasts.media

import android.os.Bundle
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.yuval.podcasts.data.repository.SettingsRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

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

        service.exoPlayer = exoPlayer
        service.castPlayer = mockk(relaxed = true)
        service.episodeDao = mockk(relaxed = true)
        service.queueDao = mockk(relaxed = true)
        service.removeEpisodeUseCase = mockk(relaxed = true)
        service.settingsRepository = settingsRepository
        service.ioDispatcher = Dispatchers.Unconfined
        service.mainDispatcher = Dispatchers.Unconfined

        val field = PlaybackService::class.java.getDeclaredField("currentPlayer")
        field.isAccessible = true
        field.set(service, exoPlayer)
    }

    @Test
    fun onCustomCommand_REWIND_10_seeksBackwardAndReturnsSuccess() {
        val callback = getCallback(service)
        val session = mockk<MediaSession>(relaxed = true)
        val controller = mockk<MediaSession.ControllerInfo>(relaxed = true)
        val command = SessionCommand("REWIND_10", Bundle.EMPTY)

        every { exoPlayer.currentPosition } returns 50000L

        val result = callback.onCustomCommand(session, controller, command, Bundle.EMPTY).get()

        assertEquals(SessionResult.RESULT_SUCCESS, result.resultCode)
        verify { exoPlayer.seekTo(40000L) }
    }

    @Test
    fun onCustomCommand_SKIP_30_seeksForwardAndReturnsSuccess() {
        val callback = getCallback(service)
        val session = mockk<MediaSession>(relaxed = true)
        val controller = mockk<MediaSession.ControllerInfo>(relaxed = true)
        val command = SessionCommand("SKIP_30", Bundle.EMPTY)

        every { exoPlayer.currentPosition } returns 50000L
        every { exoPlayer.duration } returns 100000L

        val result = callback.onCustomCommand(session, controller, command, Bundle.EMPTY).get()

        assertEquals(SessionResult.RESULT_SUCCESS, result.resultCode)
        verify { exoPlayer.seekTo(80000L) }
    }

    private fun getCallback(service: PlaybackService): MediaSession.Callback {
        val field = PlaybackService::class.java.getDeclaredField("mediaSessionCallback")
        field.isAccessible = true
        return field.get(service) as MediaSession.Callback
    }
}
