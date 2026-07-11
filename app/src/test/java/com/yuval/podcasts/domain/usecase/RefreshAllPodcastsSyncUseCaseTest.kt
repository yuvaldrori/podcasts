package com.yuval.podcasts.domain.usecase

import com.yuval.podcasts.data.db.entity.Podcast
import com.yuval.podcasts.data.repository.PodcastRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class RefreshAllPodcastsSyncUseCaseTest {

    private val repository = mockk<PodcastRepository>(relaxed = true)
    private val useCase = RefreshAllPodcastsSyncUseCase(repository)

    @Test
    fun `invoke syncs all podcasts and returns total new episodes`() = runTest {
        // Arrange
        val podcasts = listOf(
            createPodcast("https://feed1.com"),
            createPodcast("https://feed2.com")
        )
        every { repository.allPodcasts } returns flowOf(podcasts)
        coEvery { repository.refreshPodcasts(listOf("https://feed1.com", "https://feed2.com"), any()) } returns 5

        // Act
        val result = useCase()

        // Assert
        assertEquals(5, result)
        coVerify(exactly = 1) { repository.refreshPodcasts(listOf("https://feed1.com", "https://feed2.com"), any()) }
    }

    private fun createPodcast(feedUrl: String) = Podcast(
        feedUrl = feedUrl,
        title = "Title",
        description = "Desc",
        imageUrl = "url",
        website = "website"
    )
}
