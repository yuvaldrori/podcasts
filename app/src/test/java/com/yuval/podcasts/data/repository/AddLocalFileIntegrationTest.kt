package com.yuval.podcasts.data.repository

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.yuval.podcasts.data.Constants
import com.yuval.podcasts.data.db.AppDatabase
import com.yuval.podcasts.data.db.entity.DownloadStatus
import com.yuval.podcasts.domain.usecase.ImportLocalFileUseCase
import com.yuval.podcasts.utils.LogManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.spyk
import io.mockk.unmockkConstructor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class AddLocalFileIntegrationTest {

    @get:Rule
    val tmpFolder = TemporaryFolder()

    private lateinit var appContext: Context
    private lateinit var spiedContext: Context
    private lateinit var contentResolver: ContentResolver
    private lateinit var database: AppDatabase
    private lateinit var repository: PodcastRepository
    private lateinit var localMediaDataSource: LocalMediaDataSource
    private lateinit var importLocalFileUseCase: ImportLocalFileUseCase
    private lateinit var filesDir: File

    @Before
    fun setup() {
        appContext = ApplicationProvider.getApplicationContext()
        filesDir = tmpFolder.newFolder("files")
        contentResolver = mockk(relaxed = true)
        spiedContext = spyk(appContext)
        every { spiedContext.filesDir } returns filesDir
        every { spiedContext.contentResolver } returns contentResolver
        every { spiedContext.getString(any()) } returns "Local Files"

        database = Room.inMemoryDatabaseBuilder(appContext, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        val logManager = mockk<LogManager>(relaxed = true)
        repository = DefaultPodcastRepository(
            database = database,
            podcastApi = mockk(relaxed = true),
            rssParser = mockk(relaxed = true),
            podcastDao = database.podcastDao(),
            episodeDao = database.episodeDao(),
            queueDao = database.queueDao(),
            chapterDao = database.chapterDao(),
            workManager = mockk(relaxed = true),
            downloadProgressTracker = DownloadProgressTracker(),
            ioDispatcher = Dispatchers.Unconfined,
            logManager = logManager
        )
        localMediaDataSource = LocalMediaDataSource(spiedContext, Dispatchers.Unconfined, logManager)
        importLocalFileUseCase = ImportLocalFileUseCase(spiedContext, repository, localMediaDataSource, logManager)
    }

    @After
    fun teardown() {
        unmockkConstructor(MediaMetadataRetriever::class)
        database.close()
    }

    private fun stubUri(fileName: String, content: String = "dummy audio data"): Uri {
        val uri = mockk<Uri>()
        every { uri.scheme } returns "content"
        val cursor = mockk<Cursor>(relaxed = true)
        every { contentResolver.query(uri, null, null, null, null) } returns cursor
        every { cursor.moveToFirst() } returns true
        every { cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME) } returns 0
        every { cursor.getString(0) } returns fileName
        every { contentResolver.openInputStream(uri) } returns content.byteInputStream()
        return uri
    }

    @Test
    fun `addLocalFile extracts metadata copies file and inserts to DB`() = runTest {
        mockkConstructor(MediaMetadataRetriever::class)
        every { anyConstructed<MediaMetadataRetriever>().setDataSource(any<String>()) } returns Unit
        every { anyConstructed<MediaMetadataRetriever>().extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE) } returns "Test Title"
        every { anyConstructed<MediaMetadataRetriever>().extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST) } returns "Test Artist"
        every { anyConstructed<MediaMetadataRetriever>().extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION) } returns "120000"
        every { anyConstructed<MediaMetadataRetriever>().release() } returns Unit

        val uri = stubUri("my_audio_file.mp3", "dummy audio data")

        val result = importLocalFileUseCase(uri)
        assertTrue("Import should succeed: $result", result.isSuccess)

        val localPodcast = database.podcastDao().getPodcast(Constants.LOCAL_PODCAST_FEED_URL)
        assertNotNull("Local pseudo-podcast must be created in DB", localPodcast)

        val episodes = database.episodeDao().getEpisodesForPodcast(Constants.LOCAL_PODCAST_FEED_URL).first()
        assertEquals(1, episodes.size)
        val savedEpisode = episodes[0]
        assertEquals("Test Title", savedEpisode.title)
        assertTrue(savedEpisode.description.startsWith("Test Artist"))
        assertEquals(120L, savedEpisode.duration)
        assertEquals(DownloadStatus.DOWNLOADED.value, savedEpisode.downloadStatus)

        val copiedFile = File(requireNotNull(savedEpisode.localFilePath))
        assertTrue("Copied file must exist in local_podcasts", copiedFile.exists())
        assertEquals(File(filesDir, Constants.LOCAL_UPLOAD_DIR_NAME).canonicalPath, copiedFile.parentFile?.canonicalPath)
        assertEquals("dummy audio data", copiedFile.readText())
    }

    @Test
    fun `addLocalFile falls back to cleaned filename and inserts to DB when metadata is missing`() = runTest {
        mockkConstructor(MediaMetadataRetriever::class)
        every { anyConstructed<MediaMetadataRetriever>().setDataSource(any<String>()) } returns Unit
        every { anyConstructed<MediaMetadataRetriever>().extractMetadata(any()) } returns null
        every { anyConstructed<MediaMetadataRetriever>().release() } returns Unit

        val uri = stubUri("my_awesome_recording_2023.mp3", "raw-recording")

        val result = importLocalFileUseCase(uri)
        assertTrue("Import should succeed: $result", result.isSuccess)

        val episodes = database.episodeDao().getEpisodesForPodcast(Constants.LOCAL_PODCAST_FEED_URL).first()
        assertEquals(1, episodes.size)
        val savedEpisode = episodes[0]
        assertEquals("my awesome recording 2023", savedEpisode.title)
        assertTrue(savedEpisode.description.startsWith("Unknown Artist"))
        assertEquals(0L, savedEpisode.duration)
        assertEquals("raw-recording", File(requireNotNull(savedEpisode.localFilePath)).readText())
    }
}
