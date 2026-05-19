package com.yuval.podcasts.utils

import android.content.Context
import com.yuval.podcasts.data.Constants
import java.io.File

object StorageUtils {
    /**
     * Returns the directory where podcast audio files are stored.
     */
    fun getDownloadsDir(context: Context): File {
        return File(context.filesDir, Constants.DOWNLOAD_DIR_NAME).apply {
            if (!exists()) mkdirs()
        }
    }

    /**
     * Generates a consistent file name for a podcast episode.
     * Uses SHA-1 hex to minimize collision risk.
     */
    fun getFileName(episodeId: String): String {
        return try {
            val hash = java.security.MessageDigest.getInstance("SHA-1")
                .digest(episodeId.toByteArray())
                .joinToString("") { "%02x".format(it) }
            "episode_${hash}.mp3"
        } catch (e: Exception) {
            "episode_${episodeId.hashCode()}.mp3"
        }
    }

    /**
     * Returns the [File] object for a specific episode ID.
     */
    fun getFileForEpisode(context: Context, episodeId: String): File {
        return File(getDownloadsDir(context), getFileName(episodeId))
    }
}
