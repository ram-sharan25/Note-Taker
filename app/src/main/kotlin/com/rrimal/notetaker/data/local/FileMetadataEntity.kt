package com.rrimal.notetaker.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "file_metadata")
data class FileMetadataEntity(
    @PrimaryKey val filename: String,
    val lastSynced: Long,          // Epoch millis when last synced
    val lastModified: Long,        // File lastModified timestamp
    val hash: String               // SHA-256 hash for integrity check
)
