package com.yuval.podcasts.utils

import android.content.Context
import android.util.Log
import com.yuval.podcasts.data.Constants
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.channels.Channel
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.Date
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
    private val logBuffer = mutableListOf<String>()
    private val bufferLimit = Constants.LOG_BUFFER_LIMIT
    private val flushIntervalMs = Constants.LOG_FLUSH_INTERVAL_MS
    private val logChannel = Channel<LogEntry>(capacity = Channel.UNLIMITED)

    init {
        setupCrashHandler()
        startBufferTimeout()
        processLogChannel()
    }

    private fun processLogChannel() {
        scope.launch {
            for (entry in logChannel) {
                val jsonLine = Json.encodeToString(entry)
                val shouldFlush = synchronized(logBuffer) {
                    logBuffer.add(jsonLine)
                    logBuffer.size >= bufferLimit
                }
                if (shouldFlush) {
                    flushBuffer()
                }
            }
        }
    }

    private fun startBufferTimeout() {
        scope.launch {
            while (true) {
                kotlinx.coroutines.delay(flushIntervalMs)
                flushBuffer()
            }
        }
    }

    private fun setupCrashHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            logSync("CRASH", "Uncaught exception on thread ${thread.name}", "ERROR", 
                mapOf("stacktrace" to Log.getStackTraceString(throwable)))
            flushBufferSync()
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    fun v(tag: String, message: String, metadata: Map<String, String>? = null) = log(tag, message, "VERBOSE", metadata)
    fun d(tag: String, message: String, metadata: Map<String, String>? = null) = log(tag, message, "DEBUG", metadata)
    fun i(tag: String, message: String, metadata: Map<String, String>? = null) = log(tag, message, "INFO", metadata)
    fun w(tag: String, message: String, metadata: Map<String, String>? = null) = log(tag, message, "WARN", metadata)
    fun e(tag: String, message: String, metadata: Map<String, String>? = null) = log(tag, message, "ERROR", metadata)

    private fun log(tag: String, message: String, level: String, metadata: Map<String, String>? = null) {
        val levelInt = getLogLevelInt(level)

        if (levelInt < Constants.MIN_LOG_LEVEL) return

        when (level) {
            "ERROR" -> Log.e(tag, message)
            "WARN" -> Log.w(tag, message)
            "INFO" -> Log.i(tag, message)
            "DEBUG" -> Log.d(tag, message)
            "VERBOSE" -> Log.v(tag, message)
            else -> Log.i(tag, message)
        }
        
        val entry = LogEntry(
            timestamp = dateFormat.format(Instant.now()),
            level = level,
            tag = tag,
            message = message,
            metadata = metadata
        )
        logChannel.trySend(entry)
    }

    private fun getBufferItemsToFlush(): List<String> {
        return synchronized(logBuffer) {
            if (logBuffer.isEmpty()) {
                emptyList()
            } else {
                val copy = logBuffer.toList()
                logBuffer.clear()
                copy
            }
        }
    }

    private fun flushBuffer() {
        val toWrite = getBufferItemsToFlush()
        if (toWrite.isEmpty()) return
        
        try {
            checkRotation()
            activeLogFile.appendText(toWrite.joinToString("\n", postfix = "\n"))
        } catch (e: Exception) {
            Log.e("LogManager", "Failed to flush log buffer", e)
        }
    }

    private fun flushBufferSync() {
        val toWrite = getBufferItemsToFlush()
        if (toWrite.isEmpty()) return
        try {
            activeLogFile.appendText(toWrite.joinToString("\n", postfix = "\n"))
        } catch (e: Exception) {
             Log.e("LogManager", "Failed to flush log buffer sync", e)
        }
    }

    @Synchronized
    private fun logSync(tag: String, message: String, level: String, metadata: Map<String, String>?) {
        val levelInt = getLogLevelInt(level)
        if (levelInt < Constants.MIN_LOG_LEVEL) return

        try {
            val jsonLine = createLogLine(tag, message, level, metadata) + "\n"
            activeLogFile.appendText(jsonLine)
        } catch (e: Exception) {
            Log.e("LogManager", "Failed to write log", e)
        }
    }

    private fun createLogLine(tag: String, message: String, level: String, metadata: Map<String, String>?): String {
        val entry = LogEntry(
            timestamp = dateFormat.format(Instant.now()),
            level = level,
            tag = tag,
            message = message,
            metadata = metadata
        )
        return Json.encodeToString(entry)
    }

    private fun checkRotation() {
        if (activeLogFile.exists() && activeLogFile.length() > maxFileSize) {
            if (previousLogFile.exists()) previousLogFile.delete()
            activeLogFile.renameTo(previousLogFile)
        }
    }

    private fun getLogLevelInt(level: String): Int {
        return when (level) {
            "VERBOSE" -> Log.VERBOSE
            "DEBUG" -> Log.DEBUG
            "INFO" -> Log.INFO
            "WARN" -> Log.WARN
            "ERROR" -> Log.ERROR
            else -> Log.INFO
        }
    }

    fun exportLogsToStream(output: java.io.OutputStream) {
        if (previousLogFile.exists()) {
            previousLogFile.inputStream().use { it.copyTo(output) }
        }
        if (activeLogFile.exists()) {
            activeLogFile.inputStream().use { it.copyTo(output) }
        }
    }
}
