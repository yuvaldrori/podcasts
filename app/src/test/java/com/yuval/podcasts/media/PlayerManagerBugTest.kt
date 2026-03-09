package com.yuval.podcasts.media

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.session.MediaBrowser
import com.yuval.podcasts.data.repository.SettingsRepository
import com.yuval.podcasts.data.db.entity.Episode
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.lang.reflect.Field
import io.mockk.slot

@RunWith(RobolectricTestRunner::class)
class PlayerManagerBugTest {

    private lateinit var context: Context
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var mediaController: MediaBrowser
    private lateinit var playerManager: PlayerManager

    @Before
    fun setup() {
        context = mockk(relaxed = true)
        settingsRepository = mockk(relaxed = true)
        mediaController = mockk(relaxed = true)

        every { settingsRepository.getPlaybackSpeed() } returns 2.0f
        
        playerManager = PlayerManager(context, settingsRepository, kotlinx.coroutines.Dispatchers.Unconfined)

        val controllerField: Field = PlayerManager::class.java.getDeclaredField("controller")
        controllerField.isAccessible = true
        controllerField.set(playerManager, mediaController)
        
        // Setup listener
        val setupMethod = PlayerManager::class.java.getDeclaredMethod("setupControllerListener")
        setupMethod.isAccessible = true
        setupMethod.invoke(playerManager)
    }

    @Test
    fun testQueueEmpty_ResetsSpeed() {
        val listenerSlot = slot<Player.Listener>()
        verify { mediaController.addListener(capture(listenerSlot)) }
        
        // Simulate playback speed being 2.0
        playerManager.setPlaybackSpeed(2.0f)
        assertEquals(2.0f, playerManager.playbackSpeed.value)

        // Simulate queue end (STATE_ENDED)
        listenerSlot.captured.onPlaybackStateChanged(Player.STATE_ENDED)
        
        // Verify stopAndClear was called
        verify { mediaController.stop() }
        verify { mediaController.clearMediaItems() }
        
        // The bug: does speed reset to 1.0f in PlayerManager?
        // Wait, nothing sets _playbackSpeed.value to 1.0f in stopAndClear!
        // Does it?
        assertEquals(2.0f, playerManager.playbackSpeed.value)
    }
}
