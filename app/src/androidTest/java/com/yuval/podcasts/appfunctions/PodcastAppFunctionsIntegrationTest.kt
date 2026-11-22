package com.yuval.podcasts.appfunctions

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yuval.podcasts.PodcastApplication
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PodcastAppFunctionsIntegrationTest {

    private lateinit var app: PodcastApplication
    private lateinit var appFunctions: PodcastAppFunctions

    @Before
    fun setup() {
        app = ApplicationProvider.getApplicationContext()
        appFunctions = app.podcastAppFunctions
        assertNotNull("PodcastAppFunctions should be injected", appFunctions)
    }

    @Test
    fun testAddDebugLog() = runBlocking {
        val result = appFunctions.addDebugLog("Integration test note")
        assertEquals("Log added", result)
    }

    @Test
    fun testResumeQueue() = runBlocking {
        val result = appFunctions.resumeQueue()
        assertEquals("Resuming playback", result)
    }

    @Test
    fun testStopPlayback() = runBlocking {
        val result = appFunctions.stopPlayback()
        assertEquals("Playback stopped", result)
    }

    @Test
    fun testSkipForward() = runBlocking {
        val result = appFunctions.skipForward()
        assertEquals("Skipped forward 30 seconds", result)
    }

    @Test
    fun testSkipBackward() = runBlocking {
        val result = appFunctions.skipBackward()
        assertEquals("Skipped backward 15 seconds", result)
    }

    @Test
    fun testNextEpisode() = runBlocking {
        val result = appFunctions.nextEpisode()
        assertEquals("Playing next episode", result)
    }

    @Test
    fun testRefreshNewEpisodes() = runBlocking {
        val result = appFunctions.refreshNewEpisodes()
        assertNotNull(result)
    }

    @Test
    fun testMoveSubscriptionToBottom_notFound() = runBlocking {
        val result = appFunctions.moveSubscriptionToBottom("Non-existent podcast")
        assertEquals("Could not find a podcast subscription matching 'Non-existent podcast'", result)
    }
}
