package com.yuval.podcasts.data.repository

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DownloadProgressTrackerTest {

    @Test
    fun updateProgress_updatesEpisodeProgress() = runTest {
        val tracker = DownloadProgressTracker()

        tracker.updateProgress("ep_1", 45)
        assertEquals(45, tracker.getProgress("ep_1"))
        assertEquals(mapOf("ep_1" to 45), tracker.progressMap.first())

        tracker.updateProgress("ep_1", 90)
        assertEquals(90, tracker.getProgress("ep_1"))

        tracker.clearProgress("ep_1")
        assertEquals(0, tracker.getProgress("ep_1"))
        assertEquals(emptyMap<String, Int>(), tracker.progressMap.first())
    }

    @Test
    fun updateProgress_coercesPercentageRange() = runTest {
        val tracker = DownloadProgressTracker()

        tracker.updateProgress("ep_2", 150)
        assertEquals(100, tracker.getProgress("ep_2"))

        tracker.updateProgress("ep_2", -20)
        assertEquals(0, tracker.getProgress("ep_2"))
    }
}
