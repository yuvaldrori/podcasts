package com.yuval.podcasts.ui.screens

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yuval.podcasts.R
import com.yuval.podcasts.data.db.entity.Episode
import com.yuval.podcasts.data.db.entity.EpisodeWithPodcast
import com.yuval.podcasts.data.db.entity.Podcast
import com.yuval.podcasts.ui.theme.PodcastsTheme
import com.yuval.podcasts.ui.viewmodel.QueueUiState
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class QueueScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private val dummyPodcast = Podcast("feed", "Podcast Title", "Desc", "url", "web")
    private val ep1 = Episode("ep1", "feed", "Ep 1", "Desc", "url", null, null, 0L, 1000, 0, null, false, 0, null)
    private val ep2 = Episode("ep2", "feed", "Ep 2", "Desc", "url", null, null, 0L, 1000, 0, null, false, 0, null)
    private val ep3 = Episode("ep3", "feed", "Ep 3", "Desc", "url", null, null, 0L, 1000, 0, null, false, 0, null)

    @Test
    fun playButtonShowsWhenNotPlaying() {
        val state = QueueUiState.Success(persistentListOf(EpisodeWithPodcast(ep1, dummyPodcast)))

        composeTestRule.setContent {
            PodcastsTheme {
                QueueScreen(
                    uiState = state,
                    queueTimeRemaining = 1000,
                    isPlaying = false,
                    currentMediaId = null,
                    onEpisodeClick = {},
                    onRemoveFromQueue = {},
                    onMoveItem = { _, _ -> },
                    onCommitReorder = {},
                    onPlayQueue = { _, _, _ -> },
                    onPause = {}
                )
            }
        }

        composeTestRule.onNodeWithContentDescription(context.getString(R.string.play)).assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription(context.getString(R.string.pause)).assertDoesNotExist()
    }

    @Test
    fun pauseButtonShowsWhenPlaying() {
        val state = QueueUiState.Success(persistentListOf(EpisodeWithPodcast(ep1, dummyPodcast)))

        composeTestRule.setContent {
            PodcastsTheme {
                QueueScreen(
                    uiState = state,
                    queueTimeRemaining = 1000,
                    isPlaying = true,
                    currentMediaId = "ep1",
                    onEpisodeClick = {},
                    onRemoveFromQueue = {},
                    onMoveItem = { _, _ -> },
                    onCommitReorder = {},
                    onPlayQueue = { _, _, _ -> },
                    onPause = {}
                )
            }
        }

        composeTestRule.onNodeWithContentDescription(context.getString(R.string.pause)).assertIsDisplayed()
    }

    @Test
    fun dragAndDropReordersItemsAndCommits() {
        var commitCount = 0
        var moveCount = 0
        var queueItems by mutableStateOf(
            listOf(
                EpisodeWithPodcast(ep1, dummyPodcast),
                EpisodeWithPodcast(ep2, dummyPodcast)
            )
        )

        composeTestRule.setContent {
            PodcastsTheme {
                QueueScreen(
                    uiState = QueueUiState.Success(queueItems.toImmutableList()),
                    queueTimeRemaining = 2000,
                    isPlaying = false,
                    currentMediaId = null,
                    onEpisodeClick = {},
                    onRemoveFromQueue = {},
                    onMoveItem = { from, to ->
                        moveCount++
                        queueItems = queueItems.toMutableList().apply {
                            add(to, removeAt(from))
                        }
                    },
                    onCommitReorder = { commitCount++ },
                    onPlayQueue = { _, _, _ -> },
                    onPause = {}
                )
            }
        }

        val handle1 = composeTestRule.onNodeWithTag("reorder_handle_ep1", useUnmergedTree = true)
        handle1.assertExists()

        // Drag ep1 down past ep2
        handle1.performTouchInput {
            down(center)
            moveBy(Offset(0f, 300f))
            up()
        }
        composeTestRule.waitForIdle()

        assertTrue("onMoveItem should have been invoked during drag", moveCount > 0)
        assertEquals("onCommitReorder should be invoked once when drag ends", 1, commitCount)
        assertEquals(listOf("ep2", "ep1"), queueItems.map { it.episode.id })
    }

    @Test
    fun stateRestorationTest_preservesReorderedQueueAcrossRestoration() {
        val restorationTester = StateRestorationTester(composeTestRule)
        val episodesById = mapOf(
            "ep1" to EpisodeWithPodcast(ep1, dummyPodcast),
            "ep2" to EpisodeWithPodcast(ep2, dummyPodcast)
        )

        restorationTester.setContent {
            var orderIds by rememberSaveable { mutableStateOf(listOf("ep1", "ep2")) }
            val items = orderIds.mapNotNull { episodesById[it] }.toImmutableList()

            PodcastsTheme {
                QueueScreen(
                    uiState = QueueUiState.Success(items),
                    queueTimeRemaining = 2000,
                    isPlaying = false,
                    currentMediaId = null,
                    onEpisodeClick = {},
                    onRemoveFromQueue = {},
                    onMoveItem = { from, to ->
                        orderIds = orderIds.toMutableList().apply {
                            add(to, removeAt(from))
                        }
                    },
                    onCommitReorder = {},
                    onPlayQueue = { _, _, _ -> },
                    onPause = {}
                )
            }
        }

        // Drag ep1 down below ep2
        composeTestRule.onNodeWithTag("reorder_handle_ep1", useUnmergedTree = true).performTouchInput {
            down(center)
            moveBy(Offset(0f, 300f))
            up()
        }
        composeTestRule.waitForIdle()

        // Simulate process death / configuration change and verify ep2 stays above ep1
        restorationTester.emulateSavedInstanceStateRestore()
        composeTestRule.waitForIdle()

        val ep1Top = composeTestRule.onNodeWithText("Ep 1").fetchSemanticsNode().boundsInRoot.top
        val ep2Top = composeTestRule.onNodeWithText("Ep 2").fetchSemanticsNode().boundsInRoot.top
        assertTrue("Reordered order (Ep 2 above Ep 1) must survive state restoration", ep2Top < ep1Top)
    }

    @Test
    fun testPullToRefreshTriggers_whenQueueIsNotEmpty() {
        val state = QueueUiState.Success(persistentListOf(EpisodeWithPodcast(ep1, dummyPodcast)))
        var refreshCalled = false

        composeTestRule.setContent {
            PodcastsTheme {
                QueueScreen(
                    uiState = state,
                    queueTimeRemaining = 1000,
                    isPlaying = false,
                    currentMediaId = null,
                    isRefreshing = false,
                    onRefreshAll = { refreshCalled = true },
                    onEpisodeClick = {},
                    onRemoveFromQueue = {},
                    onMoveItem = { _, _ -> },
                    onCommitReorder = {},
                    onPlayQueue = { _, _, _ -> },
                    onPause = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("queue_list").performTouchInput { swipeDown() }
        composeTestRule.waitForIdle()

        assertTrue("onRefreshAll should be called when non-empty queue is pulled down", refreshCalled)
    }

    @Test
    fun testPullToRefreshTriggers_whenQueueIsEmpty() {
        val state = QueueUiState.Success(persistentListOf())
        var refreshCalled = false

        composeTestRule.setContent {
            PodcastsTheme {
                QueueScreen(
                    uiState = state,
                    queueTimeRemaining = 0,
                    isPlaying = false,
                    currentMediaId = null,
                    isRefreshing = false,
                    onRefreshAll = { refreshCalled = true },
                    onEpisodeClick = {},
                    onRemoveFromQueue = {},
                    onMoveItem = { _, _ -> },
                    onCommitReorder = {},
                    onPlayQueue = { _, _, _ -> },
                    onPause = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("queue_list").performTouchInput { swipeDown() }
        composeTestRule.waitForIdle()

        assertTrue("onRefreshAll should be called when empty queue is pulled down", refreshCalled)
    }

    @Test
    fun listeningTimeHidesWhenQueueIsEmpty() {
        val state = QueueUiState.Success(persistentListOf())

        composeTestRule.setContent {
            PodcastsTheme {
                QueueScreen(
                    uiState = state,
                    queueTimeRemaining = 120_000L,
                    isPlaying = false,
                    currentMediaId = null,
                    onEpisodeClick = {},
                    onRemoveFromQueue = {},
                    onMoveItem = { _, _ -> },
                    onCommitReorder = {},
                    onPlayQueue = { _, _, _ -> },
                    onPause = {}
                )
            }
        }

        val expectedLabel = context.getString(R.string.queue_listening_time, "2m")
        composeTestRule.onNodeWithText(expectedLabel).assertDoesNotExist()
    }

    @Test
    fun listeningTimeShowsWhenQueueIsNotEmpty() {
        val state = QueueUiState.Success(persistentListOf(EpisodeWithPodcast(ep1, dummyPodcast)))

        composeTestRule.setContent {
            PodcastsTheme {
                QueueScreen(
                    uiState = state,
                    queueTimeRemaining = 120_000L,
                    isPlaying = false,
                    currentMediaId = null,
                    onEpisodeClick = {},
                    onRemoveFromQueue = {},
                    onMoveItem = { _, _ -> },
                    onCommitReorder = {},
                    onPlayQueue = { _, _, _ -> },
                    onPause = {}
                )
            }
        }

        val expectedLabel = context.getString(R.string.queue_listening_time, "2m")
        composeTestRule.onNodeWithText(expectedLabel).assertIsDisplayed()
    }

    @Test
    fun moveToTopButton_disabledOnlyForCurrentEpisode_andInvokesCallbackForOthers() {
        val movedIds = mutableListOf<String>()
        val state = QueueUiState.Success(
            persistentListOf(
                EpisodeWithPodcast(ep1, dummyPodcast),
                EpisodeWithPodcast(ep2, dummyPodcast),
                EpisodeWithPodcast(ep3, dummyPodcast)
            )
        )

        composeTestRule.setContent {
            PodcastsTheme {
                QueueScreen(
                    uiState = state,
                    queueTimeRemaining = 3000,
                    isPlaying = false,
                    currentMediaId = "ep2",
                    onEpisodeClick = {},
                    onRemoveFromQueue = {},
                    onMoveToTop = { movedIds.add(it) },
                    onMoveItem = { _, _ -> },
                    onCommitReorder = {},
                    onPlayQueue = { _, _, _ -> },
                    onPause = {}
                )
            }
        }

        val ep1Btn = composeTestRule.onNodeWithTag("move_to_top_ep1")
        val ep2Btn = composeTestRule.onNodeWithTag("move_to_top_ep2")
        val ep3Btn = composeTestRule.onNodeWithTag("move_to_top_ep3")

        ep1Btn.assertIsDisplayed().assertIsEnabled()
        ep2Btn.assertIsDisplayed().assertIsNotEnabled()
        ep3Btn.assertIsDisplayed().assertIsEnabled()

        ep1Btn.performClick()
        ep3Btn.performClick()

        assertEquals(listOf("ep1", "ep3"), movedIds)
    }

    @Test
    fun moveToTopButton_allEnabledWhenNoCurrentEpisode() {
        val movedIds = mutableListOf<String>()
        val state = QueueUiState.Success(
            persistentListOf(
                EpisodeWithPodcast(ep1, dummyPodcast),
                EpisodeWithPodcast(ep2, dummyPodcast)
            )
        )

        composeTestRule.setContent {
            PodcastsTheme {
                QueueScreen(
                    uiState = state,
                    queueTimeRemaining = 2000,
                    isPlaying = false,
                    currentMediaId = null,
                    onEpisodeClick = {},
                    onRemoveFromQueue = {},
                    onMoveToTop = { movedIds.add(it) },
                    onMoveItem = { _, _ -> },
                    onCommitReorder = {},
                    onPlayQueue = { _, _, _ -> },
                    onPause = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("move_to_top_ep1").assertIsDisplayed().assertIsEnabled()
        composeTestRule.onNodeWithTag("move_to_top_ep2").assertIsDisplayed().assertIsEnabled().performClick()

        assertEquals(listOf("ep2"), movedIds)
    }
}
