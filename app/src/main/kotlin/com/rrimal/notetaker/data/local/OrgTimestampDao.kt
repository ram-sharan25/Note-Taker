package com.rrimal.notetaker.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface OrgTimestampDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(timestamp: OrgTimestampEntity): Long

    @Query("""
        DELETE FROM org_timestamps
        WHERE id IN (
            SELECT scheduledTimestampId FROM note_planning
            WHERE noteId IN (SELECT id FROM notes WHERE filename = :filename)
            AND scheduledTimestampId IS NOT NULL

            UNION

            SELECT deadlineTimestampId FROM note_planning
            WHERE noteId IN (SELECT id FROM notes WHERE filename = :filename)
            AND deadlineTimestampId IS NOT NULL

            UNION

            SELECT closedTimestampId FROM note_planning
            WHERE noteId IN (SELECT id FROM notes WHERE filename = :filename)
            AND closedTimestampId IS NOT NULL
        )
    """)
    suspend fun deleteByFilename(filename: String)

    @Query("SELECT * FROM org_timestamps WHERE id = :id")
    suspend fun getById(id: Long): OrgTimestampEntity?
}
