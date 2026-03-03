package com.rrimal.notetaker.data.local

import androidx.room.*

@Dao
interface NotePlanningDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(planning: NotePlanningEntity)

    @Query("SELECT * FROM note_planning WHERE noteId = :noteId")
    suspend fun getByNoteId(noteId: Long): NotePlanningEntity?
}

data class NoteWithPlanning(
    @Embedded val note: NoteEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "noteId"
    )
    val planning: NotePlanningEntity?
)

data class AgendaNoteQueryResult(
    @Embedded val note: NoteEntity,
    @ColumnInfo(name = "ts_timestamp") val timestamp: Long,
    @ColumnInfo(name = "ts_repeaterType") val repeaterType: String?,
    @ColumnInfo(name = "ts_repeaterValue") val repeaterValue: Int?,
    @ColumnInfo(name = "ts_repeaterUnit") val repeaterUnit: String?,
    @ColumnInfo(name = "ts_type") val timeType: String // "SCHEDULED" or "DEADLINE"
)
