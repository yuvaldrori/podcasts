package com.yuval.podcasts.utils

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.serialization.json.Json
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayOutputStream
import java.io.File

@RunWith(RobolectricTestRunner::class)
class LogManagerTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        File(context.filesDir, "logs").deleteRecursively()
    }

    @Test
    fun exportLogsToStream_flushesPendingEntriesImmediately() {
        val logManager = LogManager(context)
        val note = "immediate-export-${System.nanoTime()}"

        logManager.i("TestTag", note)

        val out = ByteArrayOutputStream()
        logManager.exportLogsToStream(out)
        val exported = out.toString("UTF-8")

        assertTrue("Exported logs should contain newly logged entry immediately", exported.contains(note))
        val lines = exported.lines().filter { it.isNotBlank() }
        lines.forEach { line ->
            Json.decodeFromString<LogEntry>(line)
        }
    }
}
