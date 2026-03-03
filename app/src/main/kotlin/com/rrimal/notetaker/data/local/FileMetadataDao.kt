package com.rrimal.notetaker.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface FileMetadataDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(metadata: FileMetadataEntity)

    @Query("SELECT * FROM file_metadata WHERE filename = :filename")
    suspend fun getByFilename(filename: String): FileMetadataEntity?

    @Query("SELECT * FROM file_metadata")
    suspend fun getAll(): List<FileMetadataEntity>

    @Query("DELETE FROM file_metadata WHERE filename = :filename")
    suspend fun deleteByFilename(filename: String)

    @Query("UPDATE file_metadata SET lastSynced = :lastSynced, lastModified = :lastModified, hash = :hash WHERE filename = :filename")
    suspend fun updateSyncInfo(filename: String, lastSynced: Long, lastModified: Long, hash: String)
}
