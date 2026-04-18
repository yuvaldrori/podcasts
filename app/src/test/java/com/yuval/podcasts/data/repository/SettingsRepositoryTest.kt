package com.yuval.podcasts.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsRepositoryTest {

    @get:Rule
    val tmpFolder = TemporaryFolder()

    private lateinit var testDataStore: DataStore<Preferences>
    private lateinit var settingsRepository: SettingsRepository
    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher + Job())

    @Before
    fun setup() {
        val testFile = File(tmpFolder.newFolder(), "test_settings.preferences_pb")
        
        testDataStore = PreferenceDataStoreFactory.create(
            scope = testScope,
            produceFile = { testFile }
        )

        settingsRepository = SettingsRepository(testDataStore)
    }

    @Test
    fun getPlaybackSpeed_returnsDefault() = runTest {
        val speed = settingsRepository.getPlaybackSpeed()
        assertEquals(2f, speed)
    }

    @Test
    fun savePlaybackSpeed_savesValue() = runTest {
        settingsRepository.savePlaybackSpeed(1.5f)
        val speed = settingsRepository.getPlaybackSpeed()
        assertEquals(1.5f, speed)
    }

    @Test
    fun isSkipSilenceEnabled_returnsDefault() = runTest {
        val enabled = settingsRepository.isSkipSilenceEnabled()
        assertEquals(false, enabled)
    }

    @Test
    fun saveSkipSilenceEnabled_savesValue() = runTest {
        settingsRepository.saveSkipSilenceEnabled(true)
        val enabled = settingsRepository.isSkipSilenceEnabled()
        assertEquals(true, enabled)
    }

    @Test
    fun skipSilenceFlow_emitsInitialValue() = runTest {
        val enabled = settingsRepository.skipSilenceFlow.first()
        assertEquals(false, enabled)
    }

    @Test
    fun skipSilenceFlow_emitsNewValueOnSave() = runTest {
        val values = mutableListOf<Boolean>()
        val job = launch(testDispatcher) {
            settingsRepository.skipSilenceFlow.collect { values.add(it) }
        }
        
        settingsRepository.saveSkipSilenceEnabled(true)
        
        assertEquals(listOf(false, true), values)
        job.cancel()
    }
}
