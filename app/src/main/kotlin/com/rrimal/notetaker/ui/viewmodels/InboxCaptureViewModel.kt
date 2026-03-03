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

// CaptureType is defined in NoteViewModel.kt (same package)

import androidx.compose.ui.text.input.TextFieldValue // <-- ADD THIS AT TOP

data class InboxCaptureUiState(
    val title: String = "",
    val description: TextFieldValue = TextFieldValue(),
    val todoState: String = "TODO",
    val isSubmitting: Boolean = false,
    val submitSuccess: Boolean = false,
    val submitQueued: Boolean = false,
    val submitError: String? = null,
    val inboxFilePath: String = "phone_inbox/inbox/inbox.org",
    val detectedType: CaptureType = CaptureType.NOTE
)

@HiltViewModel
class InboxCaptureViewModel @Inject constructor(
    private val repository: NoteRepository,
    private val storageConfigManager: StorageConfigManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(InboxCaptureUiState())
    val uiState: StateFlow<InboxCaptureUiState> = _uiState.asStateFlow()

    fun updateTitle(title: String) {
        _uiState.update { it.copy(title = title, submitError = null) }
        detectCaptureType()
    }

    fun updateDescription(description: TextFieldValue) {
        _uiState.update { it.copy(description = description, submitError = null) }
        detectCaptureType()
    }

    private fun detectCaptureType() {
        val title = _uiState.value.title.trim()
        val description = _uiState.value.description.text.trim()
        val combinedText = if (description.isNotEmpty()) "$title\n$description" else title

        val trimmed = combinedText.trim()
        if (trimmed.isEmpty()) {
            _uiState.update { it.copy(detectedType = CaptureType.NOTE) }
            return
        }

        val lines = trimmed.split("\n")

        // Check for URLs first (single line starting with http/https)
        val urlPattern = Regex("^https?://\\S+$", RegexOption.IGNORE_CASE)
        if (lines.size == 1 && urlPattern.matches(trimmed)) {
            _uiState.update { it.copy(detectedType = CaptureType.LINK) }
            return
        }

        // Check for TODO patterns
        val todoKeywords = listOf("TODO", "TASK:", "@TODO", "DO:", "☐")
        val hasTodoPrefix = lines.any { line ->
            todoKeywords.any { keyword ->
                line.trim().startsWith(keyword, ignoreCase = true) ||
                line.trim().startsWith("* TODO", ignoreCase = true) ||
                line.trim().startsWith("- TODO", ignoreCase = true)
            }
        }
        if (hasTodoPrefix) {
            _uiState.update { it.copy(detectedType = CaptureType.TODO) }
            return
        }

        // Check for checklists (- [ ], * [ ], [ ], ☑, ☐)
        val checklistPattern = Regex("^\\s*(\\*\\s+|-\\s+|\\d+\\.\\s+|\\-\\s+)?(\\[\\s*\\]|[☑☐✓✗])\\s+", RegexOption.IGNORE_CASE)
        val checklistCount = lines.count { checklistPattern.containsMatchIn(it) }
        if (checklistCount >= 1 && checklistCount == lines.size) {
            _uiState.update { it.copy(detectedType = CaptureType.CHECKLIST) }
            return
        }

        // Check for bullet lists
        val bulletPattern = Regex("^\\s*(\\*\\s+|-\\s+|•\\s+|→\\s+|≫\\s+)", RegexOption.IGNORE_CASE)
        val bulletCount = lines.count { bulletPattern.containsMatchIn(it) }
        if (bulletCount >= 1 && bulletCount == lines.size) {
            _uiState.update { it.copy(detectedType = CaptureType.BULLET) }
            return
        }

        _uiState.update { it.copy(detectedType = CaptureType.NOTE) }
    }

    fun updateTodoState(state: String) {
        _uiState.update { it.copy(todoState = state, submitError = null) }
    }

    fun submit() {
        val title = _uiState.value.title.trim()
        if (title.isEmpty() || _uiState.value.isSubmitting) return

        val processedTitle = processCaptureText(title, _uiState.value.detectedType)
        val processedDescription = processDescription(_uiState.value.description.text.trim(), _uiState.value.detectedType)

        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, submitError = null) }

            val metadata = NoteMetadata(
                title = processedTitle,
                description = processedDescription,
                todoState = _uiState.value.todoState
            )

            val result = repository.submitNote("", metadata)

            result.onSuccess { submitResult ->
                when (submitResult) {
                    SubmitResult.SENT -> {
                        _uiState.update {
                            it.copy(
                                title = "",
                                description = TextFieldValue(),
                                todoState = "TODO",
                                isSubmitting = false,
                                submitSuccess = true,
                                detectedType = CaptureType.NOTE
                            )
                        }
                    }
                    SubmitResult.QUEUED -> {
                        _uiState.update {
                            it.copy(
                                title = "",
                                description = TextFieldValue(),
                                todoState = "TODO",
                                isSubmitting = false,
                                submitQueued = true,
                                detectedType = CaptureType.NOTE
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

    private fun processCaptureText(text: String, captureType: CaptureType): String {
        return when (captureType) {
            CaptureType.LINK -> {
                // Convert URL to org-mode link [[url][description]]
                "[[${text.trim()}]]"
            }
            CaptureType.CHECKLIST -> {
                // Convert to org-mode checkbox items
                val lines = text.split("\n")
                lines.map { line ->
                    val trimmed = line.trim()
                    when {
                        trimmed.startsWith("- [ ]") || trimmed.startsWith("* [ ]") -> trimmed
                        trimmed.startsWith("[ ]") -> "- $trimmed"
                        trimmed.matches(Regex("^[☑☐✓✗]\\s+.*")) -> "- [${if (trimmed.startsWith("☑") || trimmed.startsWith("✓")) "X" else " "}] ${trimmed.substring(1).trim()}"
                        else -> "- [ ] $trimmed"
                    }
                }.joinToString("\n")
            }
            CaptureType.BULLET -> {
                // Convert bullets to org-mode format
                val lines = text.split("\n")
                lines.map { line ->
                    val trimmed = line.trim()
                    when {
                        trimmed.startsWith("* ") || trimmed.startsWith("- ") -> trimmed
                        trimmed.startsWith("• ") || trimmed.startsWith("→ ") || trimmed.startsWith("≫ ") -> "- ${trimmed.substring(2)}"
                        trimmed.matches(Regex("^\\d+\\..*")) -> "- ${trimmed.substringAfter(".").trim()}"
                        else -> "- $trimmed"
                    }
                }.joinToString("\n")
            }
            CaptureType.TODO -> {
                // Clean up TODO prefix
                text.replace(Regex("^(TODO|TASK:|@TODO|☐)\\s*", RegexOption.IGNORE_CASE), "")
            }
            CaptureType.NOTE -> text
        }
    }

    private fun processDescription(text: String, captureType: CaptureType): String {
        if (text.isEmpty()) return text
        return when (captureType) {
            CaptureType.CHECKLIST -> {
                val lines = text.split("\n")
                lines.map { line ->
                    val trimmed = line.trim()
                    when {
                        trimmed.startsWith("- [ ]") || trimmed.startsWith("* [ ]") -> trimmed
                        trimmed.startsWith("[ ]") -> "- $trimmed"
                        trimmed.matches(Regex("^[☑☐✓✗]\\s+.*")) -> "- [${if (trimmed.startsWith("☑") || trimmed.startsWith("✓")) "X" else " "}] ${trimmed.substring(1).trim()}"
                        else -> "- [ ] $trimmed"
                    }
                }.joinToString("\n")
            }
            CaptureType.BULLET -> {
                val lines = text.split("\n")
                lines.map { line ->
                    val trimmed = line.trim()
                    when {
                        trimmed.startsWith("* ") || trimmed.startsWith("- ") -> trimmed
                        trimmed.startsWith("• ") || trimmed.startsWith("→ ") || trimmed.startsWith("≫ ") -> "- ${trimmed.substring(2)}"
                        trimmed.matches(Regex("^\\d+\\..*")) -> "- ${trimmed.substringAfter(".").trim()}"
                        else -> "- $trimmed"
                    }
                }.joinToString("\n")
            }
            else -> text
        }
    }

    fun clearSubmitSuccess() {
        _uiState.update { it.copy(submitSuccess = false) }
    }

    fun clearSubmitQueued() {
        _uiState.update { it.copy(submitQueued = false) }
    }
}
