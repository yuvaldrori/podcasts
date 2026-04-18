package com.yuval.podcasts.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

@Singleton
class SettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    private object PreferencesKeys {
        val PLAYBACK_SPEED = floatPreferencesKey("playback_speed")
        val SKIP_SILENCE = booleanPreferencesKey("skip_silence")
    }

    // Synchronous access for players that need immediate initialization values
    fun getPlaybackSpeed(): Float = runBlocking {
        dataStore.data.first()[PreferencesKeys.PLAYBACK_SPEED] ?: 2.0f
    }

    suspend fun savePlaybackSpeed(speed: Float) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.PLAYBACK_SPEED] = speed
        }
    }

    fun isSkipSilenceEnabled(): Boolean = runBlocking {
        dataStore.data.first()[PreferencesKeys.SKIP_SILENCE] ?: false
    }

    suspend fun saveSkipSilenceEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.SKIP_SILENCE] = enabled
        }
    }

    val skipSilenceFlow: Flow<Boolean> = dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.SKIP_SILENCE] ?: false
        }

    val playbackSpeedFlow: Flow<Float> = dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.PLAYBACK_SPEED] ?: 2.0f
        }
}
