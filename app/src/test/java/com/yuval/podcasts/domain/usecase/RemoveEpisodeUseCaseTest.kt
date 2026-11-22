package com.yuval.podcasts.domain.usecase

import com.yuval.podcasts.data.repository.PodcastRepository
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RemoveEpisodeUseCaseTest {

    private val repository = mockk<PodcastRepository>(relaxed = true)
    private val useCase = RemoveEpisodeUseCase(repository, UnconfinedTestDispatcher())

    @Test
    fun invoke_withMarkAsPlayedTrue_marksPlayedAndRemovesFromQueue() = runTest {
        useCase("ep1", markAsPlayed = true)

        coVerify(exactly = 1) { repository.markAsPlayed("ep1") }
        coVerify(exactly = 1) { repository.removeFromQueue("ep1") }
    }

    @Test
    fun invoke_withMarkAsPlayedFalse_doesNotMarkPlayed() = runTest {
        useCase("ep1", markAsPlayed = false)

        coVerify(exactly = 0) { repository.markAsPlayed(any()) }
        coVerify(exactly = 1) { repository.removeFromQueue("ep1") }
    }

    @Test
    fun invoke_defaultArguments_marksAsPlayed() = runTest {
        useCase("ep1")

        coVerify(exactly = 1) { repository.markAsPlayed("ep1") }
        coVerify(exactly = 1) { repository.removeFromQueue("ep1") }
    }
}
