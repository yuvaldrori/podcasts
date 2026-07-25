package com.yuval.podcasts.data.repository

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadProgressTracker @Inject constructor() {

    private val _progressMap = MutableStateFlow<Map<String, Int>>(emptyMap())
    val progressMap: StateFlow<Map<String, Int>> = _progressMap.asStateFlow()

    fun updateProgress(episodeId: String, progressPercent: Int) {
        _progressMap.update { current ->
            current + (episodeId to progressPercent.coerceIn(0, 100))
        }
    }

    fun clearProgress(episodeId: String) {
        _progressMap.update { current ->
            current - episodeId
        }
    }

    fun getProgress(episodeId: String): Int {
        return _progressMap.value[episodeId] ?: 0
    }
}
