package com.yuval.podcasts.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "podcasts")
data class Podcast(
    @PrimaryKey
    val feedUrl: String,
    val title: String,
    val description: String,
    val imageUrl: String,
    val website: String,
    @ColumnInfo(defaultValue = "NULL")
    val etag: String? = null,
    @ColumnInfo(defaultValue = "NULL")
    val lastModified: String? = null
)