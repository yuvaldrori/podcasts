package com.yuval.podcasts.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

@Singleton
class SettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    private object PreferencesKeys {
        val PLAYBACK_SPEED = floatPreferencesKey("playback_speed")
        val SKIP_SILENCE = booleanPreferencesKey("skip_silence")
    }

    // Default values
    private val DEFAULT_PLAYBACK_SPEED = 1.0f
    private val DEFAULT_SKIP_SILENCE = false

    suspend fun getPlaybackSpeed(): Float = dataStore.data.first()[PreferencesKeys.PLAYBACK_SPEED] ?: DEFAULT_PLAYBACK_SPEED

    suspend fun savePlaybackSpeed(speed: Float) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.PLAYBACK_SPEED] = speed
        }
    }

    suspend fun isSkipSilenceEnabled(): Boolean = dataStore.data.first()[PreferencesKeys.SKIP_SILENCE] ?: DEFAULT_SKIP_SILENCE

    suspend fun saveSkipSilenceEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.SKIP_SILENCE] = enabled
        }
    }

    val skipSilenceFlow: Flow<Boolean> = dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.SKIP_SILENCE] ?: DEFAULT_SKIP_SILENCE
        }

    val playbackSpeedFlow: Flow<Float> = dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.PLAYBACK_SPEED] ?: DEFAULT_PLAYBACK_SPEED
        }
}
