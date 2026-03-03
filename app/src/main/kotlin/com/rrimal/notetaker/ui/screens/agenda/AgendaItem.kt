package com.rrimal.notetaker.ui.screens.agenda

import java.time.LocalDate

sealed class AgendaItem(open val id: Long) {
    /**
     * Date divider (sticky header)
     */
    data class Day(
        override val id: Long,
        val date: LocalDate,
        val formattedDate: String  // "Mon, 25 Sep"
    ) : AgendaItem(id)

    /**
     * Individual agenda entry
     */
    data class Note(
        override val id: Long,
        val noteId: Long,
        val title: String,
        val todoState: String?,
        val priority: String?,         // "A", "B", "C" or null
        val timeType: TimeType,        // SCHEDULED or DEADLINE
        val timestamp: Long,
        val formattedTime: String?,    // "09:00" or null
        val isOverdue: Boolean = false,
        val tags: List<String> = emptyList(),
        val filename: String,          // "Brain/tasks.org"
        val headlineId: String = "",   // Org-mode ID property
        val properties: Map<String, String> = emptyMap()  // Org properties (ID, PHONE_STARTED, etc.)
    ) : AgendaItem(id)
}

enum class TimeType { SCHEDULED, DEADLINE }
