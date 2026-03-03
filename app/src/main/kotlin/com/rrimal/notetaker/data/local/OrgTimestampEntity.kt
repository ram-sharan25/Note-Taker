package com.rrimal.notetaker.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "org_timestamps")
data class OrgTimestampEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val string: String,                // "<2026-03-01 Fri 09:00 ++1d>"
    val isActive: Boolean,             // < > (active) vs [ ] (inactive)
    val year: Int,
    val month: Int,
    val day: Int,
    val hour: Int?,
    val minute: Int?,
    val timestamp: Long,               // Epoch milliseconds (for fast queries)

    // Repeater: ++1d (catch-up), .+1w (restart), +1m (cumulative)
    val repeaterType: String?,         // "++", ".+", "+"
    val repeaterValue: Int?,           // 1, 2, 3, etc.
    val repeaterUnit: String?          // "h", "d", "w", "m", "y"
)
