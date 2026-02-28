package com.rrimal.notetaker.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * DAO for sync queue operations
 */
@Dao
interface SyncQueueDao {
    /**
     * Insert a file into the sync queue
     */
    @Insert
    suspend fun insert(syncQueueEntity: SyncQueueEntity): Long

    /**
     * Get all pending sync items
     */
    @Query("SELECT * FROM sync_queue WHERE status = 'pending' ORDER BY createdAt ASC")
    suspend fun getAllPending(): List<SyncQueueEntity>

    /**
     * Get count of pending sync items
     */
    @Query("SELECT COUNT(*) FROM sync_queue WHERE status = 'pending'")
    fun getPendingCount(): Flow<Int>

    /**
     * Update sync item status
     */
    @Query("UPDATE sync_queue SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String)

    /**
     * Delete a sync item
     */
    @Query("DELETE FROM sync_queue WHERE id = :id")
    suspend fun delete(id: Long)

    /**
     * Delete all synced items (cleanup)
     */
    @Query("DELETE FROM sync_queue WHERE status = 'synced'")
    suspend fun deleteAllSynced()
}
