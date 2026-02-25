package com.yuval.podcasts.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yuval.podcasts.data.repository.PodcastRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: PodcastRepository
) : ViewModel() {

    fun addPodcast(url: String) {
        viewModelScope.launch {
            try {
                repository.fetchAndStorePodcast(url)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun importOpml(inputStream: InputStream) {
        viewModelScope.launch {
            repository.importOpml(inputStream)
        }
    }

    fun exportOpml(outputStream: OutputStream) {
        viewModelScope.launch {
            repository.exportOpml(outputStream)
        }
    }

    fun backupDatabase(outputStream: OutputStream) {
        viewModelScope.launch {
            repository.backupDatabase(outputStream)
        }
    }

    fun restoreDatabase(inputStream: InputStream) {
        viewModelScope.launch {
            repository.restoreDatabase(inputStream)
        }
    }
}