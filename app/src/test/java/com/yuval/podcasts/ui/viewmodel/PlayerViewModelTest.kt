package com.yuval.podcasts.ui.viewmodel

import com.yuval.podcasts.data.repository.PodcastRepository
import com.yuval.podcasts.media.PlayerManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import com.yuval.podcasts.utils.MainDispatcherRule
import org.junit.Rule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.setMain

class PlayerViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(testDispatcher)

    private lateinit var repository: PodcastRepository
    private lateinit var playerManager: PlayerManager
    private lateinit var viewModel: PlayerViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        
        repository = mockk(relaxed = true)
        playerManager = mockk(relaxed = true)
        
        every { playerManager.isPlaying } returns MutableStateFlow(false)
        every { playerManager.isConnected } returns MutableStateFlow(false)
        every { playerManager.currentPosition } returns MutableStateFlow(0L)
        every { playerManager.duration } returns MutableStateFlow(0L)
        every { playerManager.playbackSpeed } returns MutableStateFlow(1f)
        every { playerManager.currentMediaId } returns MutableStateFlow(null)
        every { playerManager.isInitialized } returns MutableStateFlow(true)
        
        every { repository.listeningQueue } returns flowOf(emptyList())

        viewModel = PlayerViewModel(repository, playerManager)
    }

    @Test
    fun `playPause delegates to playerManager`() = runTest {
        viewModel.playPause()
        verify { playerManager.togglePlayPause() }
    }

    @Test
    fun `seekForward delegates to playerManager`() = runTest {
        viewModel.seekForward()
        verify { playerManager.seekForward() }
    }

    @Test
    fun `seekBackward delegates to playerManager`() = runTest {
        viewModel.seekBackward()
        verify { playerManager.seekBackward() }
    }
}
