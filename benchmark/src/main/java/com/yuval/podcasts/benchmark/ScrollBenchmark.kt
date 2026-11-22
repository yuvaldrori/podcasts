package com.yuval.podcasts.benchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScrollBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun scrollNewEpisodesList() = benchmarkRule.measureRepeated(
        packageName = "com.yuval.podcasts",
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.Partial(),
        startupMode = StartupMode.HOT,
        iterations = 5,
        setupBlock = {
            // Grant notification permission upfront so no system dialog interferes
            device.executeShellCommand("pm grant com.yuval.podcasts android.permission.POST_NOTIFICATIONS")

            pressHome()
            startActivityAndWait()

            // Target the exact Compose testTag exposed as resourceId ("tab_new_episodes")
            check(device.wait(Until.hasObject(By.res("tab_new_episodes")), 15_000L)) {
                "Navigation tab 'tab_new_episodes' not found!"
            }
            val tab = checkNotNull(device.findObject(By.res("tab_new_episodes"))) {
                "Navigation tab 'tab_new_episodes' disappeared!"
            }
            tab.click()
            device.waitForIdle()

            // Wait for the New Episodes LazyColumn specifically (not QueueScreen's list)
            check(device.wait(Until.hasObject(By.res("episode_list")), 15_000L)) {
                "New Episodes list 'episode_list' not found!"
            }
            val list = checkNotNull(device.findObject(By.res("episode_list"))) {
                "New Episodes list 'episode_list' disappeared!"
            }
            // Under StartupMode.HOT, scroll back to the top before each iteration so every
            // measure block performs a real downward fling.
            list.setGestureMargin(device.displayWidth / 5)
            list.fling(Direction.UP)
            device.waitForIdle()
        }
    ) {
        val list = checkNotNull(device.wait(Until.findObject(By.res("episode_list")), 10_000L)) {
            "New Episodes list 'episode_list' not found in measure block!"
        }
        list.setGestureMargin(device.displayWidth / 5)
        list.fling(Direction.DOWN)
        device.waitForIdle()
    }
}
