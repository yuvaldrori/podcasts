package com.yuval.podcasts.domain.usecase

import com.yuval.podcasts.data.db.entity.Podcast
import com.yuval.podcasts.data.opml.OpmlManager
import com.yuval.podcasts.data.repository.PodcastRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.io.OutputStream

class ExportOpmlUseCaseTest {

    @Test
    fun `invoke exports all podcasts to output stream`() = runTest {
        val opmlManager = mockk<OpmlManager>(relaxed = true)
        val repository = mockk<PodcastRepository>()
        val outputStream = mockk<OutputStream>()
        val podcasts = listOf(Podcast("url", "title", "desc", "img", "web"))
        
        every { repository.allPodcasts } returns flowOf(podcasts)
        
        val useCase = ExportOpmlUseCase(opmlManager, repository, UnconfinedTestDispatcher(testScheduler))

        useCase(outputStream)

        verify { opmlManager.export(podcasts, outputStream) }
    }
}