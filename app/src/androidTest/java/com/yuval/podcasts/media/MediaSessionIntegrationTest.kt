package com.yuval.podcasts.media

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import androidx.media3.session.SessionToken
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yuval.podcasts.data.Constants
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MediaSessionIntegrationTest {

    private lateinit var context: Context
    private lateinit var controller: MediaController

    @Before
    fun setup() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        val sessionToken = SessionToken(context, ComponentName(context, PlaybackService::class.java))

        controller = withContext(Dispatchers.Main) {
            MediaController.Builder(context, sessionToken).buildAsync().await()
        }
    }

    @After
    fun teardown() = runBlocking {
        if (::controller.isInitialized) {
            withContext(Dispatchers.Main) {
                controller.pause()
                controller.release()
            }
        }
    }

    @Test
    fun testCustomCommandsAcceptedBySession() = runBlocking {
        withContext(Dispatchers.Main) {
            val commandRewind = SessionCommand(Constants.COMMAND_REWIND_10, Bundle.EMPTY)
            val commandSkip = SessionCommand(Constants.COMMAND_SKIP_30, Bundle.EMPTY)

            val resultRewind = controller.sendCustomCommand(commandRewind, Bundle.EMPTY).await()
            val resultSkip = controller.sendCustomCommand(commandSkip, Bundle.EMPTY).await()

            assertEquals(SessionResult.RESULT_SUCCESS, resultRewind.resultCode)
            assertEquals(SessionResult.RESULT_SUCCESS, resultSkip.resultCode)
        }
    }

    @Test
    fun testPlayAndPauseTogglePlayWhenReady() = runBlocking {
        withContext(Dispatchers.Main) {
            // Establish a known paused baseline without relying on ambient playback state
            if (controller.playWhenReady) {
                awaitPlayWhenReady(false) { controller.pause() }
            }
            assertFalse(controller.playWhenReady)

            // Transition false -> true via play()
            awaitPlayWhenReady(true) { controller.play() }
            assertTrue("play() should set playWhenReady = true", controller.playWhenReady)

            // Transition true -> false via pause()
            awaitPlayWhenReady(false) { controller.pause() }
            assertFalse("pause() should set playWhenReady = false", controller.playWhenReady)
        }
    }

    private suspend fun awaitPlayWhenReady(expected: Boolean, action: () -> Unit) {
        val deferred = CompletableDeferred<Boolean>()
        val listener = object : Player.Listener {
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (playWhenReady == expected && !deferred.isCompleted) {
                    deferred.complete(playWhenReady)
                }
            }
        }
        controller.addListener(listener)
        try {
            action()
            if (controller.playWhenReady == expected) {
                deferred.complete(expected)
            }
            withTimeout(5_000L) {
                deferred.await()
            }
        } finally {
            controller.removeListener(listener)
        }
    }
}
