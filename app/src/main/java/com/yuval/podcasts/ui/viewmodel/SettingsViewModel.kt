package com.yuval.podcasts.ui.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asFlow
import androidx.lifecycle.viewModelScope
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.yuval.podcasts.R
import com.yuval.podcasts.data.Constants
import com.yuval.podcasts.data.repository.PodcastRepository
import com.yuval.podcasts.data.repository.SettingsRepository
import com.yuval.podcasts.domain.usecase.ExportOpmlUseCase
import com.yuval.podcasts.utils.LogManager
import com.yuval.podcasts.work.OpmlImportWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val importWorkInfo: WorkInfo? = null,
    val errorMessage: String? = null,
    val skipSilenceEnabled: Boolean = false,
    val logNote: String = "",
    val successMessage: String? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: PodcastRepository,
    private val settingsRepository: SettingsRepository,
    private val workManager: WorkManager,
    private val exportOpmlUseCase: ExportOpmlUseCase,
    private val logManager: LogManager
) : ViewModel() {

    private val _errorMessage = MutableStateFlow<String?>(null)
    private val _successMessage = MutableStateFlow<String?>(null)
    private val _logNote = MutableStateFlow("")

    val uiState: StateFlow<SettingsUiState> = combine(
        workManager.getWorkInfosForUniqueWorkLiveData("opml_import").asFlow().map { it.firstOrNull() },
        _errorMessage,
        _successMessage,
        _logNote,
        settingsRepository.skipSilenceFlow
    ) { workInfo, error, success, note, skipSilence ->
        SettingsUiState(
            importWorkInfo = workInfo,
            errorMessage = error,
            successMessage = success,
            logNote = note,
            skipSilenceEnabled = skipSilence
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(Constants.FLOW_STOP_TIMEOUT_MS),
        initialValue = SettingsUiState()
    )

    fun onLogNoteChanged(note: String) {
        _logNote.value = note
    }

    fun saveLogNote() {
        val note = _logNote.value
        if (note.isNotBlank()) {
            logManager.i("USER_NOTE", note)
            _logNote.value = ""
        }
    }

    fun downloadLogs() {
        val result = logManager.exportLogs()
        if (result.isSuccess) {
            _successMessage.value = repository.getString(R.string.logs_downloaded_success, result.getOrThrow().name)
        } else {
            _errorMessage.value = result.exceptionOrNull()?.message ?: "Export failed"
        }
    }

    fun addPodcast(url: String) {
        viewModelScope.launch {
            try {
                repository.fetchAndStorePodcast(url)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _errorMessage.value = repository.getString(R.string.error_add_podcast, e.message ?: "")
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
                _errorMessage.value = repository.getString(R.string.error_import_local, result.exceptionOrNull()?.message ?: "")
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
                android.util.Log.e("SettingsViewModel", "Failed to export OPML", e)
                _errorMessage.value = repository.getString(R.string.error_export_opml, e.message ?: "")
            }
        }
    }

    fun toggleSkipSilence(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.saveSkipSilenceEnabled(enabled)
        }
    }

    fun clearError() {
        _errorMessage.value = null
        _successMessage.value = null
    }
}
