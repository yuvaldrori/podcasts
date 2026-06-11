package com.yuval.podcasts.media

import android.content.ComponentName
import android.content.Context
import androidx.media3.session.MediaBrowser
import androidx.media3.session.SessionToken
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import kotlinx.coroutines.guava.await

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
        withContext(Dispatchers.Main) {
            browser.release()
        }
    }

    @Test
    fun testGetLibraryRoot() = runBlocking {
        val rootResult = withContext(Dispatchers.Main) {
            browser.getLibraryRoot(null).await()
        }
        val rootItem = rootResult.value
        assertNotNull(rootItem)
        assertEquals("root", rootItem?.mediaId)
        assertTrue(rootItem?.mediaMetadata?.isBrowsable == true)
    }

    @Test
    fun testGetChildrenOfRoot() = runBlocking {
        val childrenResult = withContext(Dispatchers.Main) {
            browser.getChildren("root", 0, 100, null).await()
        }
        val children = childrenResult.value
        assertNotNull(children)
        assertTrue(children!!.isNotEmpty())
        assertEquals("queue", children[0].mediaId)
        assertEquals("Queue", children[0].mediaMetadata.title)
        assertTrue(children[0].mediaMetadata.isBrowsable == true)
    }
}
