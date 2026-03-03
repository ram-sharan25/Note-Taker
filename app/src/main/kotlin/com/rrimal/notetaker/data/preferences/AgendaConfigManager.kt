package com.rrimal.notetaker.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.agendaDataStore by preferencesDataStore(name = "agenda_config")

enum class AgendaTimePeriod(val days: Int, val label: String) {
    ONE_DAY(1, "1 Day"),
    THREE_DAYS(3, "3 Days"),
    SEVEN_DAYS(7, "7 Days"),
    ONE_MONTH(30, "1 Month")
}

/**
 * Manages agenda configuration including agenda files, range, and TODO keywords.
 */
@Singleton
class AgendaConfigManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val AGENDA_FILES = stringPreferencesKey("agenda_files")
        val AGENDA_DAYS = intPreferencesKey("agenda_days")
        val TODO_KEYWORDS = stringPreferencesKey("todo_keywords")
        val TIME_PERIOD = stringPreferencesKey("time_period")
        val STATUS_FILTER = stringPreferencesKey("status_filter")
    }

    /**
     * List of files to include in the agenda (newline-separated)
     */
    val agendaFiles: Flow<List<String>> = context.agendaDataStore.data.map { prefs ->
        prefs[Keys.AGENDA_FILES]?.split("\n")?.filter { it.isNotBlank() } ?: emptyList()
    }

    /**
     * Raw string of agenda files
     */
    val agendaFilesRaw: Flow<String> = context.agendaDataStore.data.map { prefs ->
        prefs[Keys.AGENDA_FILES] ?: ""
    }

    /**
     * Number of days to show in the agenda
     */
    val agendaDays: Flow<Int> = context.agendaDataStore.data.map { prefs ->
        prefs[Keys.AGENDA_DAYS] ?: 7
    }

    /**
     * TODO keywords configuration string
     */
    val todoKeywords: Flow<String> = context.agendaDataStore.data.map { prefs ->
        prefs[Keys.TODO_KEYWORDS] ?: "TODO IN-PROGRESS WAITING HOLD | DONE CANCELLED"
    }

    /**
     * Selected time period for agenda view
     */
    val timePeriod: Flow<AgendaTimePeriod> = context.agendaDataStore.data.map { prefs ->
        val periodName = prefs[Keys.TIME_PERIOD] ?: AgendaTimePeriod.SEVEN_DAYS.name
        try {
            AgendaTimePeriod.valueOf(periodName)
        } catch (e: IllegalArgumentException) {
            AgendaTimePeriod.SEVEN_DAYS
        }
    }

    /**
     * Selected status filters (comma-separated, empty means show all)
     */
    val statusFilter: Flow<Set<String>> = context.agendaDataStore.data.map { prefs ->
        val filterString = prefs[Keys.STATUS_FILTER] ?: ""
        if (filterString.isBlank()) {
            emptySet()
        } else {
            filterString.split(",").filter { it.isNotBlank() }.toSet()
        }
    }

    suspend fun setAgendaFiles(files: String) {
        context.agendaDataStore.edit { prefs ->
            prefs[Keys.AGENDA_FILES] = files
        }
    }

    suspend fun setAgendaDays(days: Int) {
        context.agendaDataStore.edit { prefs ->
            prefs[Keys.AGENDA_DAYS] = days
        }
    }

    suspend fun setTodoKeywords(keywords: String) {
        context.agendaDataStore.edit { prefs ->
            prefs[Keys.TODO_KEYWORDS] = keywords
        }
    }

    suspend fun setTimePeriod(period: AgendaTimePeriod) {
        context.agendaDataStore.edit { prefs ->
            prefs[Keys.TIME_PERIOD] = period.name
        }
    }

    suspend fun setStatusFilter(statuses: Set<String>) {
        context.agendaDataStore.edit { prefs ->
            prefs[Keys.STATUS_FILTER] = statuses.joinToString(",")
        }
    }
}
