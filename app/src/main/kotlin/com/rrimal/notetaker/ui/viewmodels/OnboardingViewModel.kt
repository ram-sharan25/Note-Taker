package com.rrimal.notetaker.ui.viewmodels

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rrimal.notetaker.data.storage.LocalFileManager
import com.rrimal.notetaker.data.storage.StorageConfigManager
import com.rrimal.notetaker.data.storage.StorageMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val storageConfigManager: StorageConfigManager,
    private val localFileManager: LocalFileManager
) : ViewModel() {

    data class UiState(
        val step: OnboardingStep = OnboardingStep.MODE_SELECTION,
        val rootFolderUri: String? = null,
        val phoneInboxFolderUri: String? = null,
        val errorMessage: String? = null,
        val isCompleting: Boolean = false
    )

    enum class OnboardingStep {
        MODE_SELECTION,      // Show GitHub (disabled) and Local cards
        SELECT_ROOT_FOLDER,  // Folder picker for root/browse folder
        SELECT_INBOX_FOLDER  // Folder picker for phone inbox
    }

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    /**
     * User tapped GitHub card - show "coming soon" message
     */
    fun onGitHubCardTapped() {
        _uiState.update { 
            it.copy(errorMessage = "GitHub sync available in future update") 
        }
    }

    /**
     * User tapped Local Files card - start folder selection flow
     */
    fun selectLocalMode() {
        _uiState.update { 
            it.copy(
                step = OnboardingStep.SELECT_ROOT_FOLDER,
                errorMessage = null
            ) 
        }
    }

    /**
     * User selected root folder for browsing
     */
    fun onRootFolderSelected(uri: Uri) {
        _uiState.update { 
            it.copy(
                rootFolderUri = uri.toString(),
                step = OnboardingStep.SELECT_INBOX_FOLDER,
                errorMessage = null
            ) 
        }
    }

    /**
     * User selected phone inbox folder
     */
    fun onPhoneInboxFolderSelected(uri: Uri) {
        _uiState.update { 
            it.copy(
                phoneInboxFolderUri = uri.toString(),
                errorMessage = null
            ) 
        }
        // Auto-complete onboarding after both folders selected
        completeOnboarding()
    }

    /**
     * Folder picker was dismissed without selection - return to picker
     * (Auto-handled by not updating state)
     */
    fun onFolderPickerCancelled() {
        // Do nothing - stay on same step, user can retry
    }

    /**
     * Folder permission error - show error and return to picker
     */
    fun onFolderPermissionError(step: OnboardingStep) {
        _uiState.update {
            it.copy(
                errorMessage = "Unable to access folder. Please try again.",
                step = step // Return to the same step
            )
        }
    }

    /**
     * User pressed back button
     */
    fun onBackPressed() {
        _uiState.update {
            when (it.step) {
                OnboardingStep.SELECT_ROOT_FOLDER -> it.copy(
                    step = OnboardingStep.MODE_SELECTION,
                    rootFolderUri = null,
                    errorMessage = null
                )
                OnboardingStep.SELECT_INBOX_FOLDER -> it.copy(
                    step = OnboardingStep.SELECT_ROOT_FOLDER,
                    phoneInboxFolderUri = null,
                    errorMessage = null
                )
                OnboardingStep.MODE_SELECTION -> it // No-op, root screen
            }
        }
    }

    /**
     * Clear error message
     */
    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    /**
     * Complete onboarding: Save configuration and mark complete
     */
    private fun completeOnboarding() {
        val currentState = _uiState.value
        if (currentState.rootFolderUri == null || currentState.phoneInboxFolderUri == null) {
            return
        }

        _uiState.update { it.copy(isCompleting = true) }

        viewModelScope.launch {
            try {
                // Set storage mode to Local
                storageConfigManager.setStorageMode(StorageMode.LOCAL_ORG_FILES)
                
                // Save folder URIs
                storageConfigManager.setLocalFolderUri(currentState.rootFolderUri)
                storageConfigManager.setPhoneInboxFolderUri(currentState.phoneInboxFolderUri)
                
                // Persist folder permissions (take persistable URI permissions)
                localFileManager.requestPersistentPermission(Uri.parse(currentState.rootFolderUri))
                localFileManager.requestPersistentPermission(Uri.parse(currentState.phoneInboxFolderUri))
                
                // Create subdirectories in phone inbox if missing
                localFileManager.ensurePhoneInboxStructure().getOrThrow()
                
                // Mark onboarding complete
                storageConfigManager.markOnboardingComplete()
                
                // Navigation handled by NavGraph (observing onboardingComplete flow)
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        errorMessage = "Setup failed: ${e.message}. Please try again.",
                        isCompleting = false
                    ) 
                }
            }
        }
    }
}
