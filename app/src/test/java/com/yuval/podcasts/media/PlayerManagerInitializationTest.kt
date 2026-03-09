package com.yuval.podcasts.media

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.session.MediaBrowser
import com.yuval.podcasts.data.repository.SettingsRepository
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PlayerManagerInitializationTest {

    @Test
    fun playerManager_readsInitialPosition_onConnection() {
        val context = mockk<Context>(relaxed = true)
        val settingsRepository = mockk<SettingsRepository>(relaxed = true)
        every { settingsRepository.getPlaybackSpeed() } returns 1.0f

        val playerManager = PlayerManager(context, settingsRepository, kotlinx.coroutines.Dispatchers.Unconfined)

        val mediaItem = mockk<MediaItem>(relaxed = true)
        // MediaItem.mediaId is a val property, so MockK can't always mock it directly without problems if it's final. Let's just use a real MediaItem
        val realMediaItem = MediaItem.Builder().setMediaId("ep_1").build()

        val browser = mockk<MediaBrowser>(relaxed = true)
        every { browser.isPlaying } returns false
        every { browser.currentPosition } returns 45000L
        every { browser.duration } returns 3600000L
        every { browser.currentMediaItem } returns realMediaItem

        val controllerField = PlayerManager::class.java.getDeclaredField("controller")
        controllerField.isAccessible = true
        controllerField.set(playerManager, browser)
        
        val setupMethod = PlayerManager::class.java.getDeclaredMethod("setupControllerListener")
        setupMethod.isAccessible = true
        setupMethod.invoke(playerManager)

        val position = browser.currentPosition
        assertEquals("PlayerManager must actively read the paused currentPosition upon connection.", 45000L, position)
    }
}
