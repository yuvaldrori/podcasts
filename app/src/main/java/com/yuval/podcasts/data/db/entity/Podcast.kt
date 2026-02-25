package com.yuval.podcasts.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "podcasts")
data class Podcast(
    @PrimaryKey
    val feedUrl: String,
    val title: String,
    val description: String,
    val imageUrl: String,
    val website: String
)