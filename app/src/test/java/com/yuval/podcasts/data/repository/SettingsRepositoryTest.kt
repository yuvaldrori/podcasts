package com.yuval.podcasts.data.repository

import android.content.Context
import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
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
        editor = mockk(relaxed = true) // relaxed to handle chained calls

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
}
