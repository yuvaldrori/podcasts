package com.yuval.podcasts.media

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.session.MediaBrowser
import com.yuval.podcasts.data.repository.SettingsRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class PlayerManagerInitializationTest {

    @Test
    fun playerManager_readsInitialPosition_onConnection() = runTest {
        val context = mockk<Context>(relaxed = true)
        val settingsRepository = mockk<SettingsRepository>(relaxed = true)
        val logManager = mockk<com.yuval.podcasts.utils.LogManager>(relaxed = true)
        coEvery { settingsRepository.getPlaybackSpeed() } returns 1.0f

        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val playerManager = PlayerManager(context, settingsRepository, testDispatcher, testDispatcher, logManager)

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

        var position = 0L
        val job = launch(testDispatcher) {
            playerManager.currentPosition.collect { position = it }
        }

        assertEquals("PlayerManager must actively read the paused currentPosition upon connection.", 45000L, position)
        job.cancel()
    }
}
