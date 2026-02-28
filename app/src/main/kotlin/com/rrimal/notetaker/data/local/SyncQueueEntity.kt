package com.rrimal.notetaker.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity for tracking local org files that need to be synced to GitHub as backup
 */
@Entity(tableName = "sync_queue")
data class SyncQueueEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val filename: String,
    val content: String,
    val createdAt: Long,
    val status: String = "pending"  // pending, syncing, synced, failed, auth_failed
)
