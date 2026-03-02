package com.yuval.podcasts.media

import android.net.Uri
import androidx.media3.common.MediaItem
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
import org.junit.Test
import kotlinx.coroutines.guava.await
import java.util.concurrent.TimeUnit

class PlaybackServiceResumptionTest {

    @Test
    fun playbackResumption_loadsQueueFromDatabase() = runTest {
        mockkStatic(Uri::class)
        val mockUri = mockk<Uri>(relaxed = true)
        every { Uri.parse(any()) } returns mockUri
        every { mockUri.toString() } returns "http://audio1.mp3"

        val queueDao = mockk<QueueDao>()
        
        val episodes = listOf(
            Episode("ep1", "feed", "Title 1", "Desc 1", "http://audio1.mp3", null, 0L, 0L, 0, null, false, 5000L),
            Episode("ep2", "feed", "Title 2", "Desc 2", "http://audio2.mp3", null, 0L, 0L, 0, null, false, 0L)
        )
        
        // This simulates the behavior we want to build inside our PlaybackService Callback
        coEvery { queueDao.getQueueEpisodes() } returns flowOf(episodes)
        
        // Our planned Callback implementation
        val callback = PlaybackResumptionCallback(queueDao, this)
        
        val future = callback.onPlaybackResumption(
            mockk(), 
            mockk(relaxed = true)
        )
        


        // Wait for the future to complete without blocking the test thread dispatcher
        val result = future.await()
        
        assertNotNull(result)
        assertEquals(2, result.mediaItems.size)
        assertEquals("ep1", result.mediaItems[0].mediaId)
        assertEquals("http://audio1.mp3", result.mediaItems[0].localConfiguration?.uri?.toString())
        assertEquals(0, result.startIndex)
        assertEquals(5000L, result.startPositionMs)
    }
}