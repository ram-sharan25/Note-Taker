package com.rrimal.notetaker.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.ForeignKey.Companion.CASCADE
import androidx.room.PrimaryKey

@Entity(
    tableName = "note_planning",
    foreignKeys = [
        ForeignKey(
            entity = NoteEntity::class,
            parentColumns = ["id"],
            childColumns = ["noteId"],
            onDelete = CASCADE
        ),
        ForeignKey(
            entity = OrgTimestampEntity::class,
            parentColumns = ["id"],
            childColumns = ["scheduledTimestampId"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = OrgTimestampEntity::class,
            parentColumns = ["id"],
            childColumns = ["deadlineTimestampId"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = OrgTimestampEntity::class,
            parentColumns = ["id"],
            childColumns = ["closedTimestampId"],
            onDelete = ForeignKey.SET_NULL
        )
    ]
)
data class NotePlanningEntity(
    @PrimaryKey val noteId: Long,
    val scheduledTimestampId: Long?,
    val deadlineTimestampId: Long?,
    val closedTimestampId: Long?
)
