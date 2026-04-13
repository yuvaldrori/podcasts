package com.yuval.podcasts.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yuval.podcasts.data.Constants
import com.yuval.podcasts.data.repository.PodcastRepository
import com.yuval.podcasts.data.repository.SettingsRepository
import com.yuval.podcasts.domain.usecase.ExportOpmlUseCase
import androidx.lifecycle.asFlow
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.yuval.podcasts.work.OpmlImportWorker
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.flowOf

import android.content.Context
import android.net.Uri
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

import kotlinx.coroutines.flow.combine

data class SettingsUiState(
    val importWorkInfo: WorkInfo? = null,
    val errorMessage: String? = null,
    val skipSilenceEnabled: Boolean = false
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: PodcastRepository,
    private val settingsRepository: SettingsRepository,
    private val workManager: WorkManager,
    private val exportOpmlUseCase: ExportOpmlUseCase
) : ViewModel() {

    private val _errorMessage = MutableStateFlow<String?>(null)
    private val _skipSilenceTrigger = MutableStateFlow(System.currentTimeMillis())

    val uiState: StateFlow<SettingsUiState> = combine(
        workManager.getWorkInfosForUniqueWorkLiveData("opml_import").asFlow().map { it.firstOrNull() },
        _errorMessage,
        _skipSilenceTrigger
    ) { workInfo, error, _ ->
        SettingsUiState(
            importWorkInfo = workInfo,
            errorMessage = error,
            skipSilenceEnabled = settingsRepository.isSkipSilenceEnabled()
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(Constants.FLOW_STOP_TIMEOUT_MS),
        initialValue = SettingsUiState()
    )

    fun addPodcast(url: String) {
        viewModelScope.launch {
            try {
                repository.fetchAndStorePodcast(url)
            } catch (e: Exception) {
                        if (e is kotlinx.coroutines.CancellationException) throw e
                _errorMessage.value = "Failed to add podcast: ${e.message}"
            }
        }
    }

    fun importOpml(uri: Uri) {
        val inputData = Data.Builder()
            .putString(OpmlImportWorker.KEY_URI, uri.toString())
            .build()

        val request = OneTimeWorkRequestBuilder<OpmlImportWorker>()
            .setInputData(inputData)
            .build()

        workManager.enqueueUniqueWork("opml_import", ExistingWorkPolicy.REPLACE, request)
    }

    fun importLocalAudio(uri: Uri) {
        viewModelScope.launch {
            val result = repository.addLocalFile(uri)
            if (result.isFailure) {
                _errorMessage.value = "Failed to import local file: ${result.exceptionOrNull()?.message}"
            }
        }
    }

    fun exportOpml(context: Context, uri: Uri) {
        viewModelScope.launch {
            try {
                context.contentResolver.openOutputStream(uri)?.use { stream ->
                    exportOpmlUseCase(stream)
                }
            } catch (e: Exception) {
                        if (e is kotlinx.coroutines.CancellationException) throw e
                e.printStackTrace()
                _errorMessage.value = "Failed to export OPML: ${e.message}"
            }
        }
    }

    fun exportHistory(context: Context, uri: Uri) {
        viewModelScope.launch {
            val result = repository.exportHistory(context, uri)
            if (result.isFailure) {
                _errorMessage.value = "Failed to export history: ${result.exceptionOrNull()?.message}"
            }
        }
    }

    fun importHistory(uri: Uri) {
        viewModelScope.launch {
            val result = repository.importHistory(uri)
            if (result.isFailure) {
                _errorMessage.value = "Failed to import history: ${result.exceptionOrNull()?.message}"
            }
        }
    }

    fun toggleSkipSilence(enabled: Boolean) {
        settingsRepository.saveSkipSilenceEnabled(enabled)
        _skipSilenceTrigger.value = System.currentTimeMillis()
    }

    fun clearError() {
        _errorMessage.value = null
    }
}
