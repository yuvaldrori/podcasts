package com.yuval.podcasts.ui.viewmodel

import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.flow.collect

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.yuval.podcasts.data.repository.PodcastRepository
import com.yuval.podcasts.utils.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.verify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.work.WorkInfo
import androidx.work.WorkManager
import java.io.InputStream
import java.io.OutputStream

class SettingsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val instantTaskExecutorRule = androidx.arch.core.executor.testing.InstantTaskExecutorRule()

    private lateinit var repository: PodcastRepository
    private lateinit var workManager: WorkManager
    private lateinit var context: Context
    private lateinit var contentResolver: ContentResolver
    private lateinit var uri: Uri
    private lateinit var inputStream: InputStream
    private lateinit var outputStream: OutputStream
    private lateinit var exportOpmlUseCase: com.yuval.podcasts.domain.usecase.ExportOpmlUseCase
    private lateinit var viewModel: SettingsViewModel

    @Before
    fun setup() {
        repository = mockk()
        workManager = mockk(relaxed = true)
        exportOpmlUseCase = mockk()
        context = mockk()
        contentResolver = mockk()
        uri = mockk(relaxed = true)
        inputStream = mockk()
        outputStream = mockk()

        val liveData = MutableLiveData<List<WorkInfo>>(emptyList())
        every { workManager.getWorkInfosForUniqueWorkLiveData(any()) } returns liveData
        every { context.contentResolver } returns contentResolver
        every { uri.toString() } returns "content://test.opml"
        
        viewModel = SettingsViewModel(repository, workManager, exportOpmlUseCase)
    }

    @Test
    fun addPodcast_success() = runTest {
        val url = "http://example.com/feed"
        coEvery { repository.fetchAndStorePodcast(url) } returns Unit

        viewModel.addPodcast(url)

        coVerify { repository.fetchAndStorePodcast(url) }
        assertNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun addPodcast_failure_setsErrorMessage() = runTest {
        val url = "http://example.com/feed"
        val errorMessage = "Invalid URL"
        coEvery { repository.fetchAndStorePodcast(url) } throws Exception(errorMessage)
        val job = backgroundScope.launch { viewModel.uiState.collect {} }

        viewModel.addPodcast(url)
        advanceUntilIdle()

        assertEquals("Failed to add podcast: $errorMessage", viewModel.uiState.value.errorMessage)
        job.cancel()
    }

    @Test
    fun importOpml_enqueuesWorkManagerTask() = runTest {
        viewModel.importOpml(uri)
        verify { workManager.enqueueUniqueWork("opml_import", androidx.work.ExistingWorkPolicy.REPLACE, any<androidx.work.OneTimeWorkRequest>()) }
    }

    @Test
    fun importLocalAudio_success() = runTest {
        coEvery { repository.addLocalFile(uri) } returns Result.success(Unit)

        viewModel.importLocalAudio(uri)

        coVerify { repository.addLocalFile(uri) }
        assertNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun importLocalAudio_failure_setsErrorMessage() = runTest {
        val errorMsg = "Import failed"
        coEvery { repository.addLocalFile(uri) } returns Result.failure(Exception(errorMsg))
        val job = backgroundScope.launch { viewModel.uiState.collect {} }

        viewModel.importLocalAudio(uri)
        advanceUntilIdle()

        coVerify { repository.addLocalFile(uri) }
        assertEquals("Failed to import local file: $errorMsg", viewModel.uiState.value.errorMessage)
        job.cancel()
    }

    @Test
    fun exportOpml_success() = runTest {
        every { contentResolver.openOutputStream(uri) } returns outputStream
        every { outputStream.close() } returns Unit
        coEvery { exportOpmlUseCase(outputStream) } returns Unit
        val job = backgroundScope.launch { viewModel.uiState.collect {} }

        viewModel.exportOpml(context, uri)
        advanceUntilIdle()

        coVerify { exportOpmlUseCase(outputStream) }
        assertNull(viewModel.uiState.value.errorMessage)
        job.cancel()
    }

    @Test
    fun exportHistory_success() = runTest {
        coEvery { repository.exportHistory(context, uri) } returns Result.success(Unit)
        val job = backgroundScope.launch { viewModel.uiState.collect {} }

        viewModel.exportHistory(context, uri)
        advanceUntilIdle()

        coVerify { repository.exportHistory(context, uri) }
        assertNull(viewModel.uiState.value.errorMessage)
        job.cancel()
    }

    @Test
    fun exportHistory_failure_setsErrorMessage() = runTest {
        val errorMsg = "Export failed"
        coEvery { repository.exportHistory(context, uri) } returns Result.failure(Exception(errorMsg))
        val job = backgroundScope.launch { viewModel.uiState.collect {} }

        viewModel.exportHistory(context, uri)
        advanceUntilIdle()

        assertEquals("Failed to export history: $errorMsg", viewModel.uiState.value.errorMessage)
        job.cancel()
    }

    @Test
    fun importHistory_success() = runTest {
        coEvery { repository.importHistory(uri) } returns Result.success(Unit)
        val job = backgroundScope.launch { viewModel.uiState.collect {} }

        viewModel.importHistory(uri)
        advanceUntilIdle()

        coVerify { repository.importHistory(uri) }
        assertNull(viewModel.uiState.value.errorMessage)
        job.cancel()
    }

    @Test
    fun importHistory_failure_setsErrorMessage() = runTest {
        val errorMsg = "Import failed"
        coEvery { repository.importHistory(uri) } returns Result.failure(Exception(errorMsg))
        val job = backgroundScope.launch { viewModel.uiState.collect {} }

        viewModel.importHistory(uri)
        advanceUntilIdle()

        assertEquals("Failed to import history: $errorMsg", viewModel.uiState.value.errorMessage)
        job.cancel()
    }

    @Test
    fun clearError_resetsErrorMessage() = runTest {
        val url = "http://example.com/feed"
        coEvery { repository.fetchAndStorePodcast(url) } throws Exception("Error")
        val job = backgroundScope.launch { viewModel.uiState.collect {} }

        viewModel.addPodcast(url)
        advanceUntilIdle()
        assertEquals("Failed to add podcast: Error", viewModel.uiState.value.errorMessage)

        viewModel.clearError()
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.errorMessage)
        job.cancel()
    }
}
