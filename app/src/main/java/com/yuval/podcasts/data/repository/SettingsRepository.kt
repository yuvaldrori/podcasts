package com.yuval.podcasts.data.repository

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.onStart

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs: SharedPreferences = context.getSharedPreferences("podcast_settings", Context.MODE_PRIVATE)
    private val _skipSilenceFlow = MutableSharedFlow<Boolean>(replay = 1)

    fun getPlaybackSpeed(): Float {
        return prefs.getFloat("playback_speed", 2f)
    }

    fun savePlaybackSpeed(speed: Float) {
        prefs.edit { putFloat("playback_speed", speed) }
    }

    fun isSkipSilenceEnabled(): Boolean {
        return prefs.getBoolean("skip_silence", false)
    }

    fun saveSkipSilenceEnabled(enabled: Boolean) {
        prefs.edit { putBoolean("skip_silence", enabled) }
        // For simple unit tests without a real collector, a direct emit might be better
        // but SharedFlow.emit is suspend.
        _skipSilenceFlow.tryEmit(enabled)
    }

    fun skipSilenceFlow(): Flow<Boolean> = _skipSilenceFlow.onStart {
        emit(isSkipSilenceEnabled())
    }
}
