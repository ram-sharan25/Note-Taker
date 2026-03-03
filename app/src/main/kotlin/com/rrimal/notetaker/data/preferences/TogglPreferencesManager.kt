package com.rrimal.notetaker.data.preferences

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.togglDataStore by preferencesDataStore(name = "toggl_config")

/**
 * Manages Toggl Track configuration and credentials.
 * 
 * Uses EncryptedSharedPreferences for API token storage (Android Keystore-backed).
 * Uses DataStore for non-sensitive configuration.
 */
@Singleton
class TogglPreferencesManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val encryptedPrefs: SharedPreferences
) {
    private object DataStoreKeys {
        val WORKSPACE_ID = longPreferencesKey("toggl_workspace_id")
        val DEFAULT_PROJECT_ID = longPreferencesKey("toggl_default_project_id")
        val DEFAULT_PROJECT_NAME = stringPreferencesKey("toggl_default_project_name")
        val ENABLED = booleanPreferencesKey("toggl_enabled")
        val AUTO_START = booleanPreferencesKey("toggl_auto_start")
        val LAST_SYNC = longPreferencesKey("toggl_last_sync")
    }
    
    private object EncryptedKeys {
        const val API_TOKEN = "toggl_api_token"
    }
    
    // ============================================================================
    // Configuration Flows
    // ============================================================================
    
    val isEnabled: Flow<Boolean> = context.togglDataStore.data.map { prefs ->
        prefs[DataStoreKeys.ENABLED] == true
    }
    
    val apiToken: Flow<String?> = context.togglDataStore.data.map {
        // Trigger re-emission when other fields change
        encryptedPrefs.getString(EncryptedKeys.API_TOKEN, null)
    }
    
    val workspaceId: Flow<Long?> = context.togglDataStore.data.map { prefs ->
        prefs[DataStoreKeys.WORKSPACE_ID]
    }
    
    val defaultProjectId: Flow<Long?> = context.togglDataStore.data.map { prefs ->
        prefs[DataStoreKeys.DEFAULT_PROJECT_ID]
    }
    
    val defaultProjectName: Flow<String?> = context.togglDataStore.data.map { prefs ->
        prefs[DataStoreKeys.DEFAULT_PROJECT_NAME]
    }
    
    val autoStartEnabled: Flow<Boolean> = context.togglDataStore.data.map { prefs ->
        prefs[DataStoreKeys.AUTO_START] == true
    }
    
    val lastSyncTimestamp: Flow<Long?> = context.togglDataStore.data.map { prefs ->
        prefs[DataStoreKeys.LAST_SYNC]
    }
    
    val isConfigured: Flow<Boolean> = context.togglDataStore.data.map { prefs ->
        val hasToken = encryptedPrefs.getString(EncryptedKeys.API_TOKEN, null) != null
        val hasWorkspace = prefs[DataStoreKeys.WORKSPACE_ID] != null
        hasToken && hasWorkspace
    }
    
    // ============================================================================
    // Synchronous Getters (for use in background workers, coroutines)
    // ============================================================================
    
    fun getApiTokenSync(): String? {
        return encryptedPrefs.getString(EncryptedKeys.API_TOKEN, null)
    }
    
    fun getWorkspaceIdSync(): Long? {
        // Note: This is a blocking read from DataStore, use sparingly
        return null // Implement if needed for WorkManager
    }
    
    // ============================================================================
    // Configuration Setters
    // ============================================================================
    
    suspend fun saveApiToken(token: String) {
        encryptedPrefs.edit().putString(EncryptedKeys.API_TOKEN, token).apply()
        // Update DataStore to trigger Flow re-emission
        context.togglDataStore.edit { prefs ->
            prefs[DataStoreKeys.LAST_SYNC] = System.currentTimeMillis()
        }
    }
    
    suspend fun saveWorkspace(workspaceId: Long) {
        context.togglDataStore.edit { prefs ->
            prefs[DataStoreKeys.WORKSPACE_ID] = workspaceId
        }
    }
    
    suspend fun saveDefaultProject(projectId: Long, projectName: String) {
        context.togglDataStore.edit { prefs ->
            prefs[DataStoreKeys.DEFAULT_PROJECT_ID] = projectId
            prefs[DataStoreKeys.DEFAULT_PROJECT_NAME] = projectName
        }
    }
    
    suspend fun clearDefaultProject() {
        context.togglDataStore.edit { prefs ->
            prefs.remove(DataStoreKeys.DEFAULT_PROJECT_ID)
            prefs.remove(DataStoreKeys.DEFAULT_PROJECT_NAME)
        }
    }
    
    suspend fun setEnabled(enabled: Boolean) {
        context.togglDataStore.edit { prefs ->
            prefs[DataStoreKeys.ENABLED] = enabled
        }
    }
    
    suspend fun setAutoStart(enabled: Boolean) {
        context.togglDataStore.edit { prefs ->
            prefs[DataStoreKeys.AUTO_START] = enabled
        }
    }
    
    suspend fun updateLastSync() {
        context.togglDataStore.edit { prefs ->
            prefs[DataStoreKeys.LAST_SYNC] = System.currentTimeMillis()
        }
    }
    
    // ============================================================================
    // Clear Configuration
    // ============================================================================
    
    suspend fun clearAll() {
        encryptedPrefs.edit().remove(EncryptedKeys.API_TOKEN).apply()
        context.togglDataStore.edit { prefs ->
            prefs.clear()
        }
    }
    
    suspend fun disconnect() {
        clearAll()
    }
}
