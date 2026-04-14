package com.yuval.podcasts.ui.viewmodel

import com.yuval.podcasts.data.repository.PodcastRepository
import com.yuval.podcasts.media.PlayerManager
import com.yuval.podcasts.utils.NetworkMonitor
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.setMain

class PlayerViewModelTest {

    private lateinit var repository: PodcastRepository
    private lateinit var playerManager: PlayerManager
    private lateinit var networkMonitor: NetworkMonitor
    private lateinit var viewModel: PlayerViewModel
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        
        repository = mockk(relaxed = true)
        playerManager = mockk(relaxed = true)
        networkMonitor = mockk(relaxed = true)
        
        every { playerManager.isPlaying } returns MutableStateFlow(false)
        every { playerManager.isConnected } returns MutableStateFlow(false)
        every { playerManager.currentPosition } returns MutableStateFlow(0L)
        every { playerManager.duration } returns MutableStateFlow(0L)
        every { playerManager.playbackSpeed } returns MutableStateFlow(1f)
        every { playerManager.currentMediaId } returns MutableStateFlow(null)
        every { playerManager.isInitialized } returns MutableStateFlow(true)
        every { networkMonitor.isOnline } returns MutableStateFlow(true)
        
        every { repository.listeningQueue } returns flowOf(emptyList())

        viewModel = PlayerViewModel(repository, playerManager, networkMonitor)
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
