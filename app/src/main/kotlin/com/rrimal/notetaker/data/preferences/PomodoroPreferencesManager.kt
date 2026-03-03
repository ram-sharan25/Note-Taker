package com.rrimal.notetaker.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.pomodoroDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "pomodoro_preferences"
)

/**
 * Manages Pomodoro timer preferences using DataStore.
 * 
 * Default values:
 * - Pomodoro duration: 25 minutes
 * - Short break: 5 minutes
 * - Long break: 10 minutes
 */
@Singleton
class PomodoroPreferencesManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dataStore = context.pomodoroDataStore

    companion object {
        private val POMODORO_DURATION_KEY = intPreferencesKey("pomodoro_duration")
        private val SHORT_BREAK_DURATION_KEY = intPreferencesKey("short_break_duration")
        private val LONG_BREAK_DURATION_KEY = intPreferencesKey("long_break_duration")

        // Default values in minutes
        const val DEFAULT_POMODORO_DURATION = 25
        const val DEFAULT_SHORT_BREAK_DURATION = 5
        const val DEFAULT_LONG_BREAK_DURATION = 10
    }

    /**
     * Flow of Pomodoro duration in minutes.
     */
    val pomodoroDuration: Flow<Int> = dataStore.data.map { preferences ->
        preferences[POMODORO_DURATION_KEY] ?: DEFAULT_POMODORO_DURATION
    }

    /**
     * Flow of short break duration in minutes.
     */
    val shortBreakDuration: Flow<Int> = dataStore.data.map { preferences ->
        preferences[SHORT_BREAK_DURATION_KEY] ?: DEFAULT_SHORT_BREAK_DURATION
    }

    /**
     * Flow of long break duration in minutes.
     */
    val longBreakDuration: Flow<Int> = dataStore.data.map { preferences ->
        preferences[LONG_BREAK_DURATION_KEY] ?: DEFAULT_LONG_BREAK_DURATION
    }

    /**
     * Set Pomodoro duration in minutes.
     * @param minutes Duration between 5 and 60 minutes.
     */
    suspend fun setPomodoroDuration(minutes: Int) {
        require(minutes in 5..60) { "Pomodoro duration must be between 5 and 60 minutes" }
        dataStore.edit { preferences ->
            preferences[POMODORO_DURATION_KEY] = minutes
        }
    }

    /**
     * Set short break duration in minutes.
     * @param minutes Duration between 1 and 15 minutes.
     */
    suspend fun setShortBreakDuration(minutes: Int) {
        require(minutes in 1..15) { "Short break duration must be between 1 and 15 minutes" }
        dataStore.edit { preferences ->
            preferences[SHORT_BREAK_DURATION_KEY] = minutes
        }
    }

    /**
     * Set long break duration in minutes.
     * @param minutes Duration between 5 and 30 minutes.
     */
    suspend fun setLongBreakDuration(minutes: Int) {
        require(minutes in 5..30) { "Long break duration must be between 5 and 30 minutes" }
        dataStore.edit { preferences ->
            preferences[LONG_BREAK_DURATION_KEY] = minutes
        }
    }
}
