package com.rrimal.notetaker.ui.viewmodels

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rrimal.notetaker.data.preferences.TogglPreferencesManager
import com.rrimal.notetaker.data.repository.TogglRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TogglSettingsUiState(
    val isEnabled: Boolean = false,
    val isConfigured: Boolean = false,
    val workspaceId: Long? = null,
    val defaultProjectId: Long? = null,
    val defaultProjectName: String? = null,
    val isSyncing: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null
)

@HiltViewModel
class TogglSettingsViewModel @Inject constructor(
    private val togglPrefsManager: TogglPreferencesManager,
    private val togglRepository: TogglRepository
) : ViewModel() {
    
    companion object {
        private const val TAG = "TogglSettingsViewModel"
    }
    
    private val _uiState = MutableStateFlow(TogglSettingsUiState())
    val uiState: StateFlow<TogglSettingsUiState> = _uiState.asStateFlow()
    
    init {
        observeTogglConfig()
    }
    
    private fun observeTogglConfig() {
        viewModelScope.launch {
            combine(
                togglPrefsManager.isEnabled,
                togglPrefsManager.isConfigured,
                togglPrefsManager.workspaceId,
                togglPrefsManager.defaultProjectId,
                togglPrefsManager.defaultProjectName
            ) { enabled, configured, workspaceId, projectId, projectName ->
                TogglSettingsUiState(
                    isEnabled = enabled,
                    isConfigured = configured,
                    workspaceId = workspaceId,
                    defaultProjectId = projectId,
                    defaultProjectName = projectName
                )
            }.collect { state ->
                _uiState.update { it.copy(
                    isEnabled = state.isEnabled,
                    isConfigured = state.isConfigured,
                    workspaceId = state.workspaceId,
                    defaultProjectId = state.defaultProjectId,
                    defaultProjectName = state.defaultProjectName
                )}
            }
        }
    }
    
    /**
     * Save API token and validate by fetching user info
     */
    suspend fun saveApiToken(token: String): Boolean {
        if (token.isBlank()) {
            _uiState.update { it.copy(
                errorMessage = "API token cannot be empty",
                successMessage = null
            )}
            return false
        }
        
        _uiState.update { it.copy(
            isSyncing = true,
            errorMessage = null,
            successMessage = null
        )}
        
        try {
            // Save token first
            togglPrefsManager.saveApiToken(token)
            
            // Validate by fetching user data
            val result = togglRepository.fetchUserAndProjects()
            
            if (result.isSuccess) {
                val userData = result.getOrNull()!!
                Log.d(TAG, "Successfully connected to Toggl: ${userData.email}")
                
                _uiState.update { it.copy(
                    isSyncing = false,
                    successMessage = "Connected successfully! Found ${userData.projects?.size ?: 0} projects.",
                    errorMessage = null
                )}
                
                // Auto-enable after successful connection
                togglPrefsManager.setEnabled(true)
                
                // Clear success message after 3 seconds
                viewModelScope.launch {
                    kotlinx.coroutines.delay(3000)
                    _uiState.update { it.copy(successMessage = null) }
                }
                
                return true
            } else {
                val error = result.exceptionOrNull()
                Log.e(TAG, "Failed to validate Toggl token", error)
                
                // Clear invalid token
                togglPrefsManager.clearAll()
                
                _uiState.update { it.copy(
                    isSyncing = false,
                    errorMessage = "Invalid API token: ${error?.message ?: "Unknown error"}",
                    successMessage = null
                )}
                
                return false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error saving Toggl token", e)
            
            _uiState.update { it.copy(
                isSyncing = false,
                errorMessage = "Error: ${e.message}",
                successMessage = null
            )}
            
            return false
        }
    }
    
    /**
     * Enable/disable Toggl time tracking
     */
    suspend fun setEnabled(enabled: Boolean) {
        togglPrefsManager.setEnabled(enabled)
        
        _uiState.update { it.copy(
            successMessage = if (enabled) "Toggl time tracking enabled" else "Toggl time tracking disabled",
            errorMessage = null
        )}
        
        // Clear message after 2 seconds
        viewModelScope.launch {
            kotlinx.coroutines.delay(2000)
            _uiState.update { it.copy(successMessage = null) }
        }
    }
    
    /**
     * Sync projects from Toggl
     */
    suspend fun syncProjects() {
        _uiState.update { it.copy(
            isSyncing = true,
            errorMessage = null,
            successMessage = null
        )}
        
        try {
            val result = togglRepository.getActiveProjects()
            
            if (result.isSuccess) {
                val projects = result.getOrNull()!!
                Log.d(TAG, "Synced ${projects.size} active projects")
                
                _uiState.update { it.copy(
                    isSyncing = false,
                    successMessage = "Synced ${projects.size} projects",
                    errorMessage = null
                )}
                
                // Clear message after 3 seconds
                viewModelScope.launch {
                    kotlinx.coroutines.delay(3000)
                    _uiState.update { it.copy(successMessage = null) }
                }
            } else {
                val error = result.exceptionOrNull()
                Log.e(TAG, "Failed to sync projects", error)
                
                _uiState.update { it.copy(
                    isSyncing = false,
                    errorMessage = "Failed to sync projects: ${error?.message}",
                    successMessage = null
                )}
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing projects", e)
            
            _uiState.update { it.copy(
                isSyncing = false,
                errorMessage = "Error: ${e.message}",
                successMessage = null
            )}
        }
    }
    
    /**
     * Disconnect from Toggl (clear all configuration)
     */
    suspend fun disconnect() {
        togglPrefsManager.disconnect()
        
        _uiState.update { it.copy(
            successMessage = "Disconnected from Toggl",
            errorMessage = null
        )}
        
        // Clear message after 2 seconds
        viewModelScope.launch {
            kotlinx.coroutines.delay(2000)
            _uiState.update { it.copy(successMessage = null) }
        }
    }
}
