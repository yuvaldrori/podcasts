package com.yuval.podcasts.work

import androidx.work.NetworkType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DownloadWorkerRequestTest {

    @Test
    fun createWorkRequest_buildsWithCorrectParameters() {
        val request = DownloadWorker.createWorkRequest(
            episodeId = "ep_123",
            audioUrl = "https://example.com/audio.mp3",
            title = "Test Episode Title",
            isExpedited = false
        )

        // Verify Input Data
        val inputData = request.workSpec.input
        assertEquals("ep_123", inputData.getString(DownloadWorker.KEY_EPISODE_ID))
        assertEquals("https://example.com/audio.mp3", inputData.getString(DownloadWorker.KEY_AUDIO_URL))
        assertEquals("Test Episode Title", inputData.getString(DownloadWorker.KEY_EPISODE_TITLE))

        // Verify Constraints
        val constraints = request.workSpec.constraints
        assertEquals(NetworkType.CONNECTED, constraints.requiredNetworkType)

        // Verify Expedited status (should be false)
        assertTrue(!request.workSpec.expedited)
    }

    @Test
    fun createWorkRequest_withExpeditedTrue_buildsExpeditedRequest() {
        val request = DownloadWorker.createWorkRequest(
            episodeId = "ep_456",
            audioUrl = "https://example.com/audio_fast.mp3",
            title = "Fast Test Episode Title",
            isExpedited = true
        )

        // Verify Expedited status
        assertTrue(request.workSpec.expedited)
    }
}
