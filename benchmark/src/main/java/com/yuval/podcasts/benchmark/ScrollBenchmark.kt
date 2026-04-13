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
        compilationMode = CompilationMode.Full(),
        startupMode = StartupMode.COLD,
        iterations = 5,
        setupBlock = {
            pressHome()
            startActivityAndWait()
            // Wait for content to load
            device.wait(Until.hasObject(By.res("episode_list")), 5000)
        }
    ) {
        val list = device.findObject(By.res("episode_list"))
        list.setGestureMargin(device.displayWidth / 5)
        list.fling(Direction.DOWN)
        device.waitForIdle()
    }
}
