package com.yuval.podcasts.utils

import android.content.Context
import android.util.Log
import com.yuval.podcasts.data.Constants
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.OutputStream
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class LogEntry(
    val timestamp: String,
    val level: String,
    val tag: String,
    val message: String,
    val metadata: Map<String, String>? = null
)

@Singleton
class LogManager @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val logDir = File(context.filesDir, Constants.LOG_DIR_NAME).apply { if (!exists()) mkdirs() }
    private val activeLogFile = File(logDir, Constants.LOG_FILE_ACTIVE)
    private val previousLogFile = File(logDir, Constants.LOG_FILE_PREVIOUS)
    private val maxFileSize = Constants.MAX_LOG_FILE_SIZE

    private val dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
        .withZone(ZoneId.systemDefault())
    private val fileLock = Any()
    private val pendingEntries = mutableListOf<LogEntry>()
    private val bufferLimit = Constants.LOG_BUFFER_LIMIT
    private val flushIntervalMs = Constants.LOG_FLUSH_INTERVAL_MS
    private val wakeSignal = Channel<Unit>(capacity = Channel.CONFLATED)

    init {
        setupCrashHandler()
        processPendingLogs()
    }

    private fun processPendingLogs() {
        scope.launch {
            while (true) {
                wakeSignal.receive()
                val deadlineMs = System.currentTimeMillis() + flushIntervalMs
                while (synchronized(fileLock) { pendingEntries.size } < bufferLimit) {
                    val remainingMs = deadlineMs - System.currentTimeMillis()
                    if (remainingMs <= 0L) break
                    withTimeoutOrNull(remainingMs) { wakeSignal.receive() } ?: break
                }
                flushPending()
            }
        }
    }

    private fun setupCrashHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            logSync(
                "CRASH",
                "Uncaught exception on thread ${thread.name}",
                "ERROR",
                mapOf("stacktrace" to Log.getStackTraceString(throwable))
            )
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    fun v(tag: String, message: String, metadata: Map<String, String>? = null) = log(tag, message, "VERBOSE", metadata)
    fun d(tag: String, message: String, metadata: Map<String, String>? = null) = log(tag, message, "DEBUG", metadata)
    fun i(tag: String, message: String, metadata: Map<String, String>? = null) = log(tag, message, "INFO", metadata)
    fun w(tag: String, message: String, metadata: Map<String, String>? = null) = log(tag, message, "WARN", metadata)
    fun e(tag: String, message: String, metadata: Map<String, String>? = null) = log(tag, message, "ERROR", metadata)

    private fun log(tag: String, message: String, level: String, metadata: Map<String, String>? = null) {
        if (getLogLevelInt(level) < Constants.MIN_LOG_LEVEL) return

        when (level) {
            "ERROR" -> Log.e(tag, message)
            "WARN" -> Log.w(tag, message)
            "INFO" -> Log.i(tag, message)
            "DEBUG" -> Log.d(tag, message)
            "VERBOSE" -> Log.v(tag, message)
            else -> Log.i(tag, message)
        }

        val entry = createEntry(tag, message, level, metadata)
        synchronized(fileLock) {
            pendingEntries.add(entry)
        }
        wakeSignal.trySend(Unit)
    }

    private fun createEntry(tag: String, message: String, level: String, metadata: Map<String, String>?): LogEntry =
        LogEntry(
            timestamp = dateFormat.format(Instant.now()),
            level = level,
            tag = tag,
            message = message,
            metadata = metadata
        )

    private fun flushPending(rotate: Boolean = true) {
        synchronized(fileLock) {
            flushPendingLocked(rotate)
        }
    }

    private fun flushPendingLocked(rotate: Boolean) {
        if (pendingEntries.isEmpty()) return

        val payload = pendingEntries.joinToString("\n", postfix = "\n") { Json.encodeToString(it) }
        pendingEntries.clear()
        try {
            if (rotate) checkRotationLocked()
            activeLogFile.appendText(payload)
        } catch (e: Exception) {
            Log.e("LogManager", "Failed to flush log buffer", e)
        }
    }

    private fun logSync(tag: String, message: String, level: String, metadata: Map<String, String>?) {
        if (getLogLevelInt(level) < Constants.MIN_LOG_LEVEL) return
        val entry = createEntry(tag, message, level, metadata)
        synchronized(fileLock) {
            pendingEntries.add(entry)
            flushPendingLocked(rotate = false)
        }
    }

    private fun checkRotationLocked() {
        if (activeLogFile.exists() && activeLogFile.length() > maxFileSize) {
            if (previousLogFile.exists()) previousLogFile.delete()
            activeLogFile.renameTo(previousLogFile)
        }
    }

    private fun getLogLevelInt(level: String): Int = when (level) {
        "VERBOSE" -> Log.VERBOSE
        "DEBUG" -> Log.DEBUG
        "INFO" -> Log.INFO
        "WARN" -> Log.WARN
        "ERROR" -> Log.ERROR
        else -> Log.INFO
    }

    fun exportLogsToStream(output: OutputStream) {
        synchronized(fileLock) {
            flushPendingLocked(rotate = true)
            if (previousLogFile.exists()) {
                previousLogFile.inputStream().use { it.copyTo(output) }
            }
            if (activeLogFile.exists()) {
                activeLogFile.inputStream().use { it.copyTo(output) }
            }
        }
    }
}
