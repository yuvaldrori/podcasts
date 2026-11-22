package com.yuval.podcasts.media

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.session.MediaBrowser
import com.yuval.podcasts.data.repository.SettingsRepository
import com.yuval.podcasts.data.db.entity.Episode
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
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
    private lateinit var logManager: com.yuval.podcasts.utils.LogManager
    private lateinit var playerManager: PlayerManager

    @Before
    fun setup() {
        context = mockk(relaxed = true)
        settingsRepository = mockk(relaxed = true)
        mediaController = mockk(relaxed = true)
        logManager = mockk(relaxed = true)

        every { settingsRepository.playbackSpeedFlow } returns kotlinx.coroutines.flow.flowOf(1.5f)
        
        playerManager = PlayerManager(context, settingsRepository, kotlinx.coroutines.Dispatchers.Unconfined, kotlinx.coroutines.Dispatchers.Unconfined, logManager)

        // Inject mock MediaBrowser via reflection to bypass complex async initialization
        val controllerField: Field = PlayerManager::class.java.getDeclaredField("controller")
        controllerField.isAccessible = true
        controllerField.set(playerManager, mediaController)
    }

    @After
    fun teardown() {
        playerManager.release()
    }

    @Test
    fun initialSpeed_isLoadedFromSettings() {
        // Checking the value immediately after construction
        assertEquals(1.5f, playerManager.playbackSpeed.value)
    }

    @Test
    fun togglePlayPause_whenPlaying_pauses() {
        every { mediaController.playWhenReady } returns true
        
        playerManager.togglePlayPause()
        
        verify { mediaController.pause() }
    }

    @Test
    fun togglePlayPause_whenPaused_plays() {
        every { mediaController.playWhenReady } returns false
        
        playerManager.togglePlayPause()
        
        verify { mediaController.play() }
    }

    @Test
    fun setPlaybackSpeed_updatesSettingsAndController() = runTest {
        playerManager.setPlaybackSpeed(2.0f)
        
        coVerify { 
            settingsRepository.savePlaybackSpeed(2.0f)
        }
        verify {
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
    fun seekForward_whenDurationUnknown_doesNotSeek() {
        every { mediaController.currentPosition } returns 10000L
        every { mediaController.duration } returns androidx.media3.common.C.TIME_UNSET
        
        playerManager.seekForward()
        
        verify(exactly = 0) { mediaController.seekTo(any()) }
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
        playerManager.play(mediaId = "ep1", uri = "http://example.com/audio.mp3", startPositionMs = 5000L)
        
        verify { 
            mediaController.setMediaItem(any<MediaItem>())
            mediaController.seekTo(5000L)
            mediaController.prepare()
            mediaController.play()
        }
        assertEquals("ep1", playerManager.currentMediaId.value)
    }

    @Test
    fun playQueue_alwaysSetsMediaItemsAndPlays() {
        val podcast = com.yuval.podcasts.data.db.entity.Podcast("feed", "Podcast Name", "Desc", "https://art.test/cover.png", "https://web.test")
        val episode = Episode("ep1", "feed", "T1", "D1", "http://audio1.mp3", null, null, 0L, 0L, 0, null, false, 5000L)
        val episodes = listOf(com.yuval.podcasts.data.db.entity.EpisodeWithPodcast(episode, podcast))
        
        // Mock current state: ep1 is playing
        every { mediaController.currentMediaItem?.mediaId } returns "ep1"
        every { mediaController.playWhenReady } returns true
        
        // Use reflection to set _currentMediaId internal state to "ep1"
        val currentMediaIdField = PlayerManager::class.java.getDeclaredField("_currentMediaId")
        currentMediaIdField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        (currentMediaIdField.get(playerManager) as MutableStateFlow<String?>).value = "ep1"

        val itemsSlot = io.mockk.slot<List<MediaItem>>()
        playerManager.playQueue(episodes, 0, 5000L)
        
        // Should NOT shortcut to pause anymore, and should include podcast metadata
        verify { mediaController.setMediaItems(capture(itemsSlot), 0, 5000L) }
        assertEquals("Podcast Name", itemsSlot.captured[0].mediaMetadata.artist)
        assertEquals("https://art.test/cover.png", itemsSlot.captured[0].mediaMetadata.artworkUri?.toString())
        verify { mediaController.play() }
        verify(exactly = 0) { mediaController.pause() }
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
        playerManager.play(mediaId = "ep1", uri = "http://example.com/audio.mp3", startPositionMs = 0L)
        assertEquals("ep1", playerManager.currentMediaId.value)

        // Trigger the STATE_ENDED event
        listenerSlot.captured.onPlaybackStateChanged(Player.STATE_ENDED)
        
        // Verify stopAndClear() logic occurred
        verify { mediaController.stop() }
        verify { mediaController.clearMediaItems() }
        assertEquals(null, playerManager.currentMediaId.value)
    }

    @Test
    fun onPlaybackStateChanged_whenBufferingWithPlayWhenReady_isPlayingRemainsTrue() {
        val setupMethod = PlayerManager::class.java.getDeclaredMethod("setupControllerListener")
        setupMethod.isAccessible = true
        setupMethod.invoke(playerManager)

        val listenerSlot = io.mockk.slot<Player.Listener>()
        verify { mediaController.addListener(capture(listenerSlot)) }

        every { mediaController.playWhenReady } returns true
        every { mediaController.playbackState } returns Player.STATE_BUFFERING

        listenerSlot.captured.onPlaybackStateChanged(Player.STATE_BUFFERING)

        assertEquals(true, playerManager.isPlaying.value)
    }

    @Test
    fun onIsPlayingChanged_whenFalseDuringBufferingWithPlayWhenReady_isPlayingRemainsTrue() {
        val setupMethod = PlayerManager::class.java.getDeclaredMethod("setupControllerListener")
        setupMethod.isAccessible = true
        setupMethod.invoke(playerManager)

        val listenerSlot = io.mockk.slot<Player.Listener>()
        verify { mediaController.addListener(capture(listenerSlot)) }

        every { mediaController.playWhenReady } returns true
        every { mediaController.playbackState } returns Player.STATE_BUFFERING

        listenerSlot.captured.onIsPlayingChanged(false)

        assertEquals(true, playerManager.isPlaying.value)
    }

    @Test
    fun onPlayWhenReadyChanged_whenFalse_isPlayingBecomesFalse() {
        // Start in a playing state so the transition to false is genuinely verified
        every { mediaController.playWhenReady } returns true
        every { mediaController.playbackState } returns Player.STATE_READY

        val setupMethod = PlayerManager::class.java.getDeclaredMethod("setupControllerListener")
        setupMethod.isAccessible = true
        setupMethod.invoke(playerManager)
        assertEquals(true, playerManager.isPlaying.value)

        val listenerSlot = io.mockk.slot<Player.Listener>()
        verify { mediaController.addListener(capture(listenerSlot)) }

        every { mediaController.playWhenReady } returns false
        every { mediaController.playbackState } returns Player.STATE_READY

        listenerSlot.captured.onPlayWhenReadyChanged(false, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)

        assertEquals(false, playerManager.isPlaying.value)
    }

    @Test
    fun stopAndClear_resetsStateAndClearsController() = runTest {
        var controllerPos = 45000L
        every { mediaController.playWhenReady } returns true
        every { mediaController.playbackState } returns Player.STATE_READY
        every { mediaController.currentPosition } answers { controllerPos }
        every { mediaController.duration } returns 120000L
        every { mediaController.currentMediaItem } returns MediaItem.Builder().setMediaId("ep1").build()
        every { mediaController.stop() } answers { controllerPos = 0L }

        val setupMethod = PlayerManager::class.java.getDeclaredMethod("setupControllerListener")
        setupMethod.isAccessible = true
        setupMethod.invoke(playerManager)

        var observedPos = -1L
        val posJob = launch(UnconfinedTestDispatcher(testScheduler)) {
            playerManager.currentPosition.collect { observedPos = it }
        }

        // Verify non-default preconditions before stopAndClear()
        assertEquals("ep1", playerManager.currentMediaId.value)
        assertEquals(true, playerManager.isPlaying.value)
        assertEquals(45000L, observedPos)
        assertEquals(120000L, playerManager.duration.value)

        playerManager.stopAndClear()

        verify { mediaController.stop() }
        verify { mediaController.clearMediaItems() }
        assertEquals(null, playerManager.currentMediaId.value)
        assertEquals(false, playerManager.isPlaying.value)
        assertEquals(0L, observedPos)
        assertEquals(0L, playerManager.duration.value)
        posJob.cancel()
    }

    @Test
    fun release_doesNotCancelSingletonScope_subsequentOperationsSucceed() = runTest {
        playerManager.release()

        val newController = mockk<MediaBrowser>(relaxed = true)
        val controllerField: Field = PlayerManager::class.java.getDeclaredField("controller")
        controllerField.isAccessible = true
        controllerField.set(playerManager, newController)

        playerManager.setPlaybackSpeed(2.0f)

        coVerify { settingsRepository.savePlaybackSpeed(2.0f) }
        verify { newController.setPlaybackParameters(PlaybackParameters(2.0f)) }
    }

    @Test
    fun onPositionDiscontinuity_doesNotConflateWhenNewPositionMatchesTriggerCounter() = runTest {
        var controllerPos = 10_000L
        every { mediaController.playWhenReady } returns false
        every { mediaController.playbackState } returns Player.STATE_READY
        every { mediaController.currentPosition } answers { controllerPos }

        val setupMethod = PlayerManager::class.java.getDeclaredMethod("setupControllerListener")
        setupMethod.isAccessible = true
        setupMethod.invoke(playerManager)

        val listenerSlot = io.mockk.slot<Player.Listener>()
        verify { mediaController.addListener(capture(listenerSlot)) }

        var observedPos = -1L
        val posJob = launch(UnconfinedTestDispatcher(testScheduler)) {
            playerManager.currentPosition.collect { observedPos = it }
        }
        assertEquals(10_000L, observedPos)

        // setupControllerListener incremented positionTrigger to 1L.
        // Seeking to 1L must still emit 1L rather than being conflated by MutableStateFlow.
        controllerPos = 1L
        val oldInfo = Player.PositionInfo(null, 0, null, null, 0, 10_000L, 10_000L, -1, -1)
        val newInfo = Player.PositionInfo(null, 0, null, null, 0, 1L, 1L, -1, -1)
        listenerSlot.captured.onPositionDiscontinuity(oldInfo, newInfo, Player.DISCONTINUITY_REASON_SEEK)

        assertEquals(1L, observedPos)
        posJob.cancel()
    }
}

