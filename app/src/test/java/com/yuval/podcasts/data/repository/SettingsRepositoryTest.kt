package com.yuval.podcasts.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.yuval.podcasts.data.Constants
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
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
    private lateinit var testScope: TestScope

    @Before
    fun setup() {
        val testDispatcher = UnconfinedTestDispatcher()
        testScope = TestScope(testDispatcher + Job())
        val testFile = File(tmpFolder.newFolder(), "test_settings.preferences_pb")

        testDataStore = PreferenceDataStoreFactory.create(
            scope = testScope,
            produceFile = { testFile }
        )

        settingsRepository = SettingsRepository(testDataStore)
    }

    @After
    fun teardown() {
        testScope.cancel()
    }

    @Test
    fun getPlaybackSpeed_returnsDefault() = testScope.runTest {
        val speed = settingsRepository.getPlaybackSpeed()
        assertEquals(Constants.DEFAULT_PLAYBACK_SPEED, speed)
    }

    @Test
    fun savePlaybackSpeed_savesValue() = testScope.runTest {
        settingsRepository.savePlaybackSpeed(1.5f)
        val speed = settingsRepository.getPlaybackSpeed()
        assertEquals(1.5f, speed)
    }

    @Test
    fun isSkipSilenceEnabled_returnsDefault() = testScope.runTest {
        val enabled = settingsRepository.isSkipSilenceEnabled()
        assertEquals(true, enabled)
    }

    @Test
    fun skipSilenceFlow_emitsInitialValue() = testScope.runTest {
        val enabled = settingsRepository.skipSilenceFlow.first()
        assertEquals(true, enabled)
    }

    @Test
    fun isVolumeBoostEnabled_returnsDefault() = testScope.runTest {
        val enabled = settingsRepository.isVolumeBoostEnabled()
        assertEquals(true, enabled)
    }

    @Test
    fun volumeBoostFlow_emitsInitialValue() = testScope.runTest {
        val enabled = settingsRepository.volumeBoostFlow.first()
        assertEquals(true, enabled)
    }
}
