package com.rrimal.notetaker.ui.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rrimal.notetaker.data.auth.AuthManager
import com.rrimal.notetaker.data.preferences.LanguagePreferenceManager
import com.rrimal.notetaker.data.repository.NoteRepository
import com.rrimal.notetaker.data.storage.StorageConfigManager
import com.rrimal.notetaker.data.storage.SubmitResult
import com.rrimal.notetaker.speech.ListeningState
import com.rrimal.notetaker.speech.SpeechRecognizerManager
import com.rrimal.notetaker.ui.components.SubmissionItem
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

enum class InputMode { VOICE, KEYBOARD }

enum class CaptureType {
    NOTE,       // Default - plain text
    LINK,       // URL/link
    CHECKLIST,  // Checkbox items
    BULLET,     // Bullet list
    TODO        // Todo/task
}

data class NoteUiState(
    val noteText: String = "",
    val topic: String? = null,
    val isTopicLoading: Boolean = false,
    val isSubmitting: Boolean = false,
    val submitSuccess: Boolean = false,
    val submitQueued: Boolean = false,
    val pendingCount: Int = 0,
    val submissions: List<SubmissionItem> = emptyList(),
    val submitError: String? = null,
    val inputMode: InputMode = InputMode.VOICE,
    val listeningState: ListeningState = ListeningState.IDLE,
    val speechAvailable: Boolean = false,
    val permissionGranted: Boolean = false,
    val currentLanguage: String = LanguagePreferenceManager.DEFAULT_LANGUAGE,
    val detectedType: CaptureType = CaptureType.NOTE
)

