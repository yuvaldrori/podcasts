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

import android.content.Context
import android.net.Uri

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

    fun importOpml(context: Context, uri: Uri) {
        viewModelScope.launch {
            try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    repository.importOpml(stream)
                }
            } catch (e: Exception) {
                _errorMessage.value = "Failed to import OPML: ${e.message}"
            }
        }
    }

    fun exportOpml(context: Context, uri: Uri) {
        viewModelScope.launch {
            try {
                context.contentResolver.openOutputStream(uri)?.use { stream ->
                    repository.exportOpml(stream)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _errorMessage.value = "Failed to export OPML: ${e.message}"
            }
        }
    }

    fun backupDatabase(context: Context, uri: Uri) {
        viewModelScope.launch {
            try {
                context.contentResolver.openOutputStream(uri)?.use { stream ->
                    repository.backupDatabase(stream)
                }
            } catch (e: Exception) {
                _errorMessage.value = "Failed to backup database: ${e.message}"
            }
        }
    }

    fun restoreDatabase(context: Context, uri: Uri) {
        viewModelScope.launch {
            try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    repository.restoreDatabase(stream)
                }
            } catch (e: Exception) {
                _errorMessage.value = "Failed to restore database: ${e.message}"
            }
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }
}