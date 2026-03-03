package com.rrimal.notetaker.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "todo_keywords_config")
data class TodoKeywordsConfigEntity(
    @PrimaryKey val id: Int = 0,   // Singleton (only one config)
    val sequence: String            // "TODO IN-PROGRESS WAITING | DONE CANCELLED"
) {
    private val parts: List<String> by lazy { sequence.split("|") }

    val activeTodos: List<String> by lazy {
        parts[0].trim().split(" ").filter { it.isNotBlank() }
    }

    val doneTodos: List<String> by lazy {
        parts.getOrNull(1)?.trim()?.split(" ")?.filter { it.isNotBlank() } ?: emptyList()
    }

    fun isDone(state: String?): Boolean =
        state != null && doneTodos.contains(state)

    fun cycleState(current: String?): String {
        val allStates = activeTodos + doneTodos
        return when {
            current == null -> activeTodos.firstOrNull() ?: "TODO"
            current in allStates -> {
                val currentIndex = allStates.indexOf(current)
                allStates[(currentIndex + 1) % allStates.size]
            }
            else -> activeTodos.firstOrNull() ?: "TODO"
        }
    }
}
