package com.yuval.podcasts.ui.viewmodel

import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.flow.collect

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.yuval.podcasts.R
import com.yuval.podcasts.data.Constants
import com.yuval.podcasts.data.repository.PodcastRepository
import com.yuval.podcasts.utils.MainDispatcherRule
import com.yuval.podcasts.ui.utils.UiText
import io.mockk.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.yuval.podcasts.data.repository.SettingsRepository
import com.yuval.podcasts.data.opml.OpmlManager
import com.yuval.podcasts.domain.usecase.ImportLocalFileUseCase
import kotlinx.coroutines.flow.flowOf
import java.io.InputStream
import java.io.OutputStream

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val instantTaskExecutorRule = androidx.arch.core.executor.testing.InstantTaskExecutorRule()

    private lateinit var repository: PodcastRepository
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var workManager: WorkManager
    private lateinit var context: Context
    private lateinit var contentResolver: ContentResolver
    private lateinit var uri: Uri
    private lateinit var inputStream: InputStream
    private lateinit var outputStream: OutputStream
    private lateinit var opmlManager: OpmlManager
    private lateinit var importLocalFileUseCase: ImportLocalFileUseCase
    private lateinit var logManager: com.yuval.podcasts.utils.LogManager
    private lateinit var viewModel: SettingsViewModel

    @Before
    fun setup() {
        repository = mockk()
        settingsRepository = mockk(relaxed = true)
        workManager = mockk(relaxed = true)
        opmlManager = mockk()
        importLocalFileUseCase = mockk()
        logManager = mockk(relaxed = true)
        context = mockk()
        contentResolver = mockk(relaxed = true)
        uri = mockk(relaxed = true)
        inputStream = mockk()
        outputStream = mockk()

        val liveData = MutableLiveData<List<WorkInfo>>(emptyList())
        every { workManager.getWorkInfosForUniqueWorkLiveData(any()) } returns liveData
        every { context.contentResolver } returns contentResolver
        every { uri.toString() } returns "content://test.opml"
        

        
        viewModel = SettingsViewModel(
            context = context,
            repository = repository,
            workManager = workManager,
            opmlManager = opmlManager,
            importLocalFileUseCase = importLocalFileUseCase,
            logManager = logManager,
            ioDispatcher = mainDispatcherRule.testDispatcher
        )
    }

    @Test
    fun addPodcast_success() = runTest {
        val url = "http://example.com/feed"
        coEvery { repository.fetchAndStorePodcast(url) } returns 1
        val job = backgroundScope.launch { viewModel.uiState.collect {} }

        viewModel.addPodcast(url)
        advanceUntilIdle()

        coVerify { repository.fetchAndStorePodcast(url) }
        assertNull(viewModel.uiState.value.errorMessage)
        job.cancel()
    }

    @Test
    fun addPodcast_failure_setsErrorMessage() = runTest {
        val url = "http://example.com/feed"
        coEvery { repository.fetchAndStorePodcast(url) } throws Exception("Invalid URL")

        val job = backgroundScope.launch { viewModel.uiState.collect {} }

        viewModel.addPodcast(url)
        advanceUntilIdle()

        val error = viewModel.uiState.value.errorMessage as? UiText.StringResource
        assertTrue(error != null)
        assertEquals(R.string.error_add_podcast, error!!.resId)
        assertEquals(listOf("Invalid URL"), error.args.toList())
        job.cancel()
    }

    @Test
    fun importOpml_enqueuesWorkManagerTask() = runTest {
        viewModel.importOpml(uri)
        verify { workManager.enqueueUniqueWork(Constants.WORK_NAME_OPML_IMPORT, androidx.work.ExistingWorkPolicy.REPLACE, any<androidx.work.OneTimeWorkRequest>()) }
    }

    @Test
    fun importLocalAudio_success() = runTest {
        coEvery { importLocalFileUseCase(uri) } returns Result.success(Unit)
        val job = backgroundScope.launch { viewModel.uiState.collect {} }

        viewModel.importLocalAudio(uri)
        advanceUntilIdle()

        coVerify { importLocalFileUseCase(uri) }
        assertNull(viewModel.uiState.value.errorMessage)
        job.cancel()
    }

    @Test
    fun importLocalAudio_failure_setsErrorMessage() = runTest {
        coEvery { importLocalFileUseCase(uri) } returns Result.failure(Exception("Import failed"))

        val job = backgroundScope.launch { viewModel.uiState.collect {} }

        viewModel.importLocalAudio(uri)
        advanceUntilIdle()

        val error = viewModel.uiState.value.errorMessage as? UiText.StringResource
        assertTrue(error != null)
        assertEquals(R.string.error_import_local, error!!.resId)
        assertEquals(listOf("Import failed"), error.args.toList())
        job.cancel()
    }

    @Test
    fun exportOpml_success() = runTest {
        every { repository.allPodcasts } returns flowOf(emptyList())
        every { contentResolver.openOutputStream(uri) } returns outputStream
        every { outputStream.close() } returns Unit
        every { opmlManager.export(any(), outputStream) } returns Unit
        val job = backgroundScope.launch { viewModel.uiState.collect {} }

        viewModel.exportOpml(uri)
        advanceUntilIdle()

        verify { opmlManager.export(any(), outputStream) }
        assertNull(viewModel.uiState.value.errorMessage)
        job.cancel()
    }

    @Test
    fun exportOpml_filtersOutLocalPseudoSubscription() = runTest {
        val remotePodcast = com.yuval.podcasts.data.db.entity.Podcast("https://example.com/feed.xml", "Remote", "Desc", "Img", "Web")
        val localPodcast = com.yuval.podcasts.data.db.entity.Podcast(Constants.LOCAL_PODCAST_FEED_URL, "Local Files", "Desc", "Img", "Web")
        every { repository.allPodcasts } returns flowOf(listOf(remotePodcast, localPodcast))
        every { contentResolver.openOutputStream(uri) } returns outputStream
        every { outputStream.close() } returns Unit
        every { opmlManager.export(any(), outputStream) } returns Unit
        val job = backgroundScope.launch { viewModel.uiState.collect {} }

        viewModel.exportOpml(uri)
        advanceUntilIdle()

        verify { opmlManager.export(listOf(remotePodcast), outputStream) }
        assertNull(viewModel.uiState.value.errorMessage)
        job.cancel()
    }

    @Test
    fun clearMessages_resetsErrorMessage() = runTest {
        val url = "http://example.com/feed"
        coEvery { repository.fetchAndStorePodcast(url) } throws Exception("Error")
        
        val job = backgroundScope.launch { viewModel.uiState.collect {} }

        viewModel.addPodcast(url)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.errorMessage != null)

        viewModel.clearMessages()
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.errorMessage)
        job.cancel()
    }

    @Test
    fun exportLogs_failure_logsErrorToLogManager() = runTest {
        every { contentResolver.openOutputStream(uri) } throws Exception("Disk full")
        val job = backgroundScope.launch { viewModel.uiState.collect {} }

        viewModel.exportLogs(uri)
        advanceUntilIdle()

        verify { logManager.e("SettingsViewModel", "Failed to export logs", any()) }
        val error = viewModel.uiState.value.errorMessage as? UiText.StringResource
        assertTrue(error != null)
        assertEquals(R.string.error_export_failed, error!!.resId)
        job.cancel()
    }

    @Test
    fun exportOpml_failure_logsErrorToLogManager() = runTest {
        every { contentResolver.openOutputStream(uri) } throws Exception("Disk full")
        val job = backgroundScope.launch { viewModel.uiState.collect {} }

        viewModel.exportOpml(uri)
        advanceUntilIdle()

        verify { logManager.e("SettingsViewModel", "Failed to export OPML", any()) }
        val error = viewModel.uiState.value.errorMessage as? UiText.StringResource
        assertTrue(error != null)
        assertEquals(R.string.error_export_opml, error!!.resId)
        assertEquals(listOf("Disk full"), error.args.toList())
        job.cancel()
    }
}
