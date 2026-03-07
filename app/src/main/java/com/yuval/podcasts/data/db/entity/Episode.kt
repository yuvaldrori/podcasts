package com.yuval.podcasts.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo

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
)