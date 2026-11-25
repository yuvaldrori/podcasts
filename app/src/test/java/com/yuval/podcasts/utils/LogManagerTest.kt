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
import java.io.OutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

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

    @Test
    fun log_doesNotBlockWhileExportHoldsFileLock() {
        val logManager = LogManager(context)
        logManager.i("TestTag", "seed-entry")

        val exportHoldingFileLock = CountDownLatch(1)
        val allowExportToFinish = CountDownLatch(1)
        val concurrentLogFinished = CountDownLatch(1)

        val blockingStream = object : OutputStream() {
            override fun write(b: Int) {
                exportHoldingFileLock.countDown()
                allowExportToFinish.await(5, TimeUnit.SECONDS)
            }

            override fun write(b: ByteArray, off: Int, len: Int) {
                exportHoldingFileLock.countDown()
                allowExportToFinish.await(5, TimeUnit.SECONDS)
            }
        }

        val exportThread = thread {
            logManager.exportLogsToStream(blockingStream)
        }

        assertTrue("Export should acquire file lock and begin writing", exportHoldingFileLock.await(5, TimeUnit.SECONDS))

        val logThread = thread {
            logManager.i("TestTag", "concurrent-non-blocking-entry")
            concurrentLogFinished.countDown()
        }

        val completedWithoutBlocking = concurrentLogFinished.await(1, TimeUnit.SECONDS)
        allowExportToFinish.countDown()
        exportThread.join(5_000)
        logThread.join(5_000)

        assertTrue("log() must not block while file I/O holds fileLock", completedWithoutBlocking)

        val verifyOut = ByteArrayOutputStream()
        logManager.exportLogsToStream(verifyOut)
        assertTrue(
            "Concurrently logged entry must be persisted after export completes",
            verifyOut.toString("UTF-8").contains("concurrent-non-blocking-entry")
        )
    }
}
