package com.yuval.podcasts.media

import android.content.Intent
import android.view.KeyEvent
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MediaButtonRemappingTest {

    @Test
    fun onMediaButtonEvent_Next_SeeksForward() {
        val service = PlaybackService()
        val intent = Intent(Intent.ACTION_MEDIA_BUTTON).apply {
            putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_NEXT))
        }

        val onSeekForward = mockk<() -> Unit>(relaxed = true)
        val onSeekBackward = mockk<() -> Unit>(relaxed = true)

        val handled = service.handleMediaButtonIntent(intent, onSeekForward, onSeekBackward)

        assertTrue(handled)
        verify { onSeekForward() }
        verify(exactly = 0) { onSeekBackward() }
    }

    @Test
    fun onMediaButtonEvent_Previous_SeeksBackward() {
        val service = PlaybackService()
        val intent = Intent(Intent.ACTION_MEDIA_BUTTON).apply {
            putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PREVIOUS))
        }

        val onSeekForward = mockk<() -> Unit>(relaxed = true)
        val onSeekBackward = mockk<() -> Unit>(relaxed = true)

        val handled = service.handleMediaButtonIntent(intent, onSeekForward, onSeekBackward)

        assertTrue(handled)
        verify { onSeekBackward() }
        verify(exactly = 0) { onSeekForward() }
    }
}
