package com.yuval.podcasts.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yuval.podcasts.data.repository.PodcastRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: PodcastRepository
) : ViewModel() {

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    fun addPodcast(url: String) {
        viewModelScope.launch {
            try {
                repository.fetchAndStorePodcast(url)
            } catch (e: Exception) {
                _errorMessage.value = "Failed to add podcast: ${e.message}"
            }
        }
    }

    fun importOpml(inputStream: InputStream) {
        viewModelScope.launch {
            try {
                repository.importOpml(inputStream)
            } catch (e: Exception) {
                _errorMessage.value = "Failed to import OPML: ${e.message}"
            }
        }
    }

    fun exportOpml(outputStream: OutputStream) {
        viewModelScope.launch {
            try {
                repository.exportOpml(outputStream)
            } catch (e: Exception) {
                _errorMessage.value = "Failed to export OPML: ${e.message}"
            }
        }
    }

    fun backupDatabase(outputStream: OutputStream) {
        viewModelScope.launch {
            try {
                repository.backupDatabase(outputStream)
            } catch (e: Exception) {
                _errorMessage.value = "Failed to backup database: ${e.message}"
            }
        }
    }

    fun restoreDatabase(inputStream: InputStream) {
        viewModelScope.launch {
            try {
                repository.restoreDatabase(inputStream)
            } catch (e: Exception) {
                _errorMessage.value = "Failed to restore database: ${e.message}"
            }
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }
}