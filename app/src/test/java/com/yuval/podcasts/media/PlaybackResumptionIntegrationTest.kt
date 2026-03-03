package com.yuval.podcasts.media

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaSession
import com.google.common.util.concurrent.ListenableFuture
import com.yuval.podcasts.data.db.dao.QueueDao
import com.yuval.podcasts.data.db.entity.Episode
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.guava.await

class PlaybackResumptionIntegrationTest {

    @Test
    fun playbackResumption_withMalformedUri_catchesAndLogsWithoutSilentCrash() = runTest {
        mockkStatic(Uri::class)
        mockkStatic(android.util.Log::class)
        every { android.util.Log.d(any(), any()) } returns 0
        every { android.util.Log.e(any(), any(), any()) } returns 0
        
        val queueDao = mockk<QueueDao>()
        
        // Setup an episode with a potentially problematic URI structure or missing metadata that would crash the standard parser
        val episodes = listOf(
            Episode("ep1", "feed", "Title 1", "Desc 1", "http://audio1.mp3", null, 0L, 0L, 0, null, false, 5000L)
        )
        coEvery { queueDao.getQueueEpisodes() } returns flowOf(episodes)
        
        // If Uri.parse throws an exception (simulating a malformed URI or Android framework issue), 
        // the future should gracefully handle it and not crash the thread.
        every { Uri.parse(any()) } throws IllegalArgumentException("Malformed URI")
        
        val callback = PlaybackResumptionCallback(queueDao, this)
        
        val future = callback.onPlaybackResumption(
            mockk(), 
            mockk(relaxed = true)
        )
        
        // The future should successfully resolve to an empty list instead of crashing
        val result = future.await()
        assertTrue("Result should be empty", result.mediaItems.isEmpty())
    }

    @Test
    fun playbackResumption_addsRequiredMetadataForExoPlayer() = runTest {
        mockkStatic(Uri::class)
        mockkStatic(android.util.Log::class)
        every { android.util.Log.d(any(), any()) } returns 0
        every { android.util.Log.e(any(), any(), any()) } returns 0
        
        val queueDao = mockk<QueueDao>()
        val mockUri = mockk<Uri>(relaxed = true)
        every { Uri.parse(any()) } returns mockUri
        
        val episodes = listOf(
            Episode("ep1", "feed", "Title 1", "Desc 1", "http://audio1.mp3", "http://image.jpg", 0L, 0L, 0, null, false, 5000L)
        )
        coEvery { queueDao.getQueueEpisodes() } returns flowOf(episodes)
        
        val callback = PlaybackResumptionCallback(queueDao, this)
        val future = callback.onPlaybackResumption(mockk(), mockk(relaxed = true))
        
        val result = future.await()
        val mediaItem = result.mediaItems[0]
        
        // We expect the fix to attach MediaMetadata (title, artwork) so the lockscreen player works immediately
        assertNotNull("MediaItem should have metadata populated", mediaItem.mediaMetadata.title)
        assertEquals("Title 1", mediaItem.mediaMetadata.title.toString())
    }
}
