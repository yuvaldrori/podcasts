package com.yuval.podcasts.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yuval.podcasts.data.db.entity.Episode
import com.yuval.podcasts.data.db.entity.Podcast
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EpisodeItemTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun episodeItem_rendersCleanDescriptionWithoutHtmlTags() {
        val episode = Episode(
            id = "ep1",
            podcastFeedUrl = "https://feed.url",
            title = "Sample Episode",
            description = "<p dir=\"rtl\">Description with <strong>bold</strong> text&nbsp;</p>",
            audioUrl = "https://audio.url",
            pubDate = 1000L,
            duration = 300L,
            downloadStatus = 0,
            localFilePath = null
        )

        composeTestRule.setContent {
            MaterialTheme {
                EpisodeItem(episode = episode)
            }
        }

        // Exact match (substring = false) ensures leading/trailing HTML tags & entities were stripped
        composeTestRule.onNodeWithText("Description with bold text", substring = false)
            .assertIsDisplayed()
    }

    @Test
    fun podcastItem_rendersTitleAndDescription() {
        val podcast = Podcast(
            feedUrl = "https://feed.url",
            title = "Sample Podcast",
            description = "Channel italic & text",
            imageUrl = "https://img.url",
            website = "https://web.url"
        )

        composeTestRule.setContent {
            MaterialTheme {
                PodcastItem(podcast = podcast, onClick = {})
            }
        }

        composeTestRule.onNodeWithText("Sample Podcast", substring = false)
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("Channel italic & text", substring = false)
            .assertIsDisplayed()
    }
}
