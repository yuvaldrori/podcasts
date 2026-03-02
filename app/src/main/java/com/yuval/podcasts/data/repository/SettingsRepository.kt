package com.yuval.podcasts.data.repository

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs: SharedPreferences = context.getSharedPreferences("podcast_settings", Context.MODE_PRIVATE)

    fun getPlaybackSpeed(): Float {
        return prefs.getFloat("playback_speed", 2f)
    }

    fun savePlaybackSpeed(speed: Float) {
        prefs.edit { putFloat("playback_speed", speed) }
    }
}
