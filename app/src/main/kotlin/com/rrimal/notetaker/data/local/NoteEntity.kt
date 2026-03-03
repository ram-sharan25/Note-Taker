package com.rrimal.notetaker.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters

@Entity(tableName = "notes")
@TypeConverters(PropertiesConverter::class)
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val filename: String,              // "Brain/tasks.org"
    val headlineId: String,            // UUID for stable reference
    val level: Int,                    // Headline level (1 = *, 2 = **)
    val title: String,
    val todoState: String?,            // "TODO", "IN-PROGRESS", "DONE"
    val priority: String?,             // "A", "B", "C"
    val tags: String,                  // "work:urgent" (colon-separated)
    val body: String,
    val parentId: Long?,               // For nested headlines
    val position: Int,                 // Original position in file
    val lastModified: Long,            // For sync tracking
    val properties: Map<String, String> = emptyMap()  // Org-mode properties (ID, PHONE_STARTED, etc.)
)
