package com.yuval.podcasts.data.repository
import kotlinx.coroutines.Dispatchers

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import androidx.work.WorkManager
import com.yuval.podcasts.data.db.AppDatabase
import com.yuval.podcasts.data.db.dao.EpisodeDao
import com.yuval.podcasts.data.db.dao.PodcastDao
import com.yuval.podcasts.data.db.dao.QueueDao
import com.yuval.podcasts.data.db.entity.Episode
import com.yuval.podcasts.data.network.PodcastApi
import com.yuval.podcasts.data.network.RssParser
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class AddLocalFileIntegrationTest {

    private lateinit var context: Context
    private lateinit var localMediaDataSource: LocalMediaDataSource

    @Before
    fun setup() {
        context = mockk(relaxed = true)

        val filesDir = File(System.getProperty("java.io.tmpdir"), "testFilesDir")
        filesDir.mkdirs()
        every { context.filesDir } returns filesDir

        localMediaDataSource = LocalMediaDataSource(context, Dispatchers.Unconfined)
    }

    @Test
    fun `addLocalFile extracts metadata and inserts to DB`() = runTest {
        mockkConstructor(MediaMetadataRetriever::class)
        every { anyConstructed<MediaMetadataRetriever>().setDataSource(any<String>()) } returns Unit
        every { anyConstructed<MediaMetadataRetriever>().extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE) } returns "Test Title"
        every { anyConstructed<MediaMetadataRetriever>().extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST) } returns "Test Artist"
        every { anyConstructed<MediaMetadataRetriever>().extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION) } returns "120000"
        every { anyConstructed<MediaMetadataRetriever>().release() } returns Unit

        val uri = mockk<Uri>()
        every { uri.scheme } returns "content"
        val contentResolver = mockk<ContentResolver>(relaxed = true)
        every { context.contentResolver } returns contentResolver
        
        val cursor = mockk<Cursor>(relaxed = true)
        every { contentResolver.query(uri, null, null, null, null) } returns cursor
        every { cursor.moveToFirst() } returns true
        every { cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME) } returns 0
        every { cursor.getString(0) } returns "my_audio_file.mp3"
        
        val inputStream = "dummy audio data".byteInputStream()
        every { contentResolver.openInputStream(uri) } returns inputStream

        val result = localMediaDataSource.copyAndExtract(uri)
        assertTrue(result.isSuccess)
        
        val metadata = result.getOrThrow()
        assertEquals("Test Title", metadata.title)
        assertTrue(metadata.description.startsWith("Test Artist"))
        assertEquals(120L, metadata.durationSecs)
    }

    @Test
    fun `addLocalFile falls back to cleaned filename if title is missing`() = runTest {
        mockkConstructor(MediaMetadataRetriever::class)
        every { anyConstructed<MediaMetadataRetriever>().setDataSource(any<String>()) } returns Unit
        every { anyConstructed<MediaMetadataRetriever>().extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE) } returns null
        every { anyConstructed<MediaMetadataRetriever>().extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST) } returns null
        every { anyConstructed<MediaMetadataRetriever>().extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST) } returns null
        every { anyConstructed<MediaMetadataRetriever>().extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION) } returns null
        every { anyConstructed<MediaMetadataRetriever>().release() } returns Unit

        val uri = mockk<Uri>()
        every { uri.scheme } returns "content"
        val contentResolver = mockk<ContentResolver>(relaxed = true)
        every { context.contentResolver } returns contentResolver
        
        val cursor = mockk<Cursor>(relaxed = true)
        every { contentResolver.query(uri, null, null, null, null) } returns cursor
        every { cursor.moveToFirst() } returns true
        every { cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME) } returns 0
        every { cursor.getString(0) } returns "my_awesome_recording_2023.mp3"
        
        val inputStream = "dummy audio data".byteInputStream()
        every { contentResolver.openInputStream(uri) } returns inputStream
        
        val result = localMediaDataSource.copyAndExtract(uri)
        assertTrue(result.isSuccess)
        
        val metadata = result.getOrThrow()
        // It replaces underscores with spaces
        assertEquals("my awesome recording 2023", metadata.title)
        assertTrue(metadata.description.startsWith("Unknown Artist"))
        assertEquals(0L, metadata.durationSecs)
    }
}
