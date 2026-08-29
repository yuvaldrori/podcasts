package com.yuval.podcasts.ui.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.yuval.podcasts.data.db.entity.Episode
import com.yuval.podcasts.data.db.entity.EpisodeWithPodcast
import com.yuval.podcasts.data.db.entity.Podcast
import com.yuval.podcasts.data.repository.PodcastRepository
import com.yuval.podcasts.media.PlayerManager
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class ThemeViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var context: Context
    private lateinit var playerManager: PlayerManager
    private lateinit var repository: PodcastRepository
    private lateinit var imageLoader: ImageLoader
    private val currentMediaIdFlow = MutableStateFlow<String?>(null)

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        context = ApplicationProvider.getApplicationContext()
        playerManager = mockk(relaxed = true)
        repository = mockk(relaxed = true)
        imageLoader = mockk(relaxed = true)

        every { playerManager.currentMediaId } returns currentMediaIdFlow
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun dynamicTheme_isSeededFromPodcastImageUrlNotEpisodeImageUrl() = runTest(testDispatcher) {
        val episode = Episode(
            id = "ep1",
            podcastFeedUrl = "https://feed.url",
            title = "Episode 1",
            description = "Desc",
            audioUrl = "https://audio.url",
            imageUrl = "https://episode-art.url/img.png",
            pubDate = 1000L,
            duration = 1800L,
            downloadStatus = 0,
            localFilePath = null
        )
        val podcast = Podcast(
            feedUrl = "https://feed.url",
            title = "Podcast Title",
            description = "Desc",
            imageUrl = "https://podcast-sub-art.url/sub.png",
            website = "https://podcast.url"
        )
        val episodeWithPodcast = EpisodeWithPodcast(episode, podcast)

        every { repository.getEpisodeWithPodcastFlow("ep1") } returns flowOf(episodeWithPodcast)

        val requestSlot = slot<ImageRequest>()
        val bitmap = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888).apply {
            eraseColor(android.graphics.Color.BLUE)
        }
        val resultDrawable = BitmapDrawable(context.resources, bitmap)
        val fakeSuccessResult = mockk<SuccessResult> {
            every { drawable } returns resultDrawable
        }

        coEvery { imageLoader.execute(capture(requestSlot)) } returns fakeSuccessResult

        val viewModel = ThemeViewModel(context, playerManager, repository, imageLoader, testDispatcher)

        currentMediaIdFlow.value = "ep1"
        advanceTimeBy(350L) // debounce
        advanceUntilIdle()

        // Verify that the image requested for the theme palette is the PODCAST's subscription art,
        // NOT the episode's artwork.
        assertEquals("https://podcast-sub-art.url/sub.png", requestSlot.captured.data)
        assertNotNull(viewModel.dynamicColorScheme.value)
    }
}
