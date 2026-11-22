package com.yuval.podcasts.benchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.TraceSectionMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalMetricApi::class)
@RunWith(AndroidJUnit4::class)
class StartupBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun startup() = benchmarkRule.measureRepeated(
        packageName = "com.yuval.podcasts",
        metrics = listOf(
            StartupTimingMetric(),
            TraceSectionMetric("MainActivity_onCreate")
        ),
        iterations = 5,
        startupMode = StartupMode.COLD,
        compilationMode = CompilationMode.DEFAULT,
        setupBlock = {
            // Prevent POST_NOTIFICATIONS runtime dialog from interrupting cold startup timing
            device.executeShellCommand("pm grant com.yuval.podcasts android.permission.POST_NOTIFICATIONS")
            pressHome()
        }
    ) {
        startActivityAndWait()
    }
}
