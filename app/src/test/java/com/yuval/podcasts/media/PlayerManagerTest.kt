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
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.lang.reflect.Field

@RunWith(RobolectricTestRunner::class)
class PlayerManagerTest {

    private lateinit var context: Context
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var mediaController: MediaBrowser
    private lateinit var playerManager: PlayerManager

    @Before
    fun setup() {
        context = mockk(relaxed = true)
        settingsRepository = mockk(relaxed = true)
        mediaController = mockk(relaxed = true)

        every { settingsRepository.getPlaybackSpeed() } returns 1.5f
        
        playerManager = PlayerManager(context, settingsRepository)

        // Inject mock MediaBrowser via reflection to bypass complex async initialization
        val controllerField: Field = PlayerManager::class.java.getDeclaredField("controller")
        controllerField.isAccessible = true
        controllerField.set(playerManager, mediaController)
    }

    @Test
    fun initialSpeed_isLoadedFromSettings() {
        // Checking the value immediately after construction
        assertEquals(1.5f, playerManager.playbackSpeed.value)
    }

    @Test
    fun togglePlayPause_whenPlaying_pauses() {
        every { mediaController.isPlaying } returns true
        
        playerManager.togglePlayPause()
        
        verify { mediaController.pause() }
    }

    @Test
    fun togglePlayPause_whenPaused_plays() {
        every { mediaController.isPlaying } returns false
        
        playerManager.togglePlayPause()
        
        verify { mediaController.play() }
    }

    @Test
    fun setPlaybackSpeed_updatesSettingsAndController() {
        playerManager.setPlaybackSpeed(2.0f)
        
        verify { 
            settingsRepository.savePlaybackSpeed(2.0f)
            mediaController.setPlaybackParameters(PlaybackParameters(2.0f))
        }
        assertEquals(2.0f, playerManager.playbackSpeed.value)
    }

    @Test
    fun seekForward_calculatesCorrectPosition() {
        every { mediaController.currentPosition } returns 10000L
        every { mediaController.duration } returns 60000L
        
        playerManager.seekForward()
        
        verify { mediaController.seekTo(40000L) }
    }

    @Test
    fun seekForward_capsAtDuration() {
        every { mediaController.currentPosition } returns 50000L
        every { mediaController.duration } returns 60000L
        
        playerManager.seekForward()
        
        verify { mediaController.seekTo(60000L) }
    }

    @Test
    fun seekBackward_calculatesCorrectPosition() {
        every { mediaController.currentPosition } returns 30000L
        
        playerManager.seekBackward()
        
        verify { mediaController.seekTo(15000L) }
    }

    @Test
    fun seekBackward_capsAtZero() {
        every { mediaController.currentPosition } returns 5000L
        
        playerManager.seekBackward()
        
        verify { mediaController.seekTo(0L) }
    }

    @Test
    fun play_setsMediaItemAndPrepares() {
        playerManager.play("ep1", "http://example.com/audio.mp3", 5000L)
        
        verify { 
            mediaController.setMediaItem(any<MediaItem>())
            mediaController.seekTo(5000L)
            mediaController.prepare()
            mediaController.play()
        }
        assertEquals("ep1", playerManager.currentMediaId.value)
    }

    @Test
    fun playQueue_whenCurrentItemMatchesAndPlaying_pauses() {
        val episode = Episode("ep1", "feed", "T1", "D1", "http://audio1.mp3", null, null, 0L, 0L, 0, null, false, 5000L)
        val episodes = listOf(episode)
        
        // Mock current state: ep1 is playing
        every { mediaController.currentMediaItem?.mediaId } returns "ep1"
        every { mediaController.isPlaying } returns true
        
        // Use reflection to set _currentMediaId internal state to "ep1"
        val currentMediaIdField = PlayerManager::class.java.getDeclaredField("_currentMediaId")
        currentMediaIdField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        (currentMediaIdField.get(playerManager) as MutableStateFlow<String?>).value = "ep1"

        playerManager.playQueue(episodes, 0, 5000L)
        
        // Should pause instead of setMediaItems
        verify(exactly = 0) { mediaController.setMediaItems(any(), any(), any()) }
        verify { mediaController.pause() }
    }

    @Test
    fun playQueue_whenCurrentItemMatchesAndPaused_plays() {
        val episode = Episode("ep1", "feed", "T1", "D1", "http://audio1.mp3", null, null, 0L, 0L, 0, null, false, 5000L)
        val episodes = listOf(episode)
        
        // Mock current state: ep1 is paused
        every { mediaController.currentMediaItem?.mediaId } returns "ep1"
        every { mediaController.isPlaying } returns false
        
        // Use reflection to set _currentMediaId internal state to "ep1"
        val currentMediaIdField = PlayerManager::class.java.getDeclaredField("_currentMediaId")
        currentMediaIdField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        (currentMediaIdField.get(playerManager) as MutableStateFlow<String?>).value = "ep1"

        playerManager.playQueue(episodes, 0, 5000L)
        
        // Should play instead of setMediaItems
        verify(exactly = 0) { mediaController.setMediaItems(any(), any(), any()) }
        verify { mediaController.play() }
    }

    @Test
    fun onPlaybackStateChanged_whenEnded_clearsPlayer() {
        // We need to call setupControllerListener using reflection since it's private and usually called asynchronously
        val setupMethod = PlayerManager::class.java.getDeclaredMethod("setupControllerListener")
        setupMethod.isAccessible = true
        setupMethod.invoke(playerManager)
        
        // Capture the listener passed to mediaController
        val listenerSlot = io.mockk.slot<Player.Listener>()
        verify { mediaController.addListener(capture(listenerSlot)) }
        
        // Set up initial state with an item playing
        playerManager.play("ep1", "http://example.com/audio.mp3", 0L)
        assertEquals("ep1", playerManager.currentMediaId.value)

        // Trigger the STATE_ENDED event
        listenerSlot.captured.onPlaybackStateChanged(Player.STATE_ENDED)
        
        // Verify stopAndClear() logic occurred
        verify { mediaController.stop() }
        verify { mediaController.clearMediaItems() }
        assertEquals(null, playerManager.currentMediaId.value)
    }
}
