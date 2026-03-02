package com.yuval.podcasts.ui.viewmodel

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
        assertNull(viewModel.errorMessage.value)
    }

    @Test
    fun addPodcast_failure_setsErrorMessage() = runTest {
        val url = "http://example.com/feed"
        val errorMessage = "Invalid URL"
        coEvery { repository.fetchAndStorePodcast(url) } throws Exception(errorMessage)

        viewModel.addPodcast(url)

        assertEquals("Failed to add podcast: $errorMessage", viewModel.errorMessage.value)
    }

    @Test
    fun importOpml_enqueuesWorkManagerTask() = runTest {
        viewModel.importOpml(uri)
        verify { workManager.enqueueUniqueWork("opml_import", androidx.work.ExistingWorkPolicy.REPLACE, any<androidx.work.OneTimeWorkRequest>()) }
    }

    @Test
    fun exportOpml_success() = runTest {
        every { contentResolver.openOutputStream(uri) } returns outputStream
        every { outputStream.close() } returns Unit
        coEvery { exportOpmlUseCase(outputStream) } returns Unit

        viewModel.exportOpml(context, uri)

        coVerify { exportOpmlUseCase(outputStream) }
        assertNull(viewModel.errorMessage.value)
    }

    @Test
    fun clearError_resetsErrorMessage() = runTest {
        val url = "http://example.com/feed"
        coEvery { repository.fetchAndStorePodcast(url) } throws Exception("Error")

        viewModel.addPodcast(url)
        assertEquals("Failed to add podcast: Error", viewModel.errorMessage.value)

        viewModel.clearError()
        assertNull(viewModel.errorMessage.value)
    }
}
