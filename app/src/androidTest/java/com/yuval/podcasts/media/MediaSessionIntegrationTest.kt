package com.yuval.podcasts.media

import android.content.ComponentName
import android.content.Context
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit
import androidx.media3.session.SessionCommand
import android.os.Bundle
import androidx.media3.common.Player
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

@RunWith(AndroidJUnit4::class)
class MediaSessionIntegrationTest {

    private lateinit var context: Context
    private lateinit var controllerFuture: ListenableFuture<MediaController>
    private lateinit var controller: MediaController

    @Before
    fun setup() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        val sessionToken = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        
        withContext(Dispatchers.Main) {
            controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
            controller = controllerFuture.get(10, TimeUnit.SECONDS)
        }
    }

    @After
    fun teardown() = runBlocking {
        withContext(Dispatchers.Main) {
            MediaController.releaseFuture(controllerFuture)
        }
    }

    @Test
    fun testCustomCommands() = runBlocking {
        withContext(Dispatchers.Main) {
            // Verify our custom commands are accepted by the session
            val commandRewind = SessionCommand("REWIND_10", Bundle.EMPTY)
            val commandSkip = SessionCommand("SKIP_30", Bundle.EMPTY)
            
            val resultRewind = controller.sendCustomCommand(commandRewind, Bundle.EMPTY).get(5, TimeUnit.SECONDS)
            val resultSkip = controller.sendCustomCommand(commandSkip, Bundle.EMPTY).get(5, TimeUnit.SECONDS)
            
            assertEquals(androidx.media3.session.SessionResult.RESULT_SUCCESS, resultRewind.resultCode)
            assertEquals(androidx.media3.session.SessionResult.RESULT_SUCCESS, resultSkip.resultCode)
        }
    }

    @Test
    fun testPlayPauseCommand() = runBlocking {
        withContext(Dispatchers.Main) {
            // The service might start in various states based on previous runs, 
            // but we can at least verify we can toggle it.
            if (controller.isPlaying) {
                controller.pause()
                // Wait for state change
                Thread.sleep(500)
                assertTrue(!controller.isPlaying || controller.playbackState == Player.STATE_BUFFERING)
            } else {
                controller.play()
                Thread.sleep(500)
                // Note: might be buffering if no network, but we verify the command was received
                assertTrue(controller.playWhenReady)
            }
        }
    }
}
