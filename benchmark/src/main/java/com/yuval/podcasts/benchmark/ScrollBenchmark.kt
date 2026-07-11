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
        compilationMode = CompilationMode.None(),
        startupMode = StartupMode.HOT,
        iterations = 5,
        setupBlock = {
            try {
                // Grant notification permission to prevent dialog from blocking UI
                device.executeShellCommand("pm grant com.yuval.podcasts android.permission.POST_NOTIFICATIONS")
                
                pressHome()
                try {
                    startActivityAndWait()
                } catch (e: Throwable) {
                    android.util.Log.w("ScrollBenchmark", "startActivityAndWait timed out, continuing to wait for tab...", e)
                }
                
                // Wait for either the permission dialog, ANR dialog, or the main screen tab to appear (handles race conditions)
                var found = false
                for (i in 1..60) {
                    val allowButton = device.findObject(By.res("com.android.permissioncontroller:id/permission_allow_button"))
                    if (allowButton != null) {
                        allowButton.click()
                    }
                    
                    // Dismiss system ANR dialog by clicking "Wait" if it appears
                    val waitButton = device.findObject(By.text("Wait")) ?: device.findObject(By.res("android:id/aerr_wait"))
                    if (waitButton != null) {
                        waitButton.click()
                    }
                    
                    if (device.hasObject(By.text("New"))) {
                        found = true
                        break
                    }
                    Thread.sleep(1000)
                }
                check(found) { "Tab 'New' not found!" }
                val tab = device.findObject(By.text("New"))
                checkNotNull(tab) { "Tab 'New' not found!" }.click()
                
                // Wait for content to load
                device.wait(Until.hasObject(By.scrollable(true)), 30000)
                val list = device.findObject(By.scrollable(true))
                checkNotNull(list) { "List scrollable not found!" }
            } catch (e: Throwable) {
                android.util.Log.e("ScrollBenchmark", "BENCHMARK SETUP FAILED: ${e.message}", e)
                try {
                    // Dump the visible UI tree using our helper
                    android.util.Log.e("ScrollBenchmark", "STARTING UI OBJECT TREE DUMP:")
                    device.findObjects(By.depth(0)).forEach { root ->
                        logNode(root, 0)
                    }
                    android.util.Log.e("ScrollBenchmark", "FINISHED UI OBJECT TREE DUMP")
                } catch (ex: Exception) {
                    android.util.Log.e("ScrollBenchmark", "Failed to dump node tree", ex)
                }
                throw e
            }
        }
    ) {
        try {
            device.wait(Until.hasObject(By.scrollable(true)), 10000)
            val list = device.findObject(By.scrollable(true))
            checkNotNull(list) { "List scrollable not found in measure block!" }
            list.setGestureMargin(device.displayWidth / 5)
            list.fling(Direction.DOWN)
            device.waitForIdle()
        } catch (e: Throwable) {
            android.util.Log.e("ScrollBenchmark", "MEASURE BLOCK FAILED: ${e.message}", e)
            try {
                android.util.Log.e("ScrollBenchmark", "STARTING UI OBJECT TREE DUMP (MEASURE):")
                device.findObjects(By.depth(0)).forEach { root ->
                    logNode(root, 0)
                }
                android.util.Log.e("ScrollBenchmark", "FINISHED UI OBJECT TREE DUMP (MEASURE)")
            } catch (ex: Exception) {
                android.util.Log.e("ScrollBenchmark", "Failed to dump node tree", ex)
            }
            throw e
        }
    }

    private fun logNode(node: androidx.test.uiautomator.UiObject2, depth: Int) {
        val indent = "  ".repeat(depth)
        try {
            val text = node.text ?: ""
            val desc = node.contentDescription ?: ""
            val res = node.resourceName ?: ""
            val className = node.className ?: ""
            android.util.Log.e("ScrollBenchmark", "$indent- Class: $className, Res: $res, Text: $text, Desc: $desc")
            
            val children = try { node.children } catch (e: Exception) { null }
            if (children != null) {
                for (child in children) {
                    if (child != null) {
                        logNode(child, depth + 1)
                    }
                }
            }
        } catch (ex: Exception) {
            android.util.Log.e("ScrollBenchmark", "$indent- [STALE NODE OR ERROR: ${ex.message}]")
        }
    }
}
