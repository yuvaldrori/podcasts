package com.yuval.podcasts.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yuval.podcasts.data.repository.PodcastRepository
import com.yuval.podcasts.domain.usecase.ExportOpmlUseCase
import com.yuval.podcasts.domain.usecase.ImportOpmlUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

import android.content.Context
import android.net.Uri

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: PodcastRepository,
    private val importOpmlUseCase: ImportOpmlUseCase,
    private val exportOpmlUseCase: ExportOpmlUseCase
) : ViewModel() {

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

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

    fun importOpml(context: Context, uri: Uri) {
        viewModelScope.launch {
            try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    importOpmlUseCase(stream)
                }
            } catch (e: Exception) {
                        if (e is kotlinx.coroutines.CancellationException) throw e
                _errorMessage.value = "Failed to import OPML: ${e.message}"
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

    fun clearError() {
        _errorMessage.value = null
    }
}
