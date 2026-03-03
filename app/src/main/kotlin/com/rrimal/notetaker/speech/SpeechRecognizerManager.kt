package com.rrimal.notetaker.speech

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ListeningState {
    IDLE, LISTENING, RESTARTING
}

class SpeechRecognizerManager(
    private val context: Context,
    private val onSegmentFinalized: (String) -> Unit,
    private val onError: (String) -> Unit,
    initialLanguage: String = "en-US"
) {
    private var recognizer: SpeechRecognizer? = null
    private val handler = Handler(Looper.getMainLooper())
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val focusRequest: AudioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )
        .setOnAudioFocusChangeListener { /* held for entire session */ }
        .build()

    private var currentLanguage: String = initialLanguage

    // Flag to track if voice input should be active - prevents accidental restarts
    private var isVoiceEnabled: Boolean = false

    private val _listeningState = MutableStateFlow(ListeningState.IDLE)
    val listeningState: StateFlow<ListeningState> = _listeningState.asStateFlow()

    private val _partialText = MutableStateFlow("")
    val partialText: StateFlow<String> = _partialText.asStateFlow()

    val isAvailable: Boolean
        get() = SpeechRecognizer.isRecognitionAvailable(context)

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            _listeningState.value = ListeningState.LISTENING
        }

        override fun onBeginningOfSpeech() {}

        override fun onRmsChanged(rmsdB: Float) {}

        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {}

        override fun onError(error: Int) {
            when (error) {
                SpeechRecognizer.ERROR_NO_MATCH,
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
                SpeechRecognizer.ERROR_SERVER_DISCONNECTED,
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> {
                    // Transient — restart to keep listening
                    _partialText.value = ""
                    restart()
                }
                else -> {
                    // Real error — stop and notify
                    _listeningState.value = ListeningState.IDLE
                    _partialText.value = ""
                    val message = when (error) {
                        SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                        SpeechRecognizer.ERROR_NETWORK -> "Network error"
                        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                        SpeechRecognizer.ERROR_SERVER -> "Server error"
                        SpeechRecognizer.ERROR_CLIENT -> "Client error"
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Missing audio permission"
                        else -> "Speech recognition error ($error)"
                    }
                    onError(message)
                }
            }
        }

        override fun onResults(results: Bundle?) {
            val text = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                ?.trim()
            if (!text.isNullOrEmpty()) {
                onSegmentFinalized(text)
            }
            _partialText.value = ""
            // Continue listening for next segment
            restart()
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val text = partialResults
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                ?.trim()
            _partialText.value = text ?: ""
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    fun start() {
        isVoiceEnabled = true
        if (!isAvailable) {
            onError("Speech recognition not available")
            return
        }
        audioManager.requestAudioFocus(focusRequest)
        createAndStart()
    }

    fun stop() {
        isVoiceEnabled = false
        _listeningState.value = ListeningState.IDLE
        _partialText.value = ""
        handler.removeCallbacksAndMessages(null)
        try {
            recognizer?.stopListening()
        } catch (_: Exception) {}
        audioManager.abandonAudioFocusRequest(focusRequest)
    }

    fun destroy() {
        stop()
        try {
            recognizer?.destroy()
        } catch (_: Exception) {}
        recognizer = null
    }

    private fun restart() {
        // Don't restart if voice was disabled while we were listening
        if (!isVoiceEnabled || _listeningState.value == ListeningState.IDLE) {
            _listeningState.value = ListeningState.IDLE
            return
        }
        _listeningState.value = ListeningState.RESTARTING
        try {
            recognizer?.destroy()
        } catch (_: Exception) {}
        recognizer = null
        handler.postDelayed({ createAndStart() }, 150)
    }

    private fun createAndStart() {
        // Don't start if voice was disabled
        if (!isVoiceEnabled) {
            _listeningState.value = ListeningState.IDLE
            return
        }
        recognizer = SpeechRecognizer.createSpeechRecognizer(context).also {
            it.setRecognitionListener(listener)
            it.startListening(createIntent())
        }
    }

    private fun createIntent(): Intent {
        Log.d("SpeechRecognizerManager", "Creating intent with language: $currentLanguage")
        return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, currentLanguage)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, currentLanguage)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 2000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
        }
    }

    /**
     * Change the speech recognition language.
     * If currently listening, restarts the recognizer with the new language.
     * @param languageCode Language code (e.g., "en-US", "ne-NP")
     */
    fun setLanguage(languageCode: String) {
        Log.d("SpeechRecognizerManager", "setLanguage: old=$currentLanguage, new=$languageCode, state=${_listeningState.value}")
        if (currentLanguage == languageCode) {
            Log.d("SpeechRecognizerManager", "Language unchanged, skipping")
            return
        }

        val wasListening = _listeningState.value != ListeningState.IDLE

        // Stop current recognizer
        if (wasListening) {
            Log.d("SpeechRecognizerManager", "Stopping recognizer for language change")
            try {
                recognizer?.stopListening()
                recognizer?.destroy()
            } catch (_: Exception) {}
            recognizer = null
        }

        // Update language
        currentLanguage = languageCode
        Log.d("SpeechRecognizerManager", "Language updated to: $currentLanguage")

        // Restart if was listening
        if (wasListening) {
            Log.d("SpeechRecognizerManager", "Restarting recognizer with new language")
            handler.postDelayed({ createAndStart() }, 150)
        }
    }
}
