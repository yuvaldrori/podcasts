package com.yuval.podcasts.appfunctions

import androidx.appfunctions.AppFunctionContext
import com.yuval.podcasts.data.db.entity.Podcast
import com.yuval.podcasts.data.repository.PodcastRepository
import com.yuval.podcasts.domain.usecase.RefreshAllPodcastsSyncUseCase
import com.yuval.podcasts.domain.usecase.ReorderSubscriptionInQueueUseCase
import com.yuval.podcasts.media.PlayerManager
import com.yuval.podcasts.utils.LogManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class PodcastAppFunctionsTest {

    private val playerManager = mockk<PlayerManager>(relaxed = true)
    private val refreshUseCase = mockk<RefreshAllPodcastsSyncUseCase>(relaxed = true)
    private val reorderUseCase = mockk<ReorderSubscriptionInQueueUseCase>(relaxed = true)
    private val repository = mockk<PodcastRepository>(relaxed = true)
    private val logManager = mockk<LogManager>(relaxed = true)
    private val context = mockk<AppFunctionContext>()

    private val appFunctions = PodcastAppFunctions(
        playerManager,
        refreshUseCase,
        reorderUseCase,
        repository,
        logManager
    )

    private val mockPodcasts = listOf(
        Podcast(
            feedUrl = "https://example.com/rss",
            title = "The Example Podcast",
            description = "Description",
            imageUrl = "http://example.com/img",
            website = "http://example.com"
        ),
        Podcast(
            feedUrl = "https://hebrew.com/rss",
            title = "פודקאסט בעברית",
            description = "Description",
            imageUrl = "http://hebrew.com/img",
            website = "http://hebrew.com"
        )
    )

    @Before
    fun setup() {
        every { repository.allPodcasts } returns flowOf(mockPodcasts)
    }

    @Test
    fun `resumeQueue calls playerManager play`() = runTest {
        val result = appFunctions.resumeQueue(context)
        verify { playerManager.play() }
        assertEquals("Resuming playback", result)
    }

    @Test
    fun `stopPlayback calls playerManager pause`() = runTest {
        val result = appFunctions.stopPlayback(context)
        verify { playerManager.pause() }
        assertEquals("Playback stopped", result)
    }

    @Test
    fun `refreshNewEpisodes returns correct count message`() = runTest {
        coEvery { refreshUseCase() } returns 5
        val result = appFunctions.refreshNewEpisodes(context)
        assertEquals("Found 5 new episodes", result)
    }

    @Test
    fun `moveSubscriptionToBottom resolves exact feed URL`() = runTest {
        val feedUrl = "https://example.com/rss"
        val result = appFunctions.moveSubscriptionToBottom(context, feedUrl)
        coVerify { reorderUseCase(feedUrl) }
        assertEquals("Moved episodes to the bottom of the queue", result)
    }

    @Test
    fun `moveSubscriptionToBottom resolves exact title`() = runTest {
        val title = "The Example Podcast"
        val result = appFunctions.moveSubscriptionToBottom(context, title)
        coVerify { reorderUseCase("https://example.com/rss") }
        assertEquals("Moved episodes to the bottom of the queue", result)
    }

    @Test
    fun `moveSubscriptionToBottom resolves partial title in Hebrew`() = runTest {
        val query = "בעברית"
        val result = appFunctions.moveSubscriptionToBottom(context, query)
        coVerify { reorderUseCase("https://hebrew.com/rss") }
        assertEquals("Moved episodes to the bottom of the queue", result)
    }

    @Test
    fun `moveSubscriptionToBottom returns error message when subscription not found`() = runTest {
        val query = "Nonexistent"
        val result = appFunctions.moveSubscriptionToBottom(context, query)
        coVerify(exactly = 0) { reorderUseCase(any()) }
        assertEquals("Could not find a podcast subscription matching 'Nonexistent'", result)
    }

    @Test
    fun `addDebugLog calls logManager`() = runTest {
        val msg = "Test log"
        val result = appFunctions.addDebugLog(context, msg)
        verify { logManager.i("AppFunction", "User note: $msg") }
        assertEquals("Log added", result)
    }
}
