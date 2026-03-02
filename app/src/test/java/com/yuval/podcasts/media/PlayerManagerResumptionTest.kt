package com.yuval.podcasts.media

import androidx.media3.common.Player
import androidx.media3.session.MediaBrowser
import com.yuval.podcasts.data.repository.SettingsRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test
import kotlinx.coroutines.test.runTest

class PlayerManagerResumptionTest {

    @Test
    fun setupController_requestsResumptionIfEmpty() = runTest {
        val settingsRepository = mockk<SettingsRepository>(relaxed = true)
        every { settingsRepository.getPlaybackSpeed() } returns 1f
        val playerManager = PlayerManager(mockk(relaxed = true), settingsRepository)

        val mockController = mockk<MediaBrowser>(relaxed = true)
        
        // Setup initial states to simulate an empty player
        every { mockController.isPlaying } returns false
        every { mockController.duration } returns 0L
        every { mockController.currentMediaItem } returns null
        // Media3 requires 0 media items to trigger custom resumption or explicitly using browser
        every { mockController.mediaItemCount } returns 0

        // Use reflection to bypass future initialization for test isolation
        val controllerField = PlayerManager::class.java.getDeclaredField("controller")
        controllerField.isAccessible = true
        controllerField.set(playerManager, mockController)

        val setupMethod = PlayerManager::class.java.getDeclaredMethod("setupControllerListener")
        setupMethod.isAccessible = true
        setupMethod.invoke(playerManager)

        // It should detect the empty state and explicitly ask the controller to load the resumption list 
        // by calling play() or prepare() without a set item, or utilizing MediaController's built-in resumption intents.
        // Actually, MediaController natively supports `prepare()` triggering onPlaybackResumption if the playlist is empty.
        verify { mockController.prepare() }
    }
}
