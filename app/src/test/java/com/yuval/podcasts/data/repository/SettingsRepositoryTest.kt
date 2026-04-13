package com.yuval.podcasts.data.repository

import android.content.Context
import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class SettingsRepositoryTest {

    private lateinit var context: Context
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var editor: SharedPreferences.Editor
    private lateinit var settingsRepository: SettingsRepository

    @Before
    fun setup() {
        context = mockk()
        sharedPreferences = mockk()
        editor = mockk(relaxed = true)

        every { context.getSharedPreferences("podcast_settings", Context.MODE_PRIVATE) } returns sharedPreferences
        every { sharedPreferences.edit() } returns editor

        settingsRepository = SettingsRepository(context)
    }

    @Test
    fun getPlaybackSpeed_returnsDefault() {
        every { sharedPreferences.getFloat("playback_speed", 2f) } returns 2f
        val speed = settingsRepository.getPlaybackSpeed()
        assertEquals(2f, speed)
    }

    @Test
    fun getPlaybackSpeed_returnsSavedValue() {
        every { sharedPreferences.getFloat("playback_speed", 2f) } returns 1.5f
        val speed = settingsRepository.getPlaybackSpeed()
        assertEquals(1.5f, speed)
    }

    @Test
    fun savePlaybackSpeed_savesValue() {
        every { editor.putFloat("playback_speed", 1.5f) } returns editor
        every { editor.apply() } returns Unit
        
        settingsRepository.savePlaybackSpeed(1.5f)
        
        verify { 
            editor.putFloat("playback_speed", 1.5f)
            editor.apply()
        }
    }

    @Test
    fun isSkipSilenceEnabled_returnsDefault() {
        every { sharedPreferences.getBoolean("skip_silence", false) } returns false
        val enabled = settingsRepository.isSkipSilenceEnabled()
        assertEquals(false, enabled)
    }

    @Test
    fun saveSkipSilenceEnabled_savesValue() {
        every { editor.putBoolean("skip_silence", true) } returns editor
        
        settingsRepository.saveSkipSilenceEnabled(true)
        
        verify { 
            editor.putBoolean("skip_silence", true)
            editor.apply()
        }
    }

    @Test
    fun skipSilenceFlow_emitsInitialValue() = runTest {
        every { sharedPreferences.getBoolean("skip_silence", false) } returns true
        val enabled = settingsRepository.skipSilenceFlow().first()
        assertEquals(true, enabled)
    }

    @Test
    fun skipSilenceFlow_emitsNewValueOnSave() = runTest {
        every { sharedPreferences.getBoolean("skip_silence", false) } returns false
        every { editor.putBoolean("skip_silence", true) } returns editor
        
        val values = mutableListOf<Boolean>()
        val job = launch {
            settingsRepository.skipSilenceFlow().collect { values.add(it) }
        }
        advanceUntilIdle()
        
        settingsRepository.saveSkipSilenceEnabled(true)
        advanceUntilIdle()
        
        assertEquals(listOf(false, true), values)
        job.cancel()
    }
}
