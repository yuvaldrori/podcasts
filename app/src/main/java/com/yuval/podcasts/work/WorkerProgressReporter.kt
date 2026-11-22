package com.yuval.podcasts.work

import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.workDataOf
import com.yuval.podcasts.data.Constants
import com.yuval.podcasts.utils.LogManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Centralizes thread-safe foreground notification throttling and WorkManager progress reporting
 * across background workers, gracefully degrading when foreground promotion is disallowed.
 */
internal class WorkerProgressReporter(
    private val workerTag: String,
    private val logManager: LogManager,
    private val setForegroundFn: suspend (ForegroundInfo) -> Unit,
    private val setProgressFn: suspend (Data) -> Unit
) {
    private val mutex = Mutex()
    private var isForegroundAllowed = true
    private var lastForegroundUpdateMs = 0L

    suspend fun setInitialForeground(createInfo: () -> ForegroundInfo) {
        mutex.withLock {
            trySetForegroundLocked(createInfo, "Failed to set initial foreground status")
        }
    }

    suspend fun setProgress(data: Data) {
        try {
            setProgressFn(data)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logManager.w(workerTag, "Failed to set progress", mapOf("error" to e.message.toString()))
        }
    }

    suspend fun setForegroundIfAllowed(createInfo: () -> ForegroundInfo) {
        mutex.withLock {
            if (isForegroundAllowed) {
                trySetForegroundLocked(createInfo, "Failed to set foreground status progress")
            }
        }
    }

    suspend fun reportCountProgress(
        current: Int,
        total: Int,
        throttleMs: Long,
        createForegroundInfo: (Int, Int) -> ForegroundInfo
    ) {
        mutex.withLock {
            setProgress(workDataOf(Constants.WORK_KEY_PROGRESS to current, Constants.WORK_KEY_TOTAL to total))
            val now = System.currentTimeMillis()
            if (isForegroundAllowed && (current == total || now - lastForegroundUpdateMs > throttleMs)) {
                lastForegroundUpdateMs = now
                trySetForegroundLocked(
                    { createForegroundInfo(current, total) },
                    "Failed to set foreground status progress"
                )
            }
        }
    }

    private suspend fun trySetForegroundLocked(createInfo: () -> ForegroundInfo, errorMessage: String) {
        try {
            setForegroundFn(createInfo())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            isForegroundAllowed = false
            logManager.w(workerTag, errorMessage, mapOf("error" to e.message.toString()))
        }
    }
}
