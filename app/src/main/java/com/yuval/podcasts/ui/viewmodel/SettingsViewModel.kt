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
import com.yuval.podcasts.domain.usecase.ImportLocalFileUseCase
import com.yuval.podcasts.ui.utils.UiText
import com.yuval.podcasts.utils.LogManager
import com.yuval.podcasts.work.OpmlImportWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.yuval.podcasts.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import javax.inject.Inject

data class SettingsUiState(
    val importWorkInfo: WorkInfo? = null,
    val errorMessage: UiText? = null,
    val logNote: String = "",
    val successMessage: UiText? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val repository: PodcastRepository,
    private val workManager: WorkManager,
    private val exportOpmlUseCase: ExportOpmlUseCase,
    private val importLocalFileUseCase: ImportLocalFileUseCase,
    private val logManager: LogManager,
    private val messageDelegate: MessageDelegate,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : ViewModel(), MessageDelegate by messageDelegate {

    private val _successMessage = MutableStateFlow<UiText?>(null)
    private val _logNote = MutableStateFlow("")

    val uiState: StateFlow<SettingsUiState> = combine(
        workManager.getWorkInfosForUniqueWorkLiveData(Constants.WORK_NAME_OPML_IMPORT).asFlow().map { it.firstOrNull() },
        errorMessage,
        _successMessage,
        _logNote
    ) { importWorkInfo, errorMsg, successMsg, note ->
        SettingsUiState(
            importWorkInfo = importWorkInfo,
            errorMessage = errorMsg,
            successMessage = successMsg,
            logNote = note
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(Constants.FLOW_STOP_TIMEOUT_MS),
        initialValue = SettingsUiState()
    )

    fun onLogNoteChanged(note: String) {
        _logNote.update { note }
    }

    fun saveLogNote() {
        val note = _logNote.value
        if (note.isNotBlank()) {
            logManager.i("USER_NOTE", note)
            _logNote.update { "" }
        }
    }

    fun exportLogs(uri: Uri) {
        viewModelScope.launch {
            try {
                withContext(ioDispatcher) {
                    context.contentResolver.openOutputStream(uri)?.use { stream ->
                        logManager.exportLogsToStream(stream)
                    }
                }
                _successMessage.update { UiText.StringResource(R.string.logs_downloaded_success, context.getString(R.string.default_logs_filename)) }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                android.util.Log.e("SettingsViewModel", "Failed to export logs", e)
                showError(UiText.StringResource(R.string.error_export_failed))
            }
        }
    }

    fun addPodcast(url: String) {
        viewModelScope.launch {
            try {
                repository.fetchAndStorePodcast(url)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                showError(UiText.StringResource(R.string.error_add_podcast, e.message ?: ""))
            }
        }
    }

    fun importOpml(uri: Uri) {
        try {
            val flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(uri, flags)
        } catch (e: SecurityException) {
            logManager.w("SettingsViewModel", "Failed to take persistable URI permission for OPML import", mapOf("error" to e.message.toString()))
        }

        val inputData = Data.Builder()
            .putString(OpmlImportWorker.KEY_URI, uri.toString())
            .build()

        val request = OneTimeWorkRequestBuilder<OpmlImportWorker>()
            .setInputData(inputData)
            .build()

        workManager.enqueueUniqueWork(Constants.WORK_NAME_OPML_IMPORT, ExistingWorkPolicy.REPLACE, request)
    }

    fun importLocalAudio(uri: Uri) {
        viewModelScope.launch {
            val result = importLocalFileUseCase(uri)
            if (result.isFailure) {
                showError(UiText.StringResource(R.string.error_import_local, result.exceptionOrNull()?.message ?: ""))
            }
        }
    }

    fun exportOpml(uri: Uri) {
        viewModelScope.launch {
            try {
                withContext(ioDispatcher) {
                    context.contentResolver.openOutputStream(uri)?.use { stream ->
                        exportOpmlUseCase(stream)
                    }
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                android.util.Log.e("SettingsViewModel", "Failed to export OPML", e)
                showError(UiText.StringResource(R.string.error_export_opml, e.message ?: ""))
            }
        }
    }



    fun clearMessages() {
        messageDelegate.clearError()
        _successMessage.update { null }
    }
}
