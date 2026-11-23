package com.yuval.podcasts.ui.viewmodel

import android.content.Context
import android.content.Intent
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
import com.yuval.podcasts.data.network.AppUpdateManager
import com.yuval.podcasts.data.network.ReleaseInfo
import com.yuval.podcasts.data.network.UpdateCheckResult
import com.yuval.podcasts.data.opml.OpmlManager
import com.yuval.podcasts.data.repository.PodcastRepository
import com.yuval.podcasts.di.IoDispatcher
import com.yuval.podcasts.domain.usecase.ImportLocalFileUseCase
import com.yuval.podcasts.ui.utils.UiText
import com.yuval.podcasts.utils.LogManager
import com.yuval.podcasts.work.OpmlImportWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

sealed interface AppUpdateState {
    data object Idle : AppUpdateState
    data object Checking : AppUpdateState
    data object UpToDate : AppUpdateState
    data class UpdateAvailable(val release: ReleaseInfo) : AppUpdateState
    data class Downloading(val release: ReleaseInfo, val progress: Float) : AppUpdateState
    data class ReadyToInstall(val release: ReleaseInfo, val apkFile: File) : AppUpdateState
}

data class SettingsUiState(
    val importWorkInfo: WorkInfo? = null,
    val errorMessage: UiText? = null,
    val logNote: String = "",
    val successMessage: UiText? = null,
    val appUpdateState: AppUpdateState = AppUpdateState.Idle,
    val installApkTrigger: File? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val repository: PodcastRepository,
    private val workManager: WorkManager,
    private val opmlManager: OpmlManager,
    private val importLocalFileUseCase: ImportLocalFileUseCase,
    private val appUpdateManager: AppUpdateManager,
    private val logManager: LogManager,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : ViewModel() {

    private val _errorMessage = MutableStateFlow<UiText?>(null)
    private val _successMessage = MutableStateFlow<UiText?>(null)
    private val _logNote = MutableStateFlow("")
    private val _appUpdateState = MutableStateFlow<AppUpdateState>(AppUpdateState.Idle)
    private val _installApkTrigger = MutableStateFlow<File?>(null)
    private var updateDownloadJob: Job? = null

    val uiState: StateFlow<SettingsUiState> = combine(
        workManager.getWorkInfosForUniqueWorkLiveData(Constants.WORK_NAME_OPML_IMPORT).asFlow().map { it.firstOrNull() },
        _errorMessage,
        _successMessage,
        _logNote,
        combine(_appUpdateState, _installApkTrigger, ::Pair)
    ) { importWorkInfo, errorMsg, successMsg, note, (updateState, installApk) ->
        SettingsUiState(
            importWorkInfo = importWorkInfo,
            errorMessage = errorMsg,
            successMessage = successMsg,
            logNote = note,
            appUpdateState = updateState,
            installApkTrigger = installApk
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
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logManager.e("SettingsViewModel", "Failed to export logs", mapOf("error" to (e.message ?: "")))
                _errorMessage.update { UiText.StringResource(R.string.error_export_failed) }
            }
        }
    }

    fun addPodcast(url: String) {
        viewModelScope.launch {
            try {
                repository.fetchAndStorePodcast(url)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _errorMessage.update { UiText.StringResource(R.string.error_add_podcast, e.message ?: "") }
            }
        }
    }

    fun importOpml(uri: Uri) {
        try {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
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
                _errorMessage.update { UiText.StringResource(R.string.error_import_local, result.exceptionOrNull()?.message ?: "") }
            }
        }
    }

    fun exportOpml(uri: Uri) {
        viewModelScope.launch {
            try {
                withContext(ioDispatcher) {
                    context.contentResolver.openOutputStream(uri)?.use { stream ->
                        val podcasts = repository.allPodcasts.first().filter { it.feedUrl != Constants.LOCAL_PODCAST_FEED_URL }
                        opmlManager.export(podcasts, stream)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logManager.e("SettingsViewModel", "Failed to export OPML", mapOf("error" to (e.message ?: "")))
                _errorMessage.update { UiText.StringResource(R.string.error_export_opml, e.message ?: "") }
            }
        }
    }

    fun checkForUpdates() {
        val current = _appUpdateState.value
        if (current is AppUpdateState.Checking || current is AppUpdateState.Downloading) return

        viewModelScope.launch {
            _appUpdateState.value = AppUpdateState.Checking
            when (val result = appUpdateManager.checkForUpdate()) {
                is UpdateCheckResult.UpdateAvailable -> {
                    _appUpdateState.value = AppUpdateState.UpdateAvailable(result.release)
                }
                is UpdateCheckResult.UpToDate -> {
                    _appUpdateState.value = AppUpdateState.UpToDate
                }
                is UpdateCheckResult.Error -> {
                    _appUpdateState.value = AppUpdateState.Idle
                    _errorMessage.update { UiText.StringResource(R.string.error_check_updates, result.message) }
                }
            }
        }
    }

    fun downloadUpdate() {
        val release = when (val current = _appUpdateState.value) {
            is AppUpdateState.UpdateAvailable -> current.release
            is AppUpdateState.ReadyToInstall -> current.release
            else -> return
        }

        updateDownloadJob?.cancel()
        updateDownloadJob = viewModelScope.launch {
            _appUpdateState.value = AppUpdateState.Downloading(release, 0f)
            try {
                val apkFile = appUpdateManager.downloadApk(release) { progress ->
                    _appUpdateState.update { state ->
                        if (state is AppUpdateState.Downloading) state.copy(progress = progress) else state
                    }
                }
                _appUpdateState.value = AppUpdateState.ReadyToInstall(release, apkFile)
                _installApkTrigger.value = apkFile
            } catch (e: CancellationException) {
                _appUpdateState.value = AppUpdateState.UpdateAvailable(release)
                throw e
            } catch (e: Exception) {
                logManager.e("SettingsViewModel", "Failed to download update", mapOf("error" to (e.message ?: "")))
                _appUpdateState.value = AppUpdateState.UpdateAvailable(release)
                _errorMessage.update { UiText.StringResource(R.string.error_download_update, e.message ?: "") }
            }
        }
    }

    fun cancelUpdateDownload() {
        updateDownloadJob?.cancel()
        updateDownloadJob = null
    }

    fun triggerInstall() {
        val ready = _appUpdateState.value as? AppUpdateState.ReadyToInstall ?: return
        _installApkTrigger.value = ready.apkFile
    }

    fun onInstallLaunched() {
        _installApkTrigger.value = null
    }

    fun clearMessages() {
        _errorMessage.update { null }
        _successMessage.update { null }
    }
}
