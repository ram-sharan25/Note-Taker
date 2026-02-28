package com.rrimal.notetaker.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rrimal.notetaker.data.repository.NoteRepository
import com.rrimal.notetaker.data.storage.NoteMetadata
import com.rrimal.notetaker.data.storage.StorageConfigManager
import com.rrimal.notetaker.data.storage.SubmitResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class InboxCaptureUiState(
    val title: String = "",
    val description: String = "",
    val isSubmitting: Boolean = false,
    val submitSuccess: Boolean = false,
    val submitQueued: Boolean = false,
    val submitError: String? = null,
    val inboxFilePath: String = "inbox.org"
)

@HiltViewModel
class InboxCaptureViewModel @Inject constructor(
    private val repository: NoteRepository,
    private val storageConfigManager: StorageConfigManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(InboxCaptureUiState())
    val uiState: StateFlow<InboxCaptureUiState> = _uiState.asStateFlow()

    init {
        observeInboxPath()
    }

    private fun observeInboxPath() {
        viewModelScope.launch {
            storageConfigManager.inboxFilePath.collect { path ->
                _uiState.update { it.copy(inboxFilePath = path) }
            }
        }
    }

    fun updateTitle(title: String) {
        _uiState.update { it.copy(title = title, submitError = null) }
    }

    fun updateDescription(description: String) {
        _uiState.update { it.copy(description = description, submitError = null) }
    }

    fun submit() {
        val title = _uiState.value.title.trim()
        if (title.isEmpty() || _uiState.value.isSubmitting) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, submitError = null) }

            val metadata = NoteMetadata(
                title = title,
                description = _uiState.value.description.trim()
            )

            val result = repository.submitNote("", metadata)

            result.onSuccess { submitResult ->
                when (submitResult) {
                    SubmitResult.SENT -> {
                        _uiState.update {
                            it.copy(
                                title = "",
                                description = "",
                                isSubmitting = false,
                                submitSuccess = true
                            )
                        }
                    }
                    SubmitResult.QUEUED -> {
                        _uiState.update {
                            it.copy(
                                title = "",
                                description = "",
                                isSubmitting = false,
                                submitQueued = true
                            )
                        }
                    }
                    SubmitResult.AUTH_FAILED -> {
                        _uiState.update {
                            it.copy(
                                isSubmitting = false,
                                submitError = "Session expired. Please disconnect and sign back in from Settings."
                            )
                        }
                    }
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isSubmitting = false,
                        submitError = error.message ?: "Failed to save entry"
                    )
                }
            }
        }
    }

    fun clearSubmitSuccess() {
        _uiState.update { it.copy(submitSuccess = false) }
    }

    fun clearSubmitQueued() {
        _uiState.update { it.copy(submitQueued = false) }
    }
}
