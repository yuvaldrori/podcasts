package com.yuval.podcasts.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "chapters",
    foreignKeys = [
        ForeignKey(
            entity = Episode::class,
            parentColumns = ["id"],
            childColumns = ["episodeId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["episodeId"])
    ]
)
data class Chapter(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val episodeId: String,
    val title: String,
    val startTimeMs: Long,
    val url: String? = null,
    val imageUrl: String? = null
)
