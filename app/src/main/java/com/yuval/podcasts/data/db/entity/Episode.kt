package com.yuval.podcasts.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import com.yuval.podcasts.data.Constants
import java.io.File
import kotlin.time.Duration.Companion.seconds

enum class DownloadStatus(val value: Int) {
    NOT_DOWNLOADED(0),
    DOWNLOADING(1),
    DOWNLOADED(2);

    companion object {
        fun fromInt(value: Int) = entries.find { it.value == value } ?: NOT_DOWNLOADED
    }
}

@Entity(
    tableName = "episodes",
    primaryKeys = ["id", "podcastFeedUrl"],
    indices = [
        Index(value = ["isPlayed", "pubDate"]),
        Index(value = ["podcastFeedUrl"])
    ]
)
data class Episode(
    val id: String, // Guid or url
    val podcastFeedUrl: String, // Foreign key
    val title: String,
    val description: String,
    val audioUrl: String,
    val imageUrl: String? = null,
    @ColumnInfo(defaultValue = "NULL")
    val episodeWebLink: String? = null,
    val pubDate: Long,
    val duration: Long,
    @ColumnInfo(defaultValue = "0")
    val downloadStatus: Int, // 0 = Not Downloaded, 1 = Downloading, 2 = Downloaded
    val localFilePath: String?,
    @ColumnInfo(defaultValue = "0")
    val isPlayed: Boolean = false,
    @ColumnInfo(defaultValue = "0")
    val lastPlayedPosition: Long = 0L,
    @ColumnInfo(defaultValue = "NULL")
    val completedAt: Long? = null
) {
    val isLocal: Boolean
        get() = podcastFeedUrl == Constants.LOCAL_PODCAST_FEED_URL ||
                (!audioUrl.startsWith("http") && episodeWebLink == null)

    val progress: Float
        get() = if (duration > 0) {
            lastPlayedPosition.toFloat() / duration.seconds.inWholeMilliseconds.toFloat().coerceAtLeast(1f)
        } else 0f

    /**
     * Resolves the playable URI by checking whether [localFilePath] exists on disk.
     * Performs file I/O; call only from a background/IO dispatcher.
     */
    fun resolvePlayableUri(): String =
        localFilePath?.takeIf { File(it).exists() } ?: audioUrl
}
