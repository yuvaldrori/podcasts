package com.yuval.podcasts.ui.viewmodel

import com.yuval.podcasts.data.db.entity.Episode
import com.yuval.podcasts.data.db.entity.EpisodeWithPodcast
import com.yuval.podcasts.data.db.entity.Podcast
import com.yuval.podcasts.data.repository.PodcastRepository
import com.yuval.podcasts.utils.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class EpisodeDetailViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var repository: PodcastRepository
    private lateinit var enqueueEpisodeUseCase: com.yuval.podcasts.domain.usecase.EnqueueEpisodeUseCase
    private lateinit var viewModel: EpisodeDetailViewModel

    @Before
    fun setup() {
        repository = mockk()
        enqueueEpisodeUseCase = mockk()
        viewModel = EpisodeDetailViewModel(repository, enqueueEpisodeUseCase)
    }

    @Test
    fun loadEpisode_updatesEpisodeState() = runTest {
        val episode = Episode("ep1", "url", "title", "desc", "url", null, 0L, 0L, 0, null, false, 0L)
        val podcast = Podcast("url", "podTitle", "podDesc", "img", "web")
        val episodeWithPodcast = EpisodeWithPodcast(episode, podcast)

        every { repository.getEpisodeWithPodcastFlow("ep1") } returns flowOf(episodeWithPodcast)

        viewModel.loadEpisode("ep1")

        assertEquals(episodeWithPodcast, viewModel.episode.value)
    }

    @Test
    fun addToQueue_callsRepository() = runTest {
        val episode = Episode("ep1", "url", "title", "desc", "url", null, 0L, 0L, 0, null, false, 0L)
        coEvery { enqueueEpisodeUseCase(episode) } returns Unit

        viewModel.addToQueue(episode)

        coVerify { enqueueEpisodeUseCase(episode) }
    }
}
