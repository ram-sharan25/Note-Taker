package com.rrimal.notetaker.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * DAO for PomodoroHistoryEntity.
 * Provides access to Pomodoro session history.
 */
@Dao
interface PomodoroHistoryDao {
    /**
     * Insert a new Pomodoro history entry.
     */
    @Insert
    suspend fun insert(history: PomodoroHistoryEntity)
    
    /**
     * Get all Pomodoro history entries, ordered by most recent first.
     */
    @Query("SELECT * FROM pomodoro_history ORDER BY endTime DESC")
    fun getAll(): Flow<List<PomodoroHistoryEntity>>
    
    /**
     * Get all Pomodoro history entries for a specific task.
     */
    @Query("SELECT * FROM pomodoro_history WHERE taskId = :taskId ORDER BY endTime DESC")
    fun getByTaskId(taskId: Long): Flow<List<PomodoroHistoryEntity>>
    
    /**
     * Delete all Pomodoro history entries.
     * Used for data cleanup.
     */
    @Query("DELETE FROM pomodoro_history")
    suspend fun deleteAll()
}
