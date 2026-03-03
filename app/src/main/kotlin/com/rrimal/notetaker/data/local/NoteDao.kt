package com.rrimal.notetaker.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(note: NoteEntity): Long

    @Update
    suspend fun update(note: NoteEntity)

    @Query("DELETE FROM notes WHERE filename = :filename")
    suspend fun deleteByFilename(filename: String)

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun getById(id: Long): NoteEntity?
    
    @Query("SELECT * FROM notes WHERE id = :noteId")
    suspend fun getNoteById(noteId: Long): NoteEntity?

    @Query("SELECT * FROM notes WHERE todoState = :state ORDER BY lastModified DESC")
    suspend fun getNotesByState(state: String): List<NoteEntity>

    @Query("SELECT * FROM notes WHERE filename = :filename AND headlineId = :headlineId")
    suspend fun getByFilenameAndHeadlineId(filename: String, headlineId: String): NoteEntity?

    @Query("UPDATE notes SET todoState = :newState WHERE id = :noteId")
    suspend fun updateState(noteId: Long, newState: String)

    @Query("UPDATE notes SET properties = :properties WHERE id = :noteId")
    suspend fun updateProperties(noteId: Long, properties: String)

    @Transaction
    @Query("""
        SELECT n.*,
               COALESCE(ts_sched.timestamp, ts_dead.timestamp, 0) as ts_timestamp,
               COALESCE(ts_sched.repeaterType, ts_dead.repeaterType) as ts_repeaterType,
               COALESCE(ts_sched.repeaterValue, ts_dead.repeaterValue) as ts_repeaterValue,
               COALESCE(ts_sched.repeaterUnit, ts_dead.repeaterUnit) as ts_repeaterUnit,
               CASE 
                   WHEN ts_sched.id IS NOT NULL THEN 'SCHEDULED'
                   WHEN ts_dead.id IS NOT NULL THEN 'DEADLINE'
                   ELSE 'NONE'
               END as ts_type
        FROM notes n
        LEFT JOIN note_planning np ON n.id = np.noteId
        LEFT JOIN org_timestamps ts_sched ON np.scheduledTimestampId = ts_sched.id
        LEFT JOIN org_timestamps ts_dead ON np.deadlineTimestampId = ts_dead.id
        WHERE n.filename IN (:agendaFiles)
        ORDER BY 
            CASE WHEN n.todoState IN ('IN-PROGRESS', 'HOLD', 'WAITING') THEN 0 ELSE 1 END,
            COALESCE(ts_sched.timestamp, ts_dead.timestamp, 9999999999999) ASC,
            n.title ASC
    """)
    fun getAgendaItems(agendaFiles: List<String>): Flow<List<AgendaNoteQueryResult>>

    @Transaction
    @Query("""
        SELECT n.*,
               COALESCE(ts_sched.timestamp, ts_dead.timestamp, 0) as ts_timestamp,
               COALESCE(ts_sched.repeaterType, ts_dead.repeaterType) as ts_repeaterType,
               COALESCE(ts_sched.repeaterValue, ts_dead.repeaterValue) as ts_repeaterValue,
               COALESCE(ts_sched.repeaterUnit, ts_dead.repeaterUnit) as ts_repeaterUnit,
               CASE 
                   WHEN ts_sched.id IS NOT NULL THEN 'SCHEDULED'
                   WHEN ts_dead.id IS NOT NULL THEN 'DEADLINE'
                   ELSE 'NONE'
               END as ts_type
        FROM notes n
        LEFT JOIN note_planning np ON n.id = np.noteId
        LEFT JOIN org_timestamps ts_sched ON np.scheduledTimestampId = ts_sched.id
        LEFT JOIN org_timestamps ts_dead ON np.deadlineTimestampId = ts_dead.id
        WHERE n.filename IN (:agendaFiles)
          AND n.todoState IN (:statusFilter)
        ORDER BY 
            CASE WHEN n.todoState IN ('IN-PROGRESS', 'HOLD', 'WAITING') THEN 0 ELSE 1 END,
            COALESCE(ts_sched.timestamp, ts_dead.timestamp, 9999999999999) ASC,
            n.title ASC
    """)
    fun getAgendaItemsFiltered(agendaFiles: List<String>, statusFilter: List<String>): Flow<List<AgendaNoteQueryResult>>
}
