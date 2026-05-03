package com.yuval.podcasts.utils

import android.content.Context
import android.os.Environment
import android.util.Log
import com.yuval.podcasts.data.db.entity.Episode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
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
    @ApplicationContext private val context: Context
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val logDir = File(context.filesDir, "logs").apply { if (!exists()) mkdirs() }
    private val activeLogFile = File(logDir, "app_log_active.jsonl")
    private val previousLogFile = File(logDir, "app_log_previous.jsonl")
    private val maxFileSize = 5 * 1024 * 1024 // 5MB

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    init {
        setupCrashHandler()
    }

    private fun setupCrashHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            logSync("CRASH", "Uncaught exception on thread ${thread.name}", "ERROR", 
                mapOf("stacktrace" to Log.getStackTraceString(throwable)))
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    fun i(tag: String, message: String, metadata: Map<String, String>? = null) = log(tag, message, "INFO", metadata)
    fun w(tag: String, message: String, metadata: Map<String, String>? = null) = log(tag, message, "WARN", metadata)
    fun e(tag: String, message: String, metadata: Map<String, String>? = null) = log(tag, message, "ERROR", metadata)

    private fun log(tag: String, message: String, level: String, metadata: Map<String, String>? = null) {
        // Also log to logcat for development
        when (level) {
            "ERROR" -> Log.e(tag, message)
            "WARN" -> Log.w(tag, message)
            else -> Log.i(tag, message)
        }
        
        scope.launch {
            logSync(tag, message, level, metadata)
        }
    }

    @Synchronized
    private fun logSync(tag: String, message: String, level: String, metadata: Map<String, String>?) {
        try {
            checkRotation()
            val entry = LogEntry(
                timestamp = dateFormat.format(Date()),
                level = level,
                tag = tag,
                message = message,
                metadata = metadata
            )
            val jsonLine = Json.encodeToString(entry) + "\n"
            activeLogFile.appendText(jsonLine)
        } catch (e: Exception) {
            Log.e("LogManager", "Failed to write log", e)
        }
    }

    private fun checkRotation() {
        if (activeLogFile.exists() && activeLogFile.length() > maxFileSize) {
            if (previousLogFile.exists()) previousLogFile.delete()
            activeLogFile.renameTo(previousLogFile)
        }
    }

    fun exportLogs(): Result<File> {
        return try {
            val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val exportFile = File(downloadDir, "podcasts_logs_$timestamp.jsonl")
            
            FileOutputStream(exportFile).use { output ->
                if (previousLogFile.exists()) {
                    previousLogFile.inputStream().use { it.copyTo(output) }
                }
                if (activeLogFile.exists()) {
                    activeLogFile.inputStream().use { it.copyTo(output) }
                }
            }
            Result.success(exportFile)
        } catch (e: Exception) {
            Log.e("LogManager", "Export failed", e)
            Result.failure(e)
        }
    }
}
