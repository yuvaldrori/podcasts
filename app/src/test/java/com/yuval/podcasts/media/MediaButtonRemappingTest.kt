package com.yuval.podcasts.media

import android.content.Intent
import android.view.KeyEvent
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import com.yuval.podcasts.data.Constants
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MediaButtonRemappingTest {

    private lateinit var exoPlayer: ExoPlayer
    private lateinit var service: PlaybackService

    @Before
    fun setup() {
        exoPlayer = mockk(relaxed = true)
        service = PlaybackService()
        service.exoPlayer = exoPlayer
        service.castPlayer = mockk(relaxed = true)
        service.episodeDao = mockk(relaxed = true)
        service.queueDao = mockk(relaxed = true)
        service.removeEpisodeUseCase = mockk(relaxed = true)
        service.settingsRepository = mockk(relaxed = true)
        service.ioDispatcher = Dispatchers.Unconfined
        service.mainDispatcher = Dispatchers.Unconfined

        val field = PlaybackService::class.java.getDeclaredField("currentPlayer")
        field.isAccessible = true
        field.set(service, exoPlayer)
    }

    private fun getCallback(): MediaSession.Callback {
        val field = PlaybackService::class.java.getDeclaredField("mediaSessionCallback")
        field.isAccessible = true
        return field.get(service) as MediaSession.Callback
    }

    @Test
    fun onMediaButtonEvent_Next_SeeksForward() {
        val session = mockk<MediaSession>(relaxed = true)
        val controller = mockk<MediaSession.ControllerInfo>(relaxed = true)
        val intent = Intent(Intent.ACTION_MEDIA_BUTTON).apply {
            putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_NEXT))
        }
        every { exoPlayer.currentPosition } returns 20000L
        every { exoPlayer.duration } returns 100000L

        val handled = getCallback().onMediaButtonEvent(session, controller, intent)

        assertTrue(handled)
        verify(exactly = 1) { exoPlayer.seekTo(20000L + Constants.SEEK_FORWARD_MS) }
    }

    @Test
    fun onMediaButtonEvent_Previous_SeeksBackward() {
        val session = mockk<MediaSession>(relaxed = true)
        val controller = mockk<MediaSession.ControllerInfo>(relaxed = true)
        val intent = Intent(Intent.ACTION_MEDIA_BUTTON).apply {
            putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PREVIOUS))
        }
        every { exoPlayer.currentPosition } returns 40000L

        val handled = getCallback().onMediaButtonEvent(session, controller, intent)

        assertTrue(handled)
        verify(exactly = 1) { exoPlayer.seekTo(40000L - Constants.SEEK_BACKWARD_MS) }
    }
}
