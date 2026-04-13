package com.yuval.podcasts.media

import androidx.media3.exoplayer.ExoPlayer
import com.yuval.podcasts.data.repository.SettingsRepository
import io.mockk.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PlaybackServiceSilenceToggleTest {

    @Test
    fun collector_updatesExoPlayer_whenFlowEmits() = runTest {
        val exoPlayer = mockk<ExoPlayer>(relaxed = true)
        val settingsRepository = mockk<SettingsRepository>(relaxed = true)
        val skipSilenceFlow = MutableStateFlow(false)
        
        every { settingsRepository.skipSilenceFlow() } returns skipSilenceFlow

        val job = launch {
            settingsRepository.skipSilenceFlow().collect { enabled ->
                exoPlayer.skipSilenceEnabled = enabled
            }
        }
        advanceUntilIdle()

        verify { exoPlayer.skipSilenceEnabled = false }

        skipSilenceFlow.value = true
        advanceUntilIdle()
        verify { exoPlayer.skipSilenceEnabled = true }

        job.cancel()
    }
}
