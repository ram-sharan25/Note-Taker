package com.rrimal.notetaker.data.storage

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.storageDataStore by preferencesDataStore(name = "storage_config")

/**
 * Manages storage configuration including storage mode, local folder URI,
 * and GitHub sync settings using DataStore.
 */
@Singleton
class StorageConfigManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val STORAGE_MODE = stringPreferencesKey("storage_mode")
        val LOCAL_FOLDER_URI = stringPreferencesKey("local_folder_uri")
        val SYNC_TO_GITHUB_ENABLED = booleanPreferencesKey("sync_to_github_enabled")
        val CAPTURE_FOLDER = stringPreferencesKey("capture_folder")
        val CAPTURE_MODE = stringPreferencesKey("capture_mode")
        val INBOX_FILE_PATH = stringPreferencesKey("inbox_file_path")
    }

    /**
     * Current storage mode (GitHub Markdown or Local Org Files)
     */
    val storageMode: Flow<StorageMode> = context.storageDataStore.data.map { prefs ->
        when (prefs[Keys.STORAGE_MODE]) {
            "local_org" -> StorageMode.LOCAL_ORG_FILES
            else -> StorageMode.GITHUB_MARKDOWN
        }
    }

    /**
     * URI of the selected local folder (for local org file storage)
     */
    val localFolderUri: Flow<String?> = context.storageDataStore.data.map { prefs ->
        prefs[Keys.LOCAL_FOLDER_URI]
    }

    /**
     * Whether to sync local org files to GitHub as backup
     */
    val syncToGitHubEnabled: Flow<Boolean> = context.storageDataStore.data.map { prefs ->
        prefs[Keys.SYNC_TO_GITHUB_ENABLED] ?: false
    }

    /**
     * The folder where captured notes are saved (default: "" for root)
     */
    val captureFolder: Flow<String> = context.storageDataStore.data.map { prefs ->
        prefs[Keys.CAPTURE_FOLDER] ?: ""
    }

    /**
     * Current capture mode (New File or Inbox Append)
     */
    val captureMode: Flow<CaptureMode> = context.storageDataStore.data.map { prefs ->
        when (prefs[Keys.CAPTURE_MODE]) {
            "inbox_append" -> CaptureMode.INBOX_APPEND
            else -> CaptureMode.NEW_FILE
        }
    }

    /**
     * Path to the inbox file (for inbox append mode)
     */
    val inboxFilePath: Flow<String> = context.storageDataStore.data.map { prefs ->
        prefs[Keys.INBOX_FILE_PATH] ?: "inbox.org"
    }

    /**
     * Set the storage mode
     */
    suspend fun setStorageMode(mode: StorageMode) {
        context.storageDataStore.edit { prefs ->
            prefs[Keys.STORAGE_MODE] = when (mode) {
                StorageMode.GITHUB_MARKDOWN -> "github_markdown"
                StorageMode.LOCAL_ORG_FILES -> "local_org"
            }
        }
    }

    /**
     * Set the local folder URI
     */
    suspend fun setLocalFolderUri(uri: String) {
        context.storageDataStore.edit { prefs ->
            prefs[Keys.LOCAL_FOLDER_URI] = uri
        }
    }

    /**
     * Enable or disable GitHub sync for local files
     */
    suspend fun setSyncToGitHubEnabled(enabled: Boolean) {
        context.storageDataStore.edit { prefs ->
            prefs[Keys.SYNC_TO_GITHUB_ENABLED] = enabled
        }
    }

    /**
     * Clear local folder URI (e.g., when permissions are revoked)
     */
    suspend fun clearLocalFolderUri() {
        context.storageDataStore.edit { prefs ->
            prefs.remove(Keys.LOCAL_FOLDER_URI)
        }
    }

    /**
     * Set the capture folder where notes are saved
     */
    suspend fun setCaptureFolder(folder: String) {
        context.storageDataStore.edit { prefs ->
            prefs[Keys.CAPTURE_FOLDER] = folder
        }
    }

    /**
     * Set the capture mode
     */
    suspend fun setCaptureMode(mode: CaptureMode) {
        context.storageDataStore.edit { prefs ->
            prefs[Keys.CAPTURE_MODE] = when (mode) {
                CaptureMode.NEW_FILE -> "new_file"
                CaptureMode.INBOX_APPEND -> "inbox_append"
            }
        }
    }

    /**
     * Set the inbox file path
     */
    suspend fun setInboxFilePath(path: String) {
        context.storageDataStore.edit { prefs ->
            prefs[Keys.INBOX_FILE_PATH] = path
        }
    }
}
