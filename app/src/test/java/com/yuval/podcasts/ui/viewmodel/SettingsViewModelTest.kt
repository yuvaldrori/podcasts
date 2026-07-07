package com.yuval.podcasts.ui.viewmodel

import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.flow.collect

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
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
import com.yuval.podcasts.domain.usecase.ExportOpmlUseCase
import com.yuval.podcasts.domain.usecase.ImportLocalFileUseCase
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
    private lateinit var exportOpmlUseCase: ExportOpmlUseCase
    private lateinit var importLocalFileUseCase: ImportLocalFileUseCase
    private lateinit var logManager: com.yuval.podcasts.utils.LogManager
    private lateinit var viewModel: SettingsViewModel

    @Before
    fun setup() {
        repository = mockk()
        settingsRepository = mockk(relaxed = true)
        workManager = mockk(relaxed = true)
        exportOpmlUseCase = mockk()
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
            exportOpmlUseCase = exportOpmlUseCase,
            importLocalFileUseCase = importLocalFileUseCase,
            logManager = logManager,
            messageDelegate = DefaultMessageDelegate(),
            ioDispatcher = mainDispatcherRule.testDispatcher
        )
    }

    @Test
    fun addPodcast_success() = runTest {
        val url = "http://example.com/feed"
        coEvery { repository.fetchAndStorePodcast(url) } returns 1

        viewModel.addPodcast(url)

        coVerify { repository.fetchAndStorePodcast(url) }
        assertNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun addPodcast_failure_setsErrorMessage() = runTest {
        val url = "http://example.com/feed"
        coEvery { repository.fetchAndStorePodcast(url) } throws Exception("Invalid URL")
        
        val job = backgroundScope.launch { viewModel.uiState.collect {} }

        viewModel.addPodcast(url)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.errorMessage is UiText.StringResource)
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

        viewModel.importLocalAudio(uri)

        coVerify { importLocalFileUseCase(uri) }
        assertNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun importLocalAudio_failure_setsErrorMessage() = runTest {
        coEvery { importLocalFileUseCase(uri) } returns Result.failure(Exception("Import failed"))
        
        val job = backgroundScope.launch { viewModel.uiState.collect {} }

        viewModel.importLocalAudio(uri)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.errorMessage is UiText.StringResource)
        job.cancel()
    }

    @Test
    fun exportOpml_success() = runTest {
        every { contentResolver.openOutputStream(uri) } returns outputStream
        every { outputStream.close() } returns Unit
        coEvery { exportOpmlUseCase(outputStream) } returns Unit
        val job = backgroundScope.launch { viewModel.uiState.collect {} }

        viewModel.exportOpml(uri)
        advanceUntilIdle()

        coVerify { exportOpmlUseCase(outputStream) }
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


}
