package com.yuval.podcasts.data.repository

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.yuval.podcasts.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class LocalMediaMetadata(
    val title: String,
    val artist: String,
    val durationSecs: Long,
    val description: String,
    val destFile: File
)

@Singleton
class LocalMediaDataSource @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {
    suspend fun copyAndExtract(uri: Uri): Result<LocalMediaMetadata> = withContext(ioDispatcher) {
        try {
            // 1. Copy the file to internal storage
            val fileName = getFileName(uri) ?: "imported_audio_${System.currentTimeMillis()}.mp3"
            val destDir = File(context.filesDir, "local_podcasts").apply { mkdirs() }
            val destFile = File(destDir, fileName)

            context.contentResolver.openInputStream(uri)?.use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: return@withContext Result.failure(Exception("Could not open input stream for URI"))

            // 2. Extract Metadata
            val retriever = android.media.MediaMetadataRetriever()
            retriever.setDataSource(destFile.absolutePath)
            
            val title = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_TITLE) 
                ?: fileName.substringBeforeLast(".")
            val artist = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_ARTIST) 
                ?: retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST) 
                ?: "Unknown Artist"
            val durationStr = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
            val durationMs = durationStr?.toLongOrNull() ?: 0L
            val durationSecs = durationMs / 1000L
            
            retriever.release()

            // 3. Clean up title using Regex if it's just the filename (basic AI fallback)
            val cleanTitle = if (title == fileName.substringBeforeLast(".")) {
                title.replace(Regex("[-_]"), " ").replace(Regex("([a-z])([A-Z]+)"), "$1 $2")
            } else {
                title
            }

            val description = "$artist • ${formatFileSize(destFile.length())}"

            Result.success(LocalMediaMetadata(cleanTitle, artist, durationSecs, description, destFile))
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    private fun getFileName(uri: Uri): String? {
        var result: String? = null
        if (uri.scheme == "content") {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        result = cursor.getString(nameIndex)
                    }
                }
            }
        }
        if (result == null) {
            result = uri.path?.substringAfterLast('/')
        }
        return result
    }

    private fun formatFileSize(sizeBytes: Long): String {
        val mb = sizeBytes / (1024.0 * 1024.0)
        return String.format(java.util.Locale.US, "%.1f MB", mb)
    }
}
