package com.yuval.podcasts.media

import android.content.ComponentName
import android.content.Context
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaBrowser
import androidx.media3.session.SessionToken
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yuval.podcasts.data.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MediaBrowserIntegrationTest {

    private lateinit var context: Context
    private lateinit var browser: MediaBrowser

    @Before
    fun setup() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        val sessionToken = SessionToken(context, ComponentName(context, PlaybackService::class.java))

        browser = withContext(Dispatchers.Main) {
            MediaBrowser.Builder(context, sessionToken).buildAsync().await()
        }
    }

    @After
    fun teardown() = runBlocking {
        if (::browser.isInitialized) {
            withContext(Dispatchers.Main) {
                browser.release()
            }
        }
    }

    @Test
    fun testGetLibraryRoot() = runBlocking {
        val rootResult = withContext(Dispatchers.Main) {
            browser.getLibraryRoot(null).await()
        }
        val rootItem = rootResult.value
        assertEquals(
            "Expected RESULT_SUCCESS, got: ${rootResult.resultCode}",
            LibraryResult.RESULT_SUCCESS,
            rootResult.resultCode
        )
        assertNotNull(rootItem)
        assertEquals(Constants.MEDIA_LIBRARY_ROOT_ID, rootItem?.mediaId)
        assertTrue(rootItem?.mediaMetadata?.isBrowsable == true)
        assertFalse(rootItem?.mediaMetadata?.isPlayable == true)
    }

    @Test
    fun testGetChildrenOfRoot() = runBlocking {
        val childrenResult = withContext(Dispatchers.Main) {
            browser.getChildren(Constants.MEDIA_LIBRARY_ROOT_ID, 0, 100, null).await()
        }
        val children = childrenResult.value
        assertEquals(
            "Expected RESULT_SUCCESS, got: ${childrenResult.resultCode}",
            LibraryResult.RESULT_SUCCESS,
            childrenResult.resultCode
        )
        assertNotNull(children)
        assertEquals(1, children!!.size)
        assertEquals(Constants.MEDIA_LIBRARY_QUEUE_ID, children[0].mediaId)
        assertEquals("Queue", children[0].mediaMetadata.title)
        assertTrue(children[0].mediaMetadata.isBrowsable == true)
        assertFalse(children[0].mediaMetadata.isPlayable == true)
    }

    @Test
    fun testGetChildrenOfQueue() = runBlocking {
        val queueResult = withContext(Dispatchers.Main) {
            browser.getChildren(Constants.MEDIA_LIBRARY_QUEUE_ID, 0, 100, null).await()
        }
        assertEquals(
            "Expected RESULT_SUCCESS, got: ${queueResult.resultCode}",
            LibraryResult.RESULT_SUCCESS,
            queueResult.resultCode
        )
        assertNotNull(queueResult.value)
    }
}
