package com.yuval.podcasts.ui.screens

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yuval.podcasts.R
import com.yuval.podcasts.data.db.entity.Episode
import com.yuval.podcasts.data.db.entity.EpisodeWithPodcast
import com.yuval.podcasts.data.db.entity.Podcast
import com.yuval.podcasts.ui.utils.UiText
import com.yuval.podcasts.ui.viewmodel.FeedsUiState
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NewEpisodesScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private fun sampleEpisode(index: Int): EpisodeWithPodcast = EpisodeWithPodcast(
        episode = Episode(
            id = "ep$index",
            podcastFeedUrl = "feed",
            title = "Episode $index",
            description = "Desc",
            audioUrl = "url",
            imageUrl = null,
            episodeWebLink = null,
            pubDate = 1000L * index,
            duration = 300L,
            downloadStatus = 0,
            localFilePath = null,
            isPlayed = false,
            lastPlayedPosition = 0L,
            completedAt = null
        ),
        podcast = Podcast("feed", "Podcast", "Desc", "url", "web")
    )

    @Test
    fun testSnackbarIsShown_whenErrorMessagePresent() {
        val errorText = "Network refresh failed"
        val uiState = FeedsUiState.Success(
            unplayedEpisodes = persistentListOf(sampleEpisode(1)),
            errorMessage = UiText.DynamicString(errorText)
        )

        composeTestRule.setContent {
            MaterialTheme {
                NewEpisodesScreen(
                    uiState = uiState,
                    onEpisodeClick = {},
                    onRefreshAll = {},
                    onDismissAll = {},
                    onDismissEpisode = {},
                    onAddToQueue = {},
                    onClearError = {}
                )
            }
        }

        composeTestRule.onNodeWithText(errorText).assertIsDisplayed()
    }

    @Test
    fun testScrollAndEpisodeActionButtons() {
        val episodes = (1..20).map { sampleEpisode(it) }.toImmutableList()
        val uiState = FeedsUiState.Success(unplayedEpisodes = episodes)
        var dismissedEpisodeId: String? = null
        var queuedEpisodeId: String? = null

        composeTestRule.setContent {
            MaterialTheme {
                NewEpisodesScreen(
                    uiState = uiState,
                    onEpisodeClick = {},
                    onRefreshAll = {},
                    onDismissAll = {},
                    onDismissEpisode = { dismissedEpisodeId = it.id },
                    onAddToQueue = { queuedEpisodeId = it.id },
                    onClearError = {}
                )
            }
        }

        // Scroll to bottom and verify Episode 20 is rendered
        composeTestRule.onNodeWithTag("episode_list").performScrollToIndex(19)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Episode 20").assertIsDisplayed()

        // Scroll back to top and invoke Dismiss and Add to Queue buttons on Episode 1
        composeTestRule.onNodeWithTag("episode_list").performScrollToIndex(0)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Episode 1").assertIsDisplayed()

        composeTestRule.onAllNodesWithContentDescription(context.getString(R.string.dismiss))
            .onFirst()
            .performClick()
        composeTestRule.onAllNodesWithContentDescription(context.getString(R.string.add_to_queue))
            .onFirst()
            .performClick()
        composeTestRule.waitForIdle()

        assertEquals("ep1", dismissedEpisodeId)
        assertEquals("ep1", queuedEpisodeId)
    }

    @Test
    fun testPullToRefreshTriggers_whenListIsNotEmpty() {
        val uiState = FeedsUiState.Success(unplayedEpisodes = persistentListOf(sampleEpisode(1)))
        var refreshCalled = false

        composeTestRule.setContent {
            MaterialTheme {
                NewEpisodesScreen(
                    uiState = uiState,
                    onEpisodeClick = {},
                    onRefreshAll = { refreshCalled = true },
                    onDismissAll = {},
                    onDismissEpisode = {},
                    onAddToQueue = {},
                    onClearError = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("episode_list").performTouchInput { swipeDown() }
        composeTestRule.waitForIdle()

        assertTrue("onRefreshAll should be called when non-empty list is pulled down", refreshCalled)
    }

    @Test
    fun testPullToRefreshTriggers_whenListIsEmpty() {
        val uiState = FeedsUiState.Success(unplayedEpisodes = persistentListOf())
        var refreshCalled = false

        composeTestRule.setContent {
            MaterialTheme {
                NewEpisodesScreen(
                    uiState = uiState,
                    onEpisodeClick = {},
                    onRefreshAll = { refreshCalled = true },
                    onDismissAll = {},
                    onDismissEpisode = {},
                    onAddToQueue = {},
                    onClearError = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("episode_list").performTouchInput { swipeDown() }
        composeTestRule.waitForIdle()

        assertTrue("onRefreshAll should be called when empty list is pulled down", refreshCalled)
    }
}
