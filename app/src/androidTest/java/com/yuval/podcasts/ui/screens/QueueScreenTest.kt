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
    private val ep1 = Episode("ep1", "feed", "Ep 1", "Desc", "url", null, null, 0L, 1000, 0, null, false, 0, null, 1)
    private val ep2 = Episode("ep2", "feed", "Ep 2", "Desc", "url", null, null, 0L, 1000, 0, null, false, 0, null, 2)

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

        // Wait until handles are displayed
        composeTestRule.waitUntil(10000) {
            composeTestRule.onAllNodesWithTag("reorder_handle_ep1").fetchSemanticsNodes().isNotEmpty()
        }

        // Rapidly perform multiple reorder actions
        val handle1 = composeTestRule.onNodeWithTag("reorder_handle_ep1")
        
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

        composeTestRule.waitUntil(10000) {
            composeTestRule.onAllNodesWithTag("reorder_handle_ep1").fetchSemanticsNodes().isNotEmpty()
        }

        // Drag ep1 down
        composeTestRule.onNodeWithTag("reorder_handle_ep1").performTouchInput {
            down(center)
            moveBy(androidx.compose.ui.geometry.Offset(0f, 300f))
            up()
        }

        // Simulate process death and restoration
        restorationTester.emulateSavedInstanceStateRestore()

        composeTestRule.onNodeWithTag("reorder_handle_ep1").assertExists()
    }
}
