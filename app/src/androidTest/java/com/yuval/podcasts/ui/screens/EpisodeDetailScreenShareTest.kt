package com.yuval.podcasts.ui.screens

import android.app.Activity
import android.app.Instrumentation
import android.content.Context
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.AnnotatedString
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.Intents.intended
import androidx.test.espresso.intent.Intents.intending
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.espresso.intent.matcher.IntentMatchers.hasExtra
import androidx.test.espresso.intent.matcher.IntentMatchers.hasType
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yuval.podcasts.R
import com.yuval.podcasts.data.Constants
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
        // Stub ACTION_CHOOSER so the real Android share sheet doesn't steal focus
        intending(hasAction(Intent.ACTION_CHOOSER))
            .respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, null))
    }

    @After
    fun teardown() {
        Intents.release()
    }

    @Test
    fun shareLocalEpisode_sharesOnlyTextMetadataEvenIfWebLinkPresent() {
        val podcast = Podcast(Constants.LOCAL_PODCAST_FEED_URL, "Local Files", "", "", "")
        // Provide a non-null episodeWebLink so passing requires the `!episode.isLocal` guard
        val episode = Episode(
            "id1", Constants.LOCAL_PODCAST_FEED_URL, "My Recording", "Desc", "path/to/file",
            null, "https://should-not-be-shared.example.com", 0L, 0L, 2, "path/to/file", false, 0L, null
        )
        val uiState = EpisodeDetailUiState.Success(
            episodeWithPodcast = EpisodeWithPodcast(episode, podcast),
            isInQueue = false,
            chapters = emptyList(),
            annotatedDescription = AnnotatedString("Desc")
        )

        composeTestRule.setContent {
            EpisodeDetailScreen(
                uiState = uiState,
                onBack = {},
                onAddToQueue = {},
                onChapterClick = {}
            )
        }

        val shareText = context.getString(R.string.share)
        composeTestRule.onNodeWithText(shareText).performClick()

        val expectedText = context.getString(R.string.listening_to, "My Recording")

        intended(
            allOf(
                hasAction(Intent.ACTION_CHOOSER),
                hasExtra(
                    Intent.EXTRA_INTENT,
                    allOf(
                        hasAction(Intent.ACTION_SEND),
                        hasType(Constants.MIME_TYPE_TEXT_PLAIN),
                        hasExtra(Intent.EXTRA_TEXT, expectedText)
                    )
                )
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
        val uiState = EpisodeDetailUiState.Success(
            episodeWithPodcast = EpisodeWithPodcast(episode, podcast),
            isInQueue = false,
            chapters = emptyList(),
            annotatedDescription = AnnotatedString("Desc")
        )

        composeTestRule.setContent {
            EpisodeDetailScreen(
                uiState = uiState,
                onBack = {},
                onAddToQueue = {},
                onChapterClick = {}
            )
        }

        val shareText = context.getString(R.string.share)
        composeTestRule.onNodeWithText(shareText).performClick()

        val expectedText = context.getString(R.string.share_episode_format, "Remote Episode", "http://weblink")

        intended(
            allOf(
                hasAction(Intent.ACTION_CHOOSER),
                hasExtra(
                    Intent.EXTRA_INTENT,
                    allOf(
                        hasAction(Intent.ACTION_SEND),
                        hasType(Constants.MIME_TYPE_TEXT_PLAIN),
                        hasExtra(Intent.EXTRA_TEXT, expectedText)
                    )
                )
            )
        )
    }
}
