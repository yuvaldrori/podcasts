package com.yuval.podcasts.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "queue")
data class QueueState(
    @PrimaryKey
    val episodeId: String,
    val position: Int // Order in the queue
)