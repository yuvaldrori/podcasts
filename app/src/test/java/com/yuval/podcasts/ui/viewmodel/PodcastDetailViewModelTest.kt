package com.yuval.podcasts.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import com.yuval.podcasts.data.db.entity.Episode
import com.yuval.podcasts.data.repository.PodcastRepository
import com.yuval.podcasts.domain.usecase.EnqueueEpisodeUseCase
import com.yuval.podcasts.ui.navigation.PodcastDetailScreenRoute
import com.yuval.podcasts.utils.MainDispatcherRule
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class PodcastDetailViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun episodes_emitsEpisodesForFeedUrl() = runTest {
        val feedUrl = "http://example.com/feed.xml"
        val route = PodcastDetailScreenRoute(feedUrl = feedUrl)
        val savedStateHandle = SavedStateHandle(mapOf("feedUrl" to feedUrl))

        val repository = mockk<PodcastRepository>(relaxed = true)
        val enqueueEpisodeUseCase = mockk<EnqueueEpisodeUseCase>(relaxed = true)

        val episode1 = Episode("e1", feedUrl, "Title 1", "Desc 1", "http://audio1.mp3", null, null, 0L, 1000L, 0, null, false, 0L, null)
        val episode2 = Episode("e2", feedUrl, "Title 2", "Desc 2", "http://audio2.mp3", null, null, 0L, 1000L, 0, null, false, 0L, null)

        val episodesFlow = MutableStateFlow<List<Episode>>(emptyList())
        every { repository.getEpisodes(feedUrl) } returns episodesFlow

        val viewModel = PodcastDetailViewModel(repository, enqueueEpisodeUseCase, savedStateHandle)

        var emittedEpisodes = listOf<Episode>()
        val job = launch {
            viewModel.episodes.collect { emittedEpisodes = it }
        }
        advanceUntilIdle()

        episodesFlow.value = listOf(episode1, episode2)
        advanceUntilIdle()

        assertEquals(2, emittedEpisodes.size)
        assertEquals("Title 1", emittedEpisodes[0].title)
        assertEquals("Title 2", emittedEpisodes[1].title)

        job.cancel()
    }

    @Test
    fun podcast_emitsPodcastForFeedUrl() = runTest {
        val feedUrl = "http://example.com/feed.xml"
        val savedStateHandle = SavedStateHandle(mapOf("feedUrl" to feedUrl))

        val repository = mockk<PodcastRepository>(relaxed = true)
        val enqueueEpisodeUseCase = mockk<EnqueueEpisodeUseCase>(relaxed = true)

        val podcast = com.yuval.podcasts.data.db.entity.Podcast(
            feedUrl = feedUrl,
            title = "Pivot",
            description = "Pivot Podcast",
            imageUrl = "http://example.com/pivot_cover.png",
            website = "http://example.com"
        )

        val podcastFlow = MutableStateFlow<com.yuval.podcasts.data.db.entity.Podcast?>(null)
        every { repository.getPodcastFlow(feedUrl) } returns podcastFlow

        val viewModel = PodcastDetailViewModel(repository, enqueueEpisodeUseCase, savedStateHandle)

        var emittedPodcast: com.yuval.podcasts.data.db.entity.Podcast? = null
        val job = launch {
            viewModel.podcast.collect { emittedPodcast = it }
        }
        advanceUntilIdle()

        podcastFlow.value = podcast
        advanceUntilIdle()

        assertEquals("Pivot", emittedPodcast?.title)
        assertEquals("http://example.com/pivot_cover.png", emittedPodcast?.imageUrl)

        job.cancel()
    }

    @Test
    fun addToQueue_invokesEnqueueEpisodeUseCase() = runTest {
        val feedUrl = "http://example.com/feed.xml"
        val savedStateHandle = SavedStateHandle(mapOf("feedUrl" to feedUrl))

        val repository = mockk<PodcastRepository>(relaxed = true)
        val enqueueEpisodeUseCase = mockk<EnqueueEpisodeUseCase>(relaxed = true)

        val episode = Episode("e1", feedUrl, "Title 1", "Desc 1", "http://audio1.mp3", null, null, 0L, 1000L, 0, null, false, 0L, null)

        val viewModel = PodcastDetailViewModel(repository, enqueueEpisodeUseCase, savedStateHandle)

        viewModel.addToQueue(episode)
        advanceUntilIdle()

        coVerify(exactly = 1) { enqueueEpisodeUseCase(episode) }
    }
}