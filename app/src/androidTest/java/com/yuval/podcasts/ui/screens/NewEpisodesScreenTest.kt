package com.yuval.podcasts.ui.screens

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsDisplayed
import com.yuval.podcasts.ui.viewmodel.FeedsUiState
import org.junit.Rule
import org.junit.Test

class NewEpisodesScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun testSnackbarIsShown() {
        val errorMessage = "Network timeout. Try again later."

        val fakeUiState = FeedsUiState.Success(
            errorMessage = errorMessage
        )

        composeTestRule.setContent {
            androidx.compose.material3.MaterialTheme {
                NewEpisodesScreen(
                    uiState = fakeUiState,
                    onEpisodeClick = {},
                    onRefreshAll = {},
                    onDismissAll = {},
                    onDismissEpisode = {},
                    onAddToQueue = {},
                    onClearError = { }
                )
            }
        }

        // Wait for Compose to render the snackbar
        composeTestRule.waitForIdle()

        // Assert that the Snackbar containing our error message is displayed
        composeTestRule.onNodeWithText(errorMessage).assertIsDisplayed()
    }
}
