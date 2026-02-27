package com.yuval.podcasts.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "episodes")
data class Episode(
    @PrimaryKey
    val id: String, // Guid or url
    val podcastFeedUrl: String, // Foreign key
    val title: String,
    val description: String,
    val audioUrl: String,
    val imageUrl: String? = null,
    val pubDate: Long,
    val duration: Long,
    val downloadStatus: Int, // 0 = Not Downloaded, 1 = Downloading, 2 = Downloaded
    val localFilePath: String?,
    val isPlayed: Boolean = false,
    val lastPlayedPosition: Long = 0L
)