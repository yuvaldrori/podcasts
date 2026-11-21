package com.yuval.podcasts.ui.screens

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasParent
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.WorkManager
import com.yuval.podcasts.data.db.AppDatabase
import com.yuval.podcasts.data.db.entity.Episode
import com.yuval.podcasts.data.db.entity.Podcast
import com.yuval.podcasts.data.db.entity.QueueState
import com.yuval.podcasts.data.network.PodcastApi
import com.yuval.podcasts.data.network.RssParser
import com.yuval.podcasts.data.repository.DefaultPodcastRepository
import com.yuval.podcasts.data.repository.DownloadProgressTracker
import com.yuval.podcasts.ui.theme.PodcastsTheme
import com.yuval.podcasts.ui.viewmodel.FeedsUiState
import com.yuval.podcasts.ui.viewmodel.QueueUiState
import com.yuval.podcasts.utils.LogManager
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AddToQueueIntegrationTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var database: AppDatabase
    private lateinit var repository: DefaultPodcastRepository

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = DefaultPodcastRepository(
            database = database,
            podcastApi = PodcastApi(OkHttpClient(), Dispatchers.IO),
            rssParser = RssParser(),
            podcastDao = database.podcastDao(),
            episodeDao = database.episodeDao(),
            queueDao = database.queueDao(),
            chapterDao = database.chapterDao(),
            workManager = WorkManager.getInstance(context),
            downloadProgressTracker = DownloadProgressTracker(),
            ioDispatcher = Dispatchers.IO,
            logManager = LogManager(context)
        )
    }

    @After
    fun teardown() {
        database.close()
    }

    private fun episode(id: String, feed: String, title: String, pubDate: Long): Episode = Episode(
        id = id,
        podcastFeedUrl = feed,
        title = title,
        description = "Description for $title",
        audioUrl = "/local/$id.mp3",
        imageUrl = null,
        episodeWebLink = null,
        pubDate = pubDate,
        duration = 600L,
        downloadStatus = 0,
        localFilePath = null,
        isPlayed = false,
        lastPlayedPosition = 0L
    )

    private fun clickAddToQueueFor(episodeTitle: String) {
        composeTestRule.onNode(
            hasContentDescription("Add to Queue") and hasParent(hasText(episodeTitle))
        ).performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodes(hasText(episodeTitle)).fetchSemanticsNodes().isEmpty()
        }
    }

    private fun assertDisplayedTopToBottom(titles: List<String>) {
        val tops = titles.map { title ->
            val node = composeTestRule.onNodeWithText(title)
            node.assertIsDisplayed()
            node.getUnclippedBoundsInRoot().top.value
        }
        for (i in 0 until tops.lastIndex) {
            assertTrue(
                "Expected '${titles[i]}' (top=${tops[i]}) above '${titles[i + 1]}' (top=${tops[i + 1]})",
                tops[i] < tops[i + 1]
            )
        }
    }

    private fun setUpNewAndQueueHost(currentlyPlayingEpisodeId: String? = null) {
        composeTestRule.setContent {
            val unplayed by repository.unplayedEpisodes.collectAsState(initial = emptyList())
            val queue by repository.listeningQueue.collectAsState(initial = emptyList())
            var showQueue by remember { mutableStateOf(false) }
            val scope = rememberCoroutineScope()

            PodcastsTheme {
                Column {
                    Row {
                        Button(onClick = { showQueue = false }) { Text("Show New") }
                        Button(onClick = { showQueue = true }) { Text("Show Queue") }
                    }
                    if (showQueue) {
                        QueueScreen(
                            uiState = QueueUiState.Success(queue.toImmutableList()),
                            queueTimeRemaining = 0L,
                            isPlaying = currentlyPlayingEpisodeId != null,
                            currentMediaId = currentlyPlayingEpisodeId,
                            onEpisodeClick = {},
                            onRemoveFromQueue = {},
                            onMoveItem = { _, _ -> },
                            onCommitReorder = {},
                            onPlayQueue = { _, _, _ -> },
                            onPause = {}
                        )
                    } else {
                        NewEpisodesScreen(
                            uiState = FeedsUiState.Success(unplayedEpisodes = unplayed.toImmutableList()),
                            onEpisodeClick = {},
                            onRefreshAll = {},
                            onDismissAll = {},
                            onDismissEpisode = {},
                            onAddToQueue = { ep ->
                                scope.launch {
                                    repository.requeueEpisode(ep, currentlyPlayingEpisodeId)
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    @Test
    fun addFromNewTab_groupsSameSubscriptionByDate_andDoesNotSplitGroups() = runBlocking {
        database.podcastDao().insertPodcast(Podcast("feedA", "Podcast A", "", "", ""))
        database.podcastDao().insertPodcast(Podcast("feedB", "Podcast B", "", "", ""))
        database.podcastDao().insertPodcast(Podcast("feedC", "Podcast C", "", "", ""))
        database.episodeDao().insertEpisodes(
            listOf(
                episode("epA1", "feedA", "A Jan", 1_000L),
                episode("epC1", "feedC", "C Feb", 2_000L),
                episode("epA2", "feedA", "A Mar", 3_000L),
                episode("epB1", "feedB", "B Apr", 4_000L),
                episode("epA3", "feedA", "A May", 5_000L)
            )
        )

        setUpNewAndQueueHost(currentlyPlayingEpisodeId = null)

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodes(hasText("B Apr")).fetchSemanticsNodes().isNotEmpty()
        }

        // Tap '+' out of chronological order, including C Feb whose date sits between A Jan and A Mar.
        clickAddToQueueFor("B Apr")
        clickAddToQueueFor("A Mar")
        clickAddToQueueFor("A Jan")
        clickAddToQueueFor("A May")
        clickAddToQueueFor("C Feb")

        // Verify persisted Room queue order
        val persistedIds = repository.listeningQueue.first().map { it.episode.id }
        assertEquals(listOf("epA1", "epA2", "epA3", "epC1", "epB1"), persistedIds)

        // Switch to Queue tab and verify visual top-to-bottom order on screen
        composeTestRule.onNodeWithText("Show Queue").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodes(hasText("A Jan")).fetchSemanticsNodes().isNotEmpty()
        }
        assertDisplayedTopToBottom(listOf("A Jan", "A Mar", "A May", "C Feb", "B Apr"))
    }

    @Test
    fun addFromNewTab_whenPlaying_keepsNewEpisodesBelowPlayingAndPreservesExistingOrder() = runBlocking {
        database.podcastDao().insertPodcast(Podcast("feedA", "Podcast A", "", "", ""))
        database.podcastDao().insertPodcast(Podcast("feedB", "Podcast B", "", "", ""))
        database.podcastDao().insertPodcast(Podcast("feedC", "Podcast C", "", "", ""))
        database.podcastDao().insertPodcast(Podcast("feedD", "Podcast D", "", "", ""))
        database.episodeDao().insertEpisodes(
            listOf(
                episode("epD0", "feedD", "D Oldest", 500L),
                episode("epA1", "feedA", "A Jan", 1_000L),
                episode("epA2", "feedA", "A Feb", 2_000L),
                episode("epA3", "feedA", "A Mar Playing", 3_000L),
                episode("epB1", "feedB", "B Old Manual", 1_500L),
                episode("epC1", "feedC", "C May", 5_000L)
            )
        )
        // Deliberately out-of-date-order existing queue with epA3 playing at top.
        database.queueDao().updateQueue(
            listOf(
                QueueState("epA3", 0),
                QueueState("epB1", 1),
                QueueState("epC1", 2)
            )
        )

        setUpNewAndQueueHost(currentlyPlayingEpisodeId = "epA3")

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodes(hasText("A Feb")).fetchSemanticsNodes().isNotEmpty()
        }

        clickAddToQueueFor("A Feb")
        clickAddToQueueFor("A Jan")
        clickAddToQueueFor("D Oldest")

        val persistedIds = repository.listeningQueue.first().map { it.episode.id }
        assertEquals(listOf("epA3", "epA1", "epA2", "epD0", "epB1", "epC1"), persistedIds)

        composeTestRule.onNodeWithText("Show Queue").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodes(hasText("A Mar Playing")).fetchSemanticsNodes().isNotEmpty()
        }
        assertDisplayedTopToBottom(
            listOf("A Mar Playing", "A Jan", "A Feb", "D Oldest", "B Old Manual", "C May")
        )
    }
}
