package com.yuval.podcasts.domain.usecase

import androidx.work.WorkManager
import com.yuval.podcasts.data.repository.PodcastRepository
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test
import io.mockk.coEvery

import kotlinx.coroutines.test.UnconfinedTestDispatcher

class RemoveEpisodeUseCaseTest {

    private val repository = mockk<PodcastRepository>(relaxed = true)
    private val workManager = mockk<WorkManager>(relaxed = true)
    
    private val useCase = RemoveEpisodeUseCase(repository, UnconfinedTestDispatcher())

    @Test
    fun invoke_withMarkAsPlayedTrue_updatesPlaybackStatusWithTimestamp() = runTest {
        coEvery { repository.getEpisodeByIdFlow("ep1") } returns flowOf(null)
        
        useCase("ep1", markAsPlayed = true)
        
        coVerify(exactly = 1) { repository.markAsPlayed("ep1") }
        coVerify { repository.removeFromQueue("ep1") }
    }

    @Test
    fun invoke_withMarkAsPlayedFalse_doesNotUpdatePlaybackStatus() = runTest {
        coEvery { repository.getEpisodeByIdFlow("ep1") } returns flowOf(null)

        useCase("ep1", markAsPlayed = false)
        
        coVerify(exactly = 0) { repository.markAsPlayed(any()) }
        coVerify { repository.removeFromQueue("ep1") }
    }
}
