package com.rrimal.notetaker.ui.viewmodels

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import com.rrimal.notetaker.data.auth.AuthManager
import com.rrimal.notetaker.data.auth.OAuthTokenExchanger
import com.rrimal.notetaker.data.local.PendingNoteDao
import com.rrimal.notetaker.data.local.SubmissionDao
import com.rrimal.notetaker.data.storage.StorageConfigManager
import com.rrimal.notetaker.data.storage.StorageMode
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

data class SettingsUiState(
    val username: String = "",
    val repoFullName: String = "",
    val isAssistantDefault: Boolean = false,
    val authType: String = "", // "pat", "oauth", or ""
    val isSigningOut: Boolean = false,
    val pendingCount: Int = 0,
    val installationId: String = "",
    val storageMode: StorageMode = StorageMode.GITHUB_MARKDOWN,
    val localFolderUri: String? = null,
    val syncToGitHubEnabled: Boolean = false,
    val inboxFilePath: String = "inbox.org"
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val authManager: AuthManager,
    private val oAuthTokenExchanger: OAuthTokenExchanger,
    private val encryptedPrefs: SharedPreferences,
    private val submissionDao: SubmissionDao,
    private val pendingNoteDao: PendingNoteDao,
    private val workManager: WorkManager,
    private val storageConfigManager: StorageConfigManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        observeAuth()
        observePendingCount()
        observeStorageConfig()
        checkAssistantRole()
    }

    private fun observeAuth() {
        viewModelScope.launch {
            combine(
                authManager.username,
                authManager.repoOwner,
                authManager.repoName,
                authManager.authType,
                authManager.installationId
            ) { username, owner, name, authType, installationId ->
                data class AuthInfo(val username: String?, val owner: String?, val name: String?, val authType: String?, val installationId: String?)
                AuthInfo(username, owner, name, authType, installationId)
            }.collect { info ->
                _uiState.update {
                    it.copy(
                        username = info.username ?: "",
                        repoFullName = if (info.owner != null && info.name != null) "${info.owner}/${info.name}" else "",
                        authType = info.authType ?: "",
                        installationId = info.installationId ?: ""
                    )
                }
            }
        }
    }

    private fun observePendingCount() {
        viewModelScope.launch {
            pendingNoteDao.getPendingCount().collect { count ->
                _uiState.update { it.copy(pendingCount = count) }
            }
        }
    }

    fun checkAssistantRole() {
        val roleManager = context.getSystemService(RoleManager::class.java)
        val isDefault = roleManager.isRoleHeld(RoleManager.ROLE_ASSISTANT)
        _uiState.update { it.copy(isAssistantDefault = isDefault) }
    }

    fun signOut(onComplete: () -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSigningOut = true) }
            revokeOAuthTokenIfNeeded()
            authManager.signOut()
            _uiState.update { it.copy(isSigningOut = false) }
            onComplete()
        }
    }

    fun clearAllData(onComplete: () -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSigningOut = true) }
            revokeOAuthTokenIfNeeded()
            workManager.cancelAllWork()
            pendingNoteDao.deleteAll()
            submissionDao.deleteAll()
            authManager.clearAllData()
            _uiState.update { it.copy(isSigningOut = false) }
            onComplete()
        }
    }

    private suspend fun revokeOAuthTokenIfNeeded() {
        val authType = authManager.authType.first() ?: return
        if (authType != "oauth") return
        val token = encryptedPrefs.getString("access_token", null) ?: return
        withTimeoutOrNull(5_000L) {
            oAuthTokenExchanger.revokeToken(token)
        }
    }

    private fun observeStorageConfig() {
        viewModelScope.launch {
            combine(
                storageConfigManager.storageMode,
                storageConfigManager.localFolderUri,
                storageConfigManager.syncToGitHubEnabled,
                storageConfigManager.inboxFilePath
            ) { mode, uri, syncEnabled, inboxFilePath ->
                data class StorageConfig(
                    val mode: StorageMode,
                    val uri: String?,
                    val syncEnabled: Boolean,
                    val inboxFilePath: String
                )
                StorageConfig(mode, uri, syncEnabled, inboxFilePath)
            }.collect { config ->
                _uiState.update {
                    it.copy(
                        storageMode = config.mode,
                        localFolderUri = config.uri,
                        syncToGitHubEnabled = config.syncEnabled,
                        inboxFilePath = config.inboxFilePath
                    )
                }
            }
        }
    }

    fun setStorageMode(mode: StorageMode) {
        viewModelScope.launch {
            storageConfigManager.setStorageMode(mode)
        }
    }

    fun onFolderSelected(uri: Uri) {
        viewModelScope.launch {
            // Request persistent permission
            val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(uri, takeFlags)

            // Save to config
            storageConfigManager.setLocalFolderUri(uri.toString())
        }
    }

    fun setSyncToGitHub(enabled: Boolean) {
        viewModelScope.launch {
            storageConfigManager.setSyncToGitHubEnabled(enabled)
        }
    }

    fun setInboxFilePath(path: String) {
        viewModelScope.launch {
            storageConfigManager.setInboxFilePath(path)
        }
    }
}
