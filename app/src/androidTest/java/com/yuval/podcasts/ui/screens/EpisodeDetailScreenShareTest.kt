package com.yuval.podcasts.ui.screens

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.Intents.intended
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.espresso.intent.matcher.IntentMatchers.hasExtra
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yuval.podcasts.data.db.entity.Episode
import com.yuval.podcasts.data.db.entity.EpisodeWithPodcast
import com.yuval.podcasts.data.db.entity.Podcast
import com.yuval.podcasts.ui.viewmodel.EpisodeDetailUiState
import org.hamcrest.Matchers.allOf
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EpisodeDetailScreenShareTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Before
    fun setup() {
        Intents.init()
    }

    @After
    fun teardown() {
        Intents.release()
    }

    @Test
    fun shareLocalEpisode_sharesOnlyTextMetadata() {
        val podcast = Podcast(com.yuval.podcasts.data.Constants.LOCAL_PODCAST_FEED_URL, "Local Files", "", "", "")
        val episode = Episode(
            "id1", com.yuval.podcasts.data.Constants.LOCAL_PODCAST_FEED_URL, "My Recording", "Desc", "path/to/file",
            null, null, 0L, 0L, 2, "path/to/file", false, 0L, null
        )
        val uiState = EpisodeDetailUiState.Success(EpisodeWithPodcast(episode, podcast), false, emptyList())

        composeTestRule.setContent {
            EpisodeDetailScreen(
                uiState = uiState,
                onBack = {},
                onAddToQueue = {},
                onChapterClick = {}
            )
        }

        val shareText = context.getString(com.yuval.podcasts.R.string.share)
        composeTestRule.onNodeWithContentDescription(shareText).performClick()

        val expectedText = context.getString(com.yuval.podcasts.R.string.listening_to, "My Recording")

        // In Compose, Intent.createChooser is used. We can match the target intent inside the chooser
        intended(
            allOf(
                hasAction(Intent.ACTION_CHOOSER),
                hasExtra(Intent.EXTRA_INTENT, allOf(
                    hasAction(Intent.ACTION_SEND),
                    hasExtra(Intent.EXTRA_TEXT, expectedText)
                ))
            )
        )
    }

    @Test
    fun shareRemoteEpisode_sharesUrl() {
        val podcast = Podcast("http://feed", "Remote Podcast", "", "", "")
        val episode = Episode(
            "id2", "http://feed", "Remote Episode", "Desc", "http://audio",
            null, "http://weblink", 0L, 0L, 0, null, false, 0L, null
        )
        val uiState = EpisodeDetailUiState.Success(EpisodeWithPodcast(episode, podcast), false, emptyList())

        composeTestRule.setContent {
            EpisodeDetailScreen(
                uiState = uiState,
                onBack = {},
                onAddToQueue = {},
                onChapterClick = {}
            )
        }

        val shareText = context.getString(com.yuval.podcasts.R.string.share)
        composeTestRule.onNodeWithContentDescription(shareText).performClick()

        val expectedText = "Remote Episode\nhttp://weblink"

        intended(
            allOf(
                hasAction(Intent.ACTION_CHOOSER),
                hasExtra(Intent.EXTRA_INTENT, allOf(
                    hasAction(Intent.ACTION_SEND),
                    hasExtra(Intent.EXTRA_TEXT, expectedText)
                ))
            )
        )
    }
}
