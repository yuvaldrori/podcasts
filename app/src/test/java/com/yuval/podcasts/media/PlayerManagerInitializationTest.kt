package com.yuval.podcasts.media

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaBrowser
import com.yuval.podcasts.data.repository.SettingsRepository
import com.yuval.podcasts.utils.LogManager
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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
    fun playerManager_emitsInitialPosition_toExistingCollectorOnConnection() = runTest {
        val context = mockk<Context>(relaxed = true)
        val settingsRepository = mockk<SettingsRepository>(relaxed = true)
        every { settingsRepository.playbackSpeedFlow } returns MutableStateFlow(1.0f)
        val logManager = mockk<LogManager>(relaxed = true)

        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val playerManager = PlayerManager(context, settingsRepository, testDispatcher, testDispatcher, logManager)

        try {
            // Subscribe BEFORE MediaBrowser connects (matching real UI startup while paused)
            var position = -1L
            val job = launch(testDispatcher) {
                playerManager.currentPosition.collect { position = it }
            }
            assertEquals(0L, position)

            val realMediaItem = MediaItem.Builder().setMediaId("ep_1").build()
            val browser = mockk<MediaBrowser>(relaxed = true)
            every { browser.playWhenReady } returns false
            every { browser.playbackState } returns Player.STATE_READY
            every { browser.currentPosition } returns 45000L
            every { browser.duration } returns 3600000L
            every { browser.currentMediaItem } returns realMediaItem

            val controllerField = PlayerManager::class.java.getDeclaredField("controller")
            controllerField.isAccessible = true
            controllerField.set(playerManager, browser)

            val setupMethod = PlayerManager::class.java.getDeclaredMethod("setupControllerListener")
            setupMethod.isAccessible = true
            setupMethod.invoke(playerManager)

            assertEquals(
                "PlayerManager must push the paused currentPosition to an already-subscribed UI upon connection.",
                45000L,
                position
            )
            assertEquals("ep_1", playerManager.currentMediaId.value)
            assertEquals(3600000L, playerManager.duration.value)
            job.cancel()
        } finally {
            playerManager.release()
        }
    }
}
