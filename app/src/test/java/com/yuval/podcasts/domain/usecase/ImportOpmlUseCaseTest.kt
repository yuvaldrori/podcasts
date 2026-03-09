package com.yuval.podcasts.domain.usecase

import com.yuval.podcasts.data.opml.OpmlManager
import com.yuval.podcasts.data.repository.PodcastRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.io.InputStream

class ImportOpmlUseCaseTest {

    @Test
    fun `invoke parses stream and fetches podcasts`() = runTest {
        val opmlManager = mockk<OpmlManager>()
        val repository = mockk<PodcastRepository>(relaxed = true)
        val inputStream = mockk<InputStream>()
        val urls = listOf("url1", "url2")
        
        every { opmlManager.parse(inputStream) } returns urls
        coEvery { repository.fetchAndStorePodcast(any()) } returns Unit
        
        val useCase = ImportOpmlUseCase(opmlManager, repository, UnconfinedTestDispatcher(testScheduler))

        useCase(inputStream)

        coVerify(exactly = 1) { repository.fetchAndStorePodcast("url1") }
        coVerify(exactly = 1) { repository.fetchAndStorePodcast("url2") }
    }
}