package com.yuval.podcasts.media

import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import io.mockk.mockk
import io.mockk.verify
import io.mockk.every
import io.mockk.slot
import androidx.media3.session.MediaBrowser

@RunWith(RobolectricTestRunner::class)
class PlayerSpeedControllerTest {

    @Test
    fun testStopAndClear() {
        val browser = mockk<MediaBrowser>(relaxed = true)
        val repo = mockk<com.yuval.podcasts.data.repository.SettingsRepository>(relaxed = true)
        every { repo.getPlaybackSpeed() } returns 2.0f
        val manager = PlayerManager(ApplicationProvider.getApplicationContext(), repo, kotlinx.coroutines.Dispatchers.Unconfined)
        
        val controllerField = PlayerManager::class.java.getDeclaredField("controller")
        controllerField.isAccessible = true
        controllerField.set(manager, browser)

        manager.stopAndClear()
        
        // Let's see if this test actually reveals anything
        assertEquals(2.0f, manager.playbackSpeed.value)
    }
}
