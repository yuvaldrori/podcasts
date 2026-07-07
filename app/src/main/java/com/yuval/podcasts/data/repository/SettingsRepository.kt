package com.yuval.podcasts.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import com.yuval.podcasts.data.Constants

@Singleton
class SettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    private object PreferencesKeys {
        val PLAYBACK_SPEED = floatPreferencesKey("playback_speed")
        val SKIP_SILENCE = booleanPreferencesKey("skip_silence")
        val CLEANUP_SCHEDULED = booleanPreferencesKey("cleanup_scheduled")
        val VOLUME_BOOST = booleanPreferencesKey("volume_boost")
        val LAST_PLAYED_EPISODE_ID = stringPreferencesKey("last_played_episode_id")
    }

    suspend fun getLastPlayedEpisodeId(): String? = dataStore.data.first()[PreferencesKeys.LAST_PLAYED_EPISODE_ID]

    suspend fun saveLastPlayedEpisodeId(episodeId: String?) {
        dataStore.edit { preferences ->
            if (episodeId == null) {
                preferences.remove(PreferencesKeys.LAST_PLAYED_EPISODE_ID)
            } else {
                preferences[PreferencesKeys.LAST_PLAYED_EPISODE_ID] = episodeId
            }
        }
    }

    // Default values
    private val DEFAULT_PLAYBACK_SPEED = Constants.DEFAULT_PLAYBACK_SPEED
    private val DEFAULT_SKIP_SILENCE = true
    private val DEFAULT_VOLUME_BOOST = true

    suspend fun isCleanupScheduled(): Boolean = dataStore.data.first()[PreferencesKeys.CLEANUP_SCHEDULED] ?: false

    suspend fun setCleanupScheduled(scheduled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.CLEANUP_SCHEDULED] = scheduled
        }
    }

    suspend fun getPlaybackSpeed(): Float = dataStore.data.first()[PreferencesKeys.PLAYBACK_SPEED] ?: DEFAULT_PLAYBACK_SPEED

    suspend fun savePlaybackSpeed(speed: Float) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.PLAYBACK_SPEED] = speed
        }
    }

    suspend fun isSkipSilenceEnabled(): Boolean = dataStore.data.first()[PreferencesKeys.SKIP_SILENCE] ?: DEFAULT_SKIP_SILENCE



    val skipSilenceFlow: Flow<Boolean> = dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.SKIP_SILENCE] ?: DEFAULT_SKIP_SILENCE
        }

    val playbackSpeedFlow: Flow<Float> = dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.PLAYBACK_SPEED] ?: DEFAULT_PLAYBACK_SPEED
        }

    suspend fun isVolumeBoostEnabled(): Boolean = dataStore.data.first()[PreferencesKeys.VOLUME_BOOST] ?: DEFAULT_VOLUME_BOOST



    val volumeBoostFlow: Flow<Boolean> = dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.VOLUME_BOOST] ?: DEFAULT_VOLUME_BOOST
        }
}
