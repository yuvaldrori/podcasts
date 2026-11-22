package com.yuval.podcasts.data.repository

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import com.yuval.podcasts.data.Constants
import com.yuval.podcasts.utils.LogManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.unmockkConstructor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class LocalMediaDataSourceTest {

    @get:Rule
    val tmpFolder = TemporaryFolder()

    private lateinit var context: Context
    private lateinit var contentResolver: ContentResolver
    private lateinit var dataSource: LocalMediaDataSource
    private lateinit var filesDir: File

    @Before
    fun setup() {
        filesDir = tmpFolder.newFolder("files")
        context = mockk(relaxed = true)
        contentResolver = mockk(relaxed = true)
        every { context.filesDir } returns filesDir
        every { context.contentResolver } returns contentResolver

        dataSource = LocalMediaDataSource(context, Dispatchers.Unconfined, mockk<LogManager>(relaxed = true))
    }

    @After
    fun teardown() {
        unmockkConstructor(MediaMetadataRetriever::class)
    }

    private fun stubContentUri(displayName: String, content: ByteArray = "audio-bytes".toByteArray()): Uri {
        val uri = mockk<Uri>()
        every { uri.scheme } returns "content"
        val cursor = mockk<Cursor>(relaxed = true)
        every { contentResolver.query(uri, null, null, null, null) } returns cursor
        every { cursor.moveToFirst() } returns true
        every { cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME) } returns 0
        every { cursor.getString(0) } returns displayName
        every { contentResolver.openInputStream(uri) } returns content.inputStream()
        return uri
    }

    @Test
    fun copyAndExtract_withPathTraversalFileName_sanitizesAndStaysInDir() = runTest {
        mockkConstructor(MediaMetadataRetriever::class)
        every { anyConstructed<MediaMetadataRetriever>().setDataSource(any<String>()) } returns Unit
        every { anyConstructed<MediaMetadataRetriever>().extractMetadata(any()) } returns null
        every { anyConstructed<MediaMetadataRetriever>().release() } returns Unit

        val traversalUri = stubContentUri("../../../etc/passwd", "safe-audio".toByteArray())

        val result = dataSource.copyAndExtract(traversalUri)

        assertTrue("Expected sanitized traversal filename to succeed inside local dir, got: $result", result.isSuccess)
        val destFile = result.getOrThrow().destFile
        val destDir = File(filesDir, Constants.LOCAL_UPLOAD_DIR_NAME)
        assertTrue(
            "Destination must remain inside local_podcasts directory",
            destFile.canonicalPath.startsWith(destDir.canonicalPath + File.separator)
        )
        assertFalse("Sanitized filename must not contain path separators", destFile.name.contains('/'))
        assertTrue(destFile.exists())
        assertEquals("safe-audio", destFile.readText())
    }

    @Test
    fun copyAndExtract_withDotDotFileName_rejectsWithSecurityException() = runTest {
        val dotDotUri = stubContentUri("..")

        val result = dataSource.copyAndExtract(dotDotUri)

        assertTrue("Expected '..' filename to fail", result.isFailure)
        assertTrue(
            "Expected SecurityException for '..' traversal, got: ${result.exceptionOrNull()}",
            result.exceptionOrNull() is SecurityException
        )
    }
}
