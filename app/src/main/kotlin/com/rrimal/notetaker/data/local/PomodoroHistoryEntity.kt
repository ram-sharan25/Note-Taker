package com.rrimal.notetaker.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity for tracking completed Pomodoro sessions.
 * Records both focus sessions and break sessions.
 */
@Entity(tableName = "pomodoro_history")
data class PomodoroHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val taskId: Long, // References AgendaNoteEntity
    val taskTitle: String,
    val startTime: Long, // Unix timestamp (milliseconds)
    val endTime: Long, // Unix timestamp (milliseconds)
    val durationSeconds: Int, // Planned duration in seconds
    val isBreak: Boolean, // true for break, false for focus
    val completedSuccessfully: Boolean // false if user stopped/cancelled
)
