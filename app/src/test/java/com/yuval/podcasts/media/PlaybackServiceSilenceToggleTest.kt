package com.yuval.podcasts.media

import androidx.media3.exoplayer.ExoPlayer
import com.yuval.podcasts.data.repository.SettingsRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class PlaybackServiceSilenceToggleTest {

    @Test
    fun collector_updatesExoPlayer_whenFlowEmits() = runTest {
        val exoPlayer = mockk<ExoPlayer>(relaxed = true)
        val settingsRepository = mockk<SettingsRepository>(relaxed = true)
        val skipSilenceFlow = MutableStateFlow(true)

        every { settingsRepository.skipSilenceFlow } returns skipSilenceFlow

        val service = PlaybackService()
        val job = service.observeSkipSilence(
            this,
            UnconfinedTestDispatcher(testScheduler),
            settingsRepository,
            exoPlayer
        )

        verify { exoPlayer.skipSilenceEnabled = true }

        skipSilenceFlow.value = false
        verify { exoPlayer.skipSilenceEnabled = false }

        job.cancel()
    }
}
