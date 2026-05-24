package com.yuval.podcasts.appfunctions

import androidx.appfunctions.AppFunctionContext
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yuval.podcasts.PodcastApplication
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.lang.reflect.Proxy

@RunWith(AndroidJUnit4::class)
class PodcastAppFunctionsIntegrationTest {

    private lateinit var app: PodcastApplication
    private lateinit var appFunctions: PodcastAppFunctions
    
    // Create a dynamic proxy for AppFunctionContext to avoid NoClassDefFoundError/MockK issues on Android Dex VM
    private val context = Proxy.newProxyInstance(
        AppFunctionContext::class.java.classLoader,
        arrayOf(AppFunctionContext::class.java)
    ) { _, _, _ -> null } as AppFunctionContext

    @Before
    fun setup() {
        app = ApplicationProvider.getApplicationContext()
        appFunctions = app.podcastAppFunctions
        assertNotNull("PodcastAppFunctions should be injected", appFunctions)
    }

    @Test
    fun testAddDebugLog() = runBlocking {
        val result = appFunctions.addDebugLog(context, "Integration test note")
        assertEquals("Log added", result)
    }

    @Test
    fun testResumeQueue() = runBlocking {
        val result = appFunctions.resumeQueue(context)
        assertEquals("Resuming playback", result)
    }

    @Test
    fun testStopPlayback() = runBlocking {
        val result = appFunctions.stopPlayback(context)
        assertEquals("Playback stopped", result)
    }

    @Test
    fun testSkipForward() = runBlocking {
        val result = appFunctions.skipForward(context)
        assertEquals("Skipped forward 30 seconds", result)
    }

    @Test
    fun testSkipBackward() = runBlocking {
        val result = appFunctions.skipBackward(context)
        assertEquals("Skipped backward 15 seconds", result)
    }

    @Test
    fun testNextEpisode() = runBlocking {
        val result = appFunctions.nextEpisode(context)
        assertEquals("Playing next episode", result)
    }

    @Test
    fun testRefreshNewEpisodes() = runBlocking {
        val result = appFunctions.refreshNewEpisodes(context)
        assertNotNull(result)
    }

    @Test
    fun testMoveSubscriptionToBottom_notFound() = runBlocking {
        val result = appFunctions.moveSubscriptionToBottom(context, "Non-existent podcast")
        assertEquals("Could not find a podcast subscription matching 'Non-existent podcast'", result)
    }
}
