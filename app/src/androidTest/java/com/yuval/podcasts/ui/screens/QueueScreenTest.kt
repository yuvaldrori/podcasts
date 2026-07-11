package com.yuval.podcasts.ui.screens

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yuval.podcasts.data.db.entity.Episode
import com.yuval.podcasts.data.db.entity.Podcast
import com.yuval.podcasts.data.db.entity.EpisodeWithPodcast
import com.yuval.podcasts.ui.viewmodel.QueueUiState
import com.yuval.podcasts.ui.theme.PodcastsTheme
import kotlinx.collections.immutable.persistentListOf
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class QueueScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val dummyPodcast = Podcast("feed", "Podcast Title", "Desc", "url", "web")
    private val ep1 = Episode("ep1", "feed", "Ep 1", "Desc", "url", null, null, 0L, 1000, 0, null, false, 0, null)
    private val ep2 = Episode("ep2", "feed", "Ep 2", "Desc", "url", null, null, 0L, 1000, 0, null, false, 0, null)

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

        composeTestRule.onNodeWithContentDescription("Play").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Pause").assertDoesNotExist()
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

        composeTestRule.onNodeWithContentDescription("Pause").assertIsDisplayed()
    }

    @Test
    fun dragAndDropStressTest() {
        var callCount = 0
        val state = QueueUiState.Success(persistentListOf(
            EpisodeWithPodcast(ep1, dummyPodcast),
            EpisodeWithPodcast(ep2, dummyPodcast)
        ))

        composeTestRule.setContent {
            PodcastsTheme {
                QueueScreen(
                    uiState = state,
                    queueTimeRemaining = 2000,
                    isPlaying = false,
                    currentMediaId = null,
                    onEpisodeClick = {},
                    onRemoveFromQueue = {},
                    onMoveItem = { _, _ -> },
                    onCommitReorder = { callCount++ },
                    onPlayQueue = { _, _, _ -> },
                    onPause = {}
                )
            }
        }

        // Assert handles are displayed
        composeTestRule.onNodeWithTag("reorder_handle_ep1", useUnmergedTree = true).assertExists()

        // Rapidly perform multiple reorder actions
        val handle1 = composeTestRule.onNodeWithTag("reorder_handle_ep1", useUnmergedTree = true)
        
        repeat(3) {
            handle1.performTouchInput {
                down(center)
                moveBy(androidx.compose.ui.geometry.Offset(0f, 500f))
                up()
            }
        }

        assert(callCount > 0)
    }

    @Test
    fun stateRestorationTest() {
        val restorationTester = StateRestorationTester(composeTestRule)
        val state = QueueUiState.Success(persistentListOf(
            EpisodeWithPodcast(ep1, dummyPodcast),
            EpisodeWithPodcast(ep2, dummyPodcast)
        ))

        restorationTester.setContent {
            PodcastsTheme {
                QueueScreen(
                    uiState = state,
                    queueTimeRemaining = 2000,
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

        // Assert handles are displayed
        composeTestRule.onNodeWithTag("reorder_handle_ep1", useUnmergedTree = true).assertExists()

        // Drag ep1 down
        composeTestRule.onNodeWithTag("reorder_handle_ep1", useUnmergedTree = true).performTouchInput {
            down(center)
            moveBy(androidx.compose.ui.geometry.Offset(0f, 300f))
            up()
        }

        // Simulate process death and restoration
        restorationTester.emulateSavedInstanceStateRestore()

        composeTestRule.onNodeWithTag("reorder_handle_ep1", useUnmergedTree = true).assertExists()
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

        composeTestRule.onNodeWithTag("queue_list")
            .performTouchInput {
                swipeDown()
            }

        org.junit.Assert.assertTrue("onRefreshAll should be called when non-empty queue is pulled down", refreshCalled)
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

        composeTestRule.onNodeWithTag("queue_list")
            .performTouchInput {
                swipeDown()
            }

        org.junit.Assert.assertTrue("onRefreshAll should be called when empty queue is pulled down", refreshCalled)
    }

    @Test
    fun listeningTimeHidesWhenQueueIsEmpty() {
        val state = QueueUiState.Success(persistentListOf())

        composeTestRule.setContent {
            PodcastsTheme {
                QueueScreen(
                    uiState = state,
                    queueTimeRemaining = 120_000L, // 2 minutes
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

        composeTestRule.onNodeWithText("2m of queue listening time").assertDoesNotExist()
    }

    @Test
    fun listeningTimeShowsWhenQueueIsNotEmpty() {
        val state = QueueUiState.Success(persistentListOf(EpisodeWithPodcast(ep1, dummyPodcast)))

        composeTestRule.setContent {
            PodcastsTheme {
                QueueScreen(
                    uiState = state,
                    queueTimeRemaining = 120_000L, // 2 minutes
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

        composeTestRule.onNodeWithText("2m of queue listening time").assertIsDisplayed()
    }
}