@HiltViewModel
class NoteViewModel @Inject constructor(
    private val repository: NoteRepository,
    private val authManager: AuthManager,
    private val storageConfigManager: StorageConfigManager,
    private val languagePreferenceManager: LanguagePreferenceManager,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(NoteUiState())
    private val _showOnboarding = MutableStateFlow(false)
    val showOnboarding: StateFlow<Boolean> = _showOnboarding.asStateFlow()
    val uiState: StateFlow<NoteUiState> = _uiState.asStateFlow()

    private val timeFormatter = DateTimeFormatter.ofPattern("h:mm a")

    // Accumulates finalized speech segments
    private var confirmedText: String = ""

    private val speechManager = SpeechRecognizerManager(
        context = context,
        onSegmentFinalized = { segment ->
            confirmedText = if (confirmedText.isEmpty()) segment else "$confirmedText $segment"
            _uiState.update { it.copy(noteText = confirmedText) }
        },
        onError = { message ->
            _uiState.update { it.copy(
                listeningState = ListeningState.IDLE,
                submitError = message
            ) }
        },
        initialLanguage = languagePreferenceManager.getLanguage()
    )

    init {
        _uiState.update { it.copy(
            speechAvailable = speechManager.isAvailable,
            currentLanguage = languagePreferenceManager.getLanguage()
        ) }
        observeSubmissions()
        observePendingCount()
        observeSpeechState()
        observeLanguage()
        fetchTopic()
        checkOnboarding()
    }

    private fun observeLanguage() {
        viewModelScope.launch {
            languagePreferenceManager.currentLanguage.collect { language ->
                _uiState.update { it.copy(currentLanguage = language) }
            }
        }
    }

    private fun checkOnboarding() {
        viewModelScope.launch {
            val shown = authManager.onboardingShown.first()
            if (!shown) {
                _showOnboarding.value = true
            }
        }
    }

    fun dismissOnboarding() {
        _showOnboarding.value = false
        viewModelScope.launch {
            authManager.markOnboardingShown()
        }
    }

    private fun observeSubmissions() {
        viewModelScope.launch {
            repository.recentSubmissions.collect { entities ->
                val items = entities.map { entity ->
                    val time = Instant.ofEpochMilli(entity.timestamp)
                        .atZone(ZoneId.systemDefault())
                        .format(timeFormatter)
                    SubmissionItem(
                        time = time,
                        preview = entity.preview,
                        success = entity.success
                    )
                }
                _uiState.update { it.copy(submissions = items) }
            }
        }
    }

    private fun observePendingCount() {
        viewModelScope.launch {
            repository.pendingCount.collect { count ->
                _uiState.update { it.copy(pendingCount = count) }
            }
        }
    }

    private fun observeSpeechState() {
        viewModelScope.launch {
            speechManager.listeningState.collect { state ->
                _uiState.update { it.copy(listeningState = state) }
            }
        }
        viewModelScope.launch {
            speechManager.partialText.collect { partial ->
                if (_uiState.value.inputMode == InputMode.VOICE) {
                    val display = if (confirmedText.isEmpty()) partial
                        else if (partial.isEmpty()) confirmedText
                        else "$confirmedText $partial"
                    _uiState.update { it.copy(noteText = display) }
                }
            }
        }
    }

    private fun fetchTopic() {
        viewModelScope.launch {
            _uiState.update { it.copy(isTopicLoading = true) }
            val topic = repository.fetchCurrentTopic()
            _uiState.update { it.copy(topic = topic, isTopicLoading = false) }
        }
    }

    fun onPermissionResult(granted: Boolean) {
        _uiState.update { it.copy(permissionGranted = granted) }
        if (granted && _uiState.value.speechAvailable) {
            startVoiceInput()
        } else {
            _uiState.update { it.copy(inputMode = InputMode.KEYBOARD) }
        }
    }

    fun startVoiceInput() {
        if (!_uiState.value.permissionGranted || !_uiState.value.speechAvailable) return
        // Sync confirmedText from whatever is currently in noteText
        confirmedText = _uiState.value.noteText.trim()
        _uiState.update { it.copy(inputMode = InputMode.VOICE) }
        speechManager.start()
    }

    fun switchToKeyboard() {
        speechManager.stop()
        // Keep current noteText as-is for keyboard editing
        confirmedText = _uiState.value.noteText.trim()
        _uiState.update { it.copy(inputMode = InputMode.KEYBOARD) }
    }

    fun stopVoiceInput() {
        speechManager.stop()
    }

    /**
     * Start voice input only if the user is in VOICE mode
     * Used by MainScreen pager to resume voice when returning to Dictation page
     */
    fun startVoiceInputIfReady() {
        if (_uiState.value.inputMode == InputMode.VOICE &&
            _uiState.value.permissionGranted &&
            _uiState.value.speechAvailable) {
            speechManager.start()
        }
    }

    fun clearSubmitSuccess() {
        _uiState.update { it.copy(submitSuccess = false) }
    }

    fun clearSubmitQueued() {
        _uiState.update { it.copy(submitQueued = false) }
    }

    fun updateNoteText(text: String) {
        _uiState.update { it.copy(noteText = text, submitError = null) }
        if (_uiState.value.inputMode == InputMode.KEYBOARD) {
            confirmedText = text.trim()
        }
        detectCaptureType(text)
    }

    private fun detectCaptureType(text: String) {
        val trimmed = text.trim()
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

    private fun processCaptureText(text: String, captureType: CaptureType): String {
        val lines = text.split("\n")
        val currentTime = java.time.LocalDateTime.now()
            .format(java.time.format.DateTimeFormatter.ofPattern("[yyyy-MM-dd EEEE]"))

        return when (captureType) {
            CaptureType.NOTE -> {
                // For regular notes, use improved dictation format
                val firstSentence = extractFirstSentence(text)
                val body = text.removePrefix(firstSentence).trim()
                if (body.isNotEmpty()) {
                    "* $firstSentence\n$body"
                } else {
                    text
                }
            }
            CaptureType.LINK -> {
                // Convert URL to org-mode link [[url][description]]
                val description = lines.getOrNull(1)?.trim() ?: "Link"
                "[[${text.trim()}][$description]]"
            }
            CaptureType.CHECKLIST -> {
                // Ensure proper org-mode checkbox format
                lines.mapIndexed { index, line ->
                    val trimmed = line.trim()
                    when {
                        trimmed.startsWith("- [ ]") || trimmed.startsWith("* [ ]") -> trimmed
                        trimmed.startsWith("- [") || trimmed.startsWith("* [") -> trimmed
                        trimmed.startsWith("[ ]") -> "- $trimmed"
                        trimmed.startsWith("[") && !trimmed.startsWith("[[") -> "- $trimmed"
                        trimmed.matches(Regex("^[☑☐✓✗]\\s+.*")) -> "- [${if (trimmed.startsWith("☑") || trimmed.startsWith("✓")) "X" else " "}] ${trimmed.substring(1).trim()}"
                        else -> "- [ ] $trimmed"
                    }
                }.joinToString("\n")
            }
            CaptureType.BULLET -> {
                // Convert bullets to org-mode format
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
                // Convert to proper org-mode TODO
                lines.mapIndexed { index, line ->
                    val trimmed = line.trim()
                    when {
                        trimmed.startsWith("TODO", ignoreCase = true) || trimmed.startsWith("TASK:", ignoreCase = true) -> "* TODO ${trimmed.substringAfter(" ").substringAfter(":").trim()}"
                        trimmed.startsWith("@TODO", ignoreCase = true) -> "* TODO ${trimmed.substringAfter("@TODO").trim()}"
                        trimmed.startsWith("☐") -> "* TODO ${trimmed.substring(1).trim()}"
                        trimmed.startsWith("* TODO") || trimmed.startsWith("- TODO") -> trimmed
                        index == 0 -> "* TODO $trimmed"
                        else -> trimmed
                    }
                }.joinToString("\n")
            }
        }
    }

    private fun extractFirstSentence(text: String): String {
        val sentenceEnders = Regex("[.?!][\\s\n]")
        val match = sentenceEnders.find(text)
        return if (match != null) {
            val endIndex = match.range.last + 1
            val firstSentence = text.substring(0, endIndex).trim()
            if (firstSentence.length > 200) {
                text.substring(0, 200).substringBeforeLast(" ") + "..."
            } else {
                firstSentence
            }
        } else {
            val truncated = text.take(200)
            if (truncated.length < text.length) {
                truncated.substringBeforeLast(" ") + "..."
            } else {
                truncated
            }
        }
    }

    fun submit() {
        val text = _uiState.value.noteText.trim()
        if (text.isEmpty() || _uiState.value.isSubmitting) return

        val wasVoice = _uiState.value.inputMode == InputMode.VOICE
        speechManager.stop()

        val processedText = processCaptureText(text, _uiState.value.detectedType)

        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, submitError = null) }
            val result = repository.submitNote(processedText)

            result.onSuccess { submitResult ->
                when (submitResult) {
                    SubmitResult.SENT -> {
                        confirmedText = ""
                        fetchTopic()
                        _uiState.update {
                            it.copy(noteText = "", isSubmitting = false, submitSuccess = true)
                        }
                    }
                    SubmitResult.QUEUED -> {
                        confirmedText = ""
                        _uiState.update {
                            it.copy(noteText = "", isSubmitting = false, submitQueued = true)
                        }
                    }
                    SubmitResult.AUTH_FAILED -> {
                        // Preserve note text so user doesn't lose their work
                        _uiState.update {
                            it.copy(
                                isSubmitting = false,
                                submitError = "Session expired. Please disconnect and sign back in from Settings."
                            )
                        }
                        return@onSuccess // Don't restart voice
                    }
                }
                // Restart voice if we were in voice mode
                if (wasVoice && _uiState.value.permissionGranted && _uiState.value.speechAvailable) {
                    _uiState.update { it.copy(inputMode = InputMode.VOICE) }
                    speechManager.start()
                }
            }
        }
    }

    /**
     * Toggle between English and Nepali language.
     * Saves preference and updates the speech recognizer.
     */
    fun toggleLanguage() {
        val newLanguage = languagePreferenceManager.toggleLanguage()
        speechManager.setLanguage(newLanguage)
    }

    override fun onCleared() {
        super.onCleared()
        speechManager.destroy()
    }
}
