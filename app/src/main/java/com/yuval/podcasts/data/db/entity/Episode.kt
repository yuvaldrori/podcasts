package com.yuval.podcasts.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo
import kotlin.time.Duration.Companion.seconds

enum class DownloadStatus(val value: Int) {
    NOT_DOWNLOADED(0),
    DOWNLOADING(1),
    DOWNLOADED(2);

    companion object {
        fun fromInt(value: Int) = values().find { it.value == value } ?: NOT_DOWNLOADED
    }
}

@Entity(
    tableName = "episodes",
    indices = [
        Index(value = ["id"], unique = true),
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
    val completedAt: Long? = null,
    @PrimaryKey(autoGenerate = true)
    val localId: Long = 0
) {
    val isLocal: Boolean
        get() = podcastFeedUrl == com.yuval.podcasts.data.Constants.LOCAL_PODCAST_FEED_URL ||
                (!audioUrl.startsWith("http") && episodeWebLink == null)

    val progress: Float
        get() = if (duration > 0) {
            lastPlayedPosition.toFloat() / duration.seconds.inWholeMilliseconds.toFloat().coerceAtLeast(1f)
        } else 0f

    val playableUri: String
        get() = localFilePath?.takeIf { java.io.File(it).exists() } ?: audioUrl
}
