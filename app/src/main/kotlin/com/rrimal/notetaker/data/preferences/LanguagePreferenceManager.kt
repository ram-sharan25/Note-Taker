package com.rrimal.notetaker.data.preferences

import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages speech recognition language preferences.
 * Supports manual language switching between English and Nepali.
 *
 * See ADR 002: Nepali Language Support - Phase 1
 */
@Singleton
class LanguagePreferenceManager @Inject constructor(
    private val encryptedPrefs: SharedPreferences
) {
    private object Keys {
        const val SPEECH_LANGUAGE = "speech_language"
    }

    companion object {
        const val LANGUAGE_ENGLISH = "en-US"
        const val LANGUAGE_NEPALI = "ne"
        const val DEFAULT_LANGUAGE = LANGUAGE_ENGLISH
    }

    private val _currentLanguage = MutableStateFlow(getLanguage())
    val currentLanguage: StateFlow<String> = _currentLanguage.asStateFlow()

    /**
     * Get the currently selected language code.
     * Returns "en-US" if no preference is set.
     */
    fun getLanguage(): String {
        return encryptedPrefs.getString(Keys.SPEECH_LANGUAGE, DEFAULT_LANGUAGE) ?: DEFAULT_LANGUAGE
    }

    /**
     * Save the selected language code.
     * @param languageCode Language code (e.g., "en-US", "ne")
     */
    fun setLanguage(languageCode: String) {
        encryptedPrefs.edit().putString(Keys.SPEECH_LANGUAGE, languageCode).apply()
        _currentLanguage.value = languageCode
    }

    /**
     * Toggle between English and Nepali.
     * @return The new language code after toggling
     */
    fun toggleLanguage(): String {
        val newLanguage = if (getLanguage() == LANGUAGE_ENGLISH) {
            LANGUAGE_NEPALI
        } else {
            LANGUAGE_ENGLISH
        }
        setLanguage(newLanguage)
        return newLanguage
    }
}
