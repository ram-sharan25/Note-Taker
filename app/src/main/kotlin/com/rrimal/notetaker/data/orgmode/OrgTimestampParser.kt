package com.rrimal.notetaker.data.orgmode

import com.rrimal.notetaker.data.local.OrgTimestampEntity
import java.util.*

/**
 * Parser for org-mode timestamps
 */
class OrgTimestampParser {
    // Regex to match <2026-03-01 Sun 09:00 ++1d> or [2026-03-01 Sun 09:00 ++1d]
    // Also handles time ranges: <2026-03-01 Sun 09:00-10:30 ++1d> (captures start time only)
    // Group 1: < or [
    // Group 2: Year
    // Group 3: Month
    // Group 4: Day
    // Group 5: Hour (optional)
    // Group 6: Minute (optional)
    // Group 7: Repeater type (optional, e.g., +, ++, .+)
    // Group 8: Repeater value (optional)
    // Group 9: Repeater unit (optional, h, d, w, m, y)
    // Group 10: > or ]
    // Note: (?:-\d{2}:\d{2})? captures and ignores the end time of a range (e.g., -23:30)
    private val timestampRegex = Regex("""([<\[])(\d{4})-(\d{2})-(\d{2})(?:\s+[A-Za-z]+)?(?:\s+(\d{2}):(\d{2})(?:-\d{2}:\d{2})?)?(?:\s+([\.\+]{1,2})(\d+)([hdwmy]))?(?:\s+[-+]\d+[hdwmy])?([>\]])""")

    fun parse(raw: String?): OrgTimestampEntity? {
        if (raw == null) return null
        
        val match = timestampRegex.find(raw) ?: return null
        
        val isActive = match.groupValues[1] == "<"
        val year = match.groupValues[2].toInt()
        val month = match.groupValues[3].toInt()
        val day = match.groupValues[4].toInt()
        val hour = match.groupValues[5].takeIf { it.isNotBlank() }?.toInt()
        val minute = match.groupValues[6].takeIf { it.isNotBlank() }?.toInt()
        val repeaterType = match.groupValues[7].takeIf { it.isNotBlank() }
        val repeaterValue = match.groupValues[8].takeIf { it.isNotBlank() }?.toInt()
        val repeaterUnit = match.groupValues[9].takeIf { it.isNotBlank() }

        val calendar = Calendar.getInstance()
        calendar.set(Calendar.YEAR, year)
        calendar.set(Calendar.MONTH, month - 1)
        calendar.set(Calendar.DAY_OF_MONTH, day)
        calendar.set(Calendar.HOUR_OF_DAY, hour ?: 0)
        calendar.set(Calendar.MINUTE, minute ?: 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        
        return OrgTimestampEntity(
            string = raw,
            isActive = isActive,
            year = year,
            month = month,
            day = day,
            hour = hour,
            minute = minute,
            timestamp = calendar.timeInMillis,
            repeaterType = repeaterType,
            repeaterValue = repeaterValue,
            repeaterUnit = repeaterUnit
        )
    }
}
