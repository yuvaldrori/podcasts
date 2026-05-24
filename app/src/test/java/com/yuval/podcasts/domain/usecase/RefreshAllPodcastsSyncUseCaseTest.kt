package com.yuval.podcasts.domain.usecase

import com.yuval.podcasts.data.db.entity.Podcast
import com.yuval.podcasts.data.repository.PodcastRepository
import com.yuval.podcasts.utils.LogManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class RefreshAllPodcastsSyncUseCaseTest {

    private val repository = mockk<PodcastRepository>(relaxed = true)
    private val logManager = mockk<LogManager>(relaxed = true)
    private val useCase = RefreshAllPodcastsSyncUseCase(repository, logManager)

    @Test
    fun `invoke syncs all podcasts and returns total new episodes`() = runTest {
        // Arrange
        val podcasts = listOf(
            createPodcast("https://feed1.com"),
            createPodcast("https://feed2.com")
        )
        every { repository.allPodcasts } returns flowOf(podcasts)
        coEvery { repository.fetchAndStorePodcast("https://feed1.com") } returns 3
        coEvery { repository.fetchAndStorePodcast("https://feed2.com") } returns 2

        // Act
        val result = useCase()

        // Assert
        assertEquals(5, result)
        coVerify(exactly = 1) { repository.fetchAndStorePodcast("https://feed1.com") }
        coVerify(exactly = 1) { repository.fetchAndStorePodcast("https://feed2.com") }
    }

    @Test
    fun `invoke logs error and continues when a podcast sync fails`() = runTest {
        // Arrange
        val podcasts = listOf(
            createPodcast("https://feed1.com"),
            createPodcast("https://feed2.com")
        )
        every { repository.allPodcasts } returns flowOf(podcasts)
        coEvery { repository.fetchAndStorePodcast("https://feed1.com") } throws RuntimeException("Network Error")
        coEvery { repository.fetchAndStorePodcast("https://feed2.com") } returns 4

        // Act
        val result = useCase()

        // Assert
        assertEquals(4, result)
        verify {
            logManager.e(
                "RefreshAllSync",
                "Failed to refresh https://feed1.com",
                match { it["error"] == "RuntimeException: Network Error" }
            )
        }
        coVerify(exactly = 1) { repository.fetchAndStorePodcast("https://feed2.com") }
    }

    private fun createPodcast(feedUrl: String) = Podcast(
        feedUrl = feedUrl,
        title = "Title",
        description = "Desc",
        imageUrl = "url",
        website = "website"
    )
}
