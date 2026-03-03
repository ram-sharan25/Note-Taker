# ADR 002: Nepali Language Support with Transliteration

## Status

✅ **Implemented** - Phase 1 (Manual Language Switching) completed
Future Phases 2-3 subject to user demand and feasibility review

## Context

The note-taker app currently supports English voice dictation only. Users who speak Nepali want to capture notes in their native language. This requires two main capabilities:

1. **Speech Recognition**: Converting spoken Nepali to text
2. **Script Handling**: Deciding whether to save notes in Devanagari (नेपाली) or Romanized Latin script

### Current Implementation

The app uses Android's `SpeechRecognizer` API with `RecognizerIntent.ACTION_RECOGNIZE_SPEECH`. The language is not explicitly set, defaulting to the device's system language. See `SpeechRecognizerManager.kt:158-163`.

### User Needs

- **Nepali speakers** want to dictate notes in Nepali without switching system language
- **Emacs/org-mode users** may prefer Romanized Nepali for easier editing in plain text
- **Devanagari users** want proper Unicode support for native script
- **Bilingual users** may want to mix English and Nepali in the same note

## Decision

Implement **Nepali speech recognition with user-selectable output format** (Devanagari or Romanized) using a **phased approach**.

### Implementation Approach

**Phase 1: Manual Language Switching with Google RecognizerIntent**
- Use Android's existing `SpeechRecognizer` API with `RecognizerIntent.EXTRA_LANGUAGE`
- Add language toggle UI (Nepali 🇳🇵 / English 🇺🇸) next to microphone
- Support Nepali (ne) and English (en-US) with manual switching
- Store language preference in `EncryptedSharedPreferences`
- Remember last-used language as default

**Phase 2: Optional Transliteration**
- Use Android's built-in ICU library (`android.icu.text.Transliterator`) for Devanagari→Latin conversion
- Add toggle in Settings: "Romanize Nepali" (default: off, saves in Devanagari)
- Apply transliteration after speech recognition, before saving to org file

**Phase 3 (Future): Migration to Whisper for Offline + Auto-Detection**
- Integrate OpenAI Whisper for fully offline speech recognition
- Enable automatic language detection (eliminate manual switching)
- Offer both engines in Settings (users choose: Google or Whisper)
- Whisper provides better offline support and aligns with local-first philosophy

### Why This Phased Approach?

1. **Phase 1 is quick win**: Minimal code change, proven solution, free
2. **Phase 2 adds flexibility**: Users choose Devanagari or Romanized output
3. **Phase 3 future-proofs**: Whisper enables offline + auto-detection when needed

### Technical Details

**Speech Recognition (Phase 1)**
```kotlin
private fun createIntent(languageCode: String?): Intent {
    return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        languageCode?.let {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, it) // "ne" for Nepali
        }
    }
}
```

**Transliteration (Phase 2)**
```kotlin
// Requires API 24+, already min SDK for this app
import android.icu.text.Transliterator

fun transliterateToLatin(devanagariText: String): String {
    val transliterator = Transliterator.getInstance("Devanagari-Latin")
    return transliterator.transliterate(devanagariText)
}
```

## Alternatives Considered

### Option A: Devanagari Only (No Transliteration)
- **Pro:** Simpler implementation, preserves native script
- **Pro:** Emacs supports Devanagari natively, org-mode handles Unicode well
- **Pro:** No data loss from transliteration (one-to-one character mapping not guaranteed)
- **Con:** Users without Devanagari fonts or input methods may struggle
- **Con:** Some users prefer Romanized for cross-platform compatibility

**Decision:** Rejected as sole option. Offer both.

### Option B: Third-Party Transliteration Libraries
Examples:
- [devanagaritransliterate](https://github.com/Vyshantha/devanagaritransliterate) (IAST, Harvard-Kyoto, SLP-1, ISO-15919)
- [roman-nepali-transliteration](https://github.com/BaileyTrotter/roman-nepali-transliteration) (Roman→Devanagari)

- **Pro:** More control over transliteration scheme (ISO 15919, IAST, etc.)
- **Pro:** Can support custom mappings (e.g., Madan Puraskar keyboard layout)
- **Con:** Adds dependency and APK size
- **Con:** Maintenance burden (library updates, security)
- **Con:** Overkill when Android provides ICU built-in

**Decision:** Rejected for Phase 1. Consider if ICU's Devanagari-Latin proves insufficient.

### Option C: Cloud-Based Transliteration (Google Cloud Translation API)
- **Pro:** Highest accuracy, context-aware transliteration
- **Pro:** Supports multiple romanization schemes
- **Con:** Requires API key and internet connection
- **Con:** Privacy concerns (sending notes to Google)
- **Con:** Cost per character (breaks "local-first" philosophy)
- **Con:** Violates offline-first design (notes should save even offline)

**Decision:** Rejected. Incompatible with app's local-first architecture.

### Option D: Multiple Romanization Schemes
Offer user choice between:
- **ISO 15919** (international standard, diacritics: ā, ī, ū, ṛ, etc.)
- **IAST** (academic standard for Indic scripts)
- **Harvard-Kyoto** (ASCII-only, computer-friendly)
- **ITRANS** (popular in linguistic circles)

- **Pro:** Maximum flexibility for power users
- **Pro:** Emacs users may have strong preferences
- **Con:** Complex UI, overwhelming for casual users
- **Con:** Most users won't know the difference
- **Con:** ICU provides ISO 15919 by default (sufficient for most)

**Decision:** Deferred to future. Start with ICU's default (ISO 15919). Revisit if users request specific schemes.

### Option E: Automatic Language Detection
Use `RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE` to prefer Nepali but allow auto-detection.

- **Pro:** No manual switching for bilingual users
- **Pro:** Better UX for mixed English-Nepali notes
- **Con:** Android speech recognition doesn't reliably support this
- **Con:** May mis-recognize Nepali as Hindi (similar Devanagari script)
- **Con:** Unpredictable behavior, harder to debug

**Decision:** Rejected for Phase 1. Explicit language selection is more reliable. Revisit if Google improves auto-detection.

### Option F: Speech Recognition Engine Alternatives

A comprehensive evaluation of speech recognition engines was conducted to determine the best approach for Nepali support.

#### F1. Google RecognizerIntent (Current - Selected for Phase 1)

**Nepali Support:** ✅ Yes (ne)

**Pros:**
- Already integrated in the app (zero migration cost)
- Native Nepali support with good accuracy
- Free (no API costs)
- Works out of the box, no setup required
- Maintained by Google (regular updates)

**Cons:**
- Requires internet connection (cloud-based models)
- Privacy concerns (audio sent to Google servers)
- Cannot do automatic language detection (manual switching required)
- Limited to one language per session

**Decision:** **Selected for Phase 1** - Quick implementation, proven solution, minimal risk.

---

#### F2. OpenAI Whisper (Selected for Phase 3)

**Nepali Support:** ✅ Yes (99 languages including Nepali)

**Pros:**
- **Native Nepali support** with [fine-tuned models available](https://arxiv.org/abs/2411.12587)
- **Fully offline** - works without internet (aligns with local-first philosophy)
- **Automatic language detection** - can handle Nepali + English code-switching
- Open source (MIT license) - no vendor lock-in
- Active Android community with libraries: [whisper_android](https://github.com/vilassn/whisper_android), [WhisperInput](https://github.com/alex-vt/WhisperInput)
- Recent research shows fine-tuning improves Nepali WER (Word Error Rate) significantly
- Privacy-preserving (all processing on-device)

**Cons:**
- Large model size (~75MB for tiny model, ~1.5GB for large model)
- Resource-intensive - slower transcription on lower-end devices
- Integration requires more work than RecognizerIntent (custom JNI or TFLite)
- Higher battery consumption
- Users reported occasional accuracy issues for Nepali (solvable with fine-tuning)

**Decision:** **Deferred to Phase 3** - Best long-term solution for offline + auto-detection, but requires significant integration effort. Implement after Phase 1 proves demand.

---

#### F3. Vosk Offline Speech Recognition

**Nepali Support:** ❌ No (20+ languages, Nepali not included)

**Pros:**
- Lightweight models (~50MB per language)
- True offline support
- [Easy Android integration](https://alphacephei.com/vosk/android) via Maven
- Low resource usage (works on Raspberry Pi)
- Open source (Apache 2.0 license)

**Cons:**
- **No pre-trained Nepali model** - would require custom model training
- Smaller language selection compared to Whisper (20 vs 99 languages)
- Training custom model requires significant dataset and ML expertise

**Decision:** **Rejected** - No Nepali support out of the box. Not worth training custom model when Whisper already provides Nepali.

---

#### F4. Google Cloud Speech-to-Text API (Chirp 3)

**Nepali Support:** ✅ Yes with [Chirp 3 multilingual model](https://cloud.google.com/speech-to-text/docs/models/chirp-3)

**Pros:**
- **Automatic language detection** with code-switching support
- Highest accuracy for Nepali (cloud-based with latest models)
- Speaker diarization (identify multiple speakers)
- Handles noisy environments well

**Cons:**
- **Costs money** - pay per minute of audio (~$0.024/min for Chirp 3)
- Requires API key setup and billing account
- Always needs internet connection
- Privacy concerns (audio sent to Google Cloud)
- **Violates local-first philosophy** - cannot work offline

**Decision:** **Rejected** - Incompatible with app's local-first architecture. Ongoing costs unacceptable for personal tool.

---

#### F5. Mozilla DeepSpeech

**Nepali Support:** ❌ Project discontinued

**Status:** [Project archived in 2021](https://github.com/mozilla/DeepSpeech)

**Decision:** **Rejected** - No longer maintained, use Whisper instead.

---

#### F6. PocketSphinx (CMU Sphinx)

**Nepali Support:** ❌ No pre-trained models

**Pros:**
- Extremely lightweight (~10MB models)
- Very low resource usage
- Oldest open-source speech recognition (mature codebase)

**Cons:**
- No Nepali model available
- Lower accuracy compared to modern neural models
- Limited language support

**Decision:** **Rejected** - No Nepali support, outdated technology.

---

### Speech Recognition Engine Decision Matrix

| Engine | Nepali Support | Offline | Auto-Detect | Free | Effort | Phase |
|--------|---------------|---------|-------------|------|--------|-------|
| **Google RecognizerIntent** | ✅ Yes | ❌ No | ❌ No | ✅ Yes | Low | **Phase 1** ✅ |
| **Whisper** | ✅ Yes | ✅ Yes | ✅ Yes | ✅ Yes | High | **Phase 3** 🔮 |
| **Google Cloud API** | ✅ Yes | ❌ No | ✅ Yes | ❌ Paid | Medium | ❌ Rejected |
| **Vosk** | ❌ No | ✅ Yes | ❌ No | ✅ Yes | High | ❌ Rejected |
| **DeepSpeech** | ❌ Discontinued | - | - | - | - | ❌ Rejected |
| **PocketSphinx** | ❌ No | ✅ Yes | ❌ No | ✅ Yes | High | ❌ Rejected |

**Legend:**
- ✅ Supported/Yes
- ❌ Not supported/No
- 🔮 Future consideration

---

### Final Decision: Phased Approach

**Phase 1 (Now):** Google RecognizerIntent with manual language switching
**Phase 2 (Soon):** Add optional transliteration (Devanagari → Latin)
**Phase 3 (Future):** Migrate to Whisper for offline + automatic language detection

**Rationale:**
1. **Phase 1 minimizes risk** - leverages existing infrastructure, proven solution
2. **Whisper is best long-term** - offline, auto-detection, privacy-preserving
3. **Phased rollout allows validation** - prove demand before investing in Whisper integration
4. **Users can choose** - eventually offer both engines in Settings (Google for online, Whisper for offline)

## Consequences

### Positive

**Phase 1 (Manual Language Switching)**
- Native Nepali speakers can dictate notes in their language
- Leverages Google's existing Nepali speech recognition (proven, maintained)
- Minimal code change: one extra parameter in `SpeechRecognizerManager`
- No new dependencies, zero migration risk
- Quick implementation (estimated: 1-2 days)
- Org files with Devanagari are valid Unicode text (Emacs handles natively)
- Free (no API costs or subscriptions)
- Language toggle UI provides clear visual feedback

**Phase 2 (With Transliteration)**
- Romanized Nepali is easier to edit without Devanagari keyboard
- Cross-platform compatibility (terminals, SSH, older systems)
- Uses Android's built-in ICU (no external dependencies)
- Reversible: users can toggle transliteration on/off
- ISO 15919 is well-documented and standardized
- Preserves choice: users decide Devanagari vs Romanized per their workflow

**Phase 3 (Whisper Integration)**
- **Fully offline**: No internet required, works on airplane mode
- **Privacy-preserving**: All processing on-device, no data sent to cloud
- **Automatic language detection**: Eliminates manual switching
- **Mixed-language support**: Handle Nepali + English in same note
- **Local-first alignment**: Matches app's offline-first philosophy
- **No vendor lock-in**: Open source (MIT license), community-maintained
- **Better for rural areas**: Works without reliable internet connection

### Negative

**Phase 1 (Manual Language Switching)**
- Users must manually switch language before each dictation session
- Cannot mix English and Nepali in the same dictation (must finish one, switch, start another)
- Extra tap required to change language (friction in workflow)
- Emacs users without Devanagari font support may see mojibake (fixable with font config)
- Requires internet connection (Google's cloud-based models)
- Privacy concern: Audio sent to Google servers (some users may object)

**Phase 2 (Transliteration)**
- Transliteration is lossy: some Devanagari nuances may not map perfectly to Latin
- Diacritics (ā, ṭ, ṇ) may not render correctly in all terminals/editors
- Adds complexity: users must understand Devanagari vs Romanized choice
- Testing burden: need native Nepali speakers to validate accuracy
- Potential confusion: "Why does my Nepali look like English letters?"
- Romanization scheme (ISO 15919) may not match user's preferred scheme (e.g., IAST)

**Phase 3 (Whisper Integration)**
- Large APK size increase (~75MB minimum for tiny model, ~1.5GB for large)
- Higher battery consumption (on-device ML model inference)
- Slower transcription on low-end devices (may take 2-3x real-time)
- Integration complexity (JNI or TFLite setup, model download management)
- Development effort (estimated: 2-3 weeks for full integration + testing)
- Model update mechanism needed (how to deliver new Whisper versions?)
- Testing matrix expands (Google vs Whisper, various Android devices/versions)
- User confusion: "Which engine should I use?" (need clear guidance)

### Technical Constraints

**Phase 1 & 2 (Google RecognizerIntent + ICU Transliteration)**
- **Minimum API Level**: ICU Transliterator requires API 24+ (Android 7.0). Current app min SDK is 26, so ✅ not a blocker.
- **Device Requirements**: Nepali speech recognition requires Google Play Services with Speech Services (pre-installed on 95%+ Android devices). Users without it will see error: "Speech recognition not available for Nepali."
- **Network Dependency**: Google's speech recognition requires internet for cloud-based models. Offline mode may work if device has cached Nepali model (rare, not guaranteed).
- **Language Support**: Limited to languages supported by Google (currently 120+, includes Nepali ne).

**Phase 3 (Whisper Integration)**
- **Minimum API Level**: TensorFlow Lite requires API 19+, whisper.cpp works on API 21+. Current min SDK 26, so ✅ compatible.
- **Storage Requirements**:
  - Tiny model: ~75MB
  - Base model: ~150MB
  - Small model: ~500MB
  - Must check available storage before download
- **Memory Requirements**:
  - Tiny: ~200MB RAM
  - Base: ~500MB RAM
  - Small: ~1GB RAM
  - May crash on low-memory devices (< 2GB RAM) with large models
- **Performance**:
  - Tiny: ~0.5-1x real-time (fast)
  - Base: ~1-2x real-time (moderate)
  - Small: ~2-4x real-time (slow on older devices)
  - Recommend minimum: Snapdragon 660 or equivalent (2017+)
- **Battery Impact**: On-device ML inference consumes ~2-3x battery vs cloud API
- **Offline Capability**: ✅ Fully offline once model downloaded (no internet needed)
- **Update Mechanism**: Need strategy for model updates (new Whisper versions, fine-tuned models)

### Migration Path

**Phase 1: Manual Language Switching (MVP)**
1. Add language toggle UI next to microphone (🇳🇵 Nepali / 🇺🇸 English icons)
2. Modify `SpeechRecognizerManager.createIntent()` to accept language parameter
3. Store language preference in `EncryptedSharedPreferences`
4. Remember last-used language as default
5. Update UI to show selected language (e.g., "🎤 Recording in Nepali...")
6. Handle language switching during active recording (restart recognizer)

**Phase 2: Optional Transliteration**
1. Add "Romanize Nepali" toggle in Settings (visible only when Nepali selected)
2. Create `TransliterationManager.kt` wrapper around ICU Transliterator
3. Apply transliteration in `NoteViewModel` after speech recognition, before saving
4. Add unit tests with Nepali text samples (Devanagari → Latin verification)
5. Document ISO 15919 transliteration scheme in help/docs

**Phase 3: Whisper Integration (Future)**
1. Research: Evaluate Whisper model sizes vs accuracy trade-offs
   - Tiny model (~75MB): Fast, lower accuracy
   - Base model (~150MB): Balanced
   - Small model (~500MB): Better accuracy
2. Integrate Whisper Android library (choose: TFLite, whisper.cpp JNI, or WhisperInput)
3. Add Settings option: "Speech Engine" (Google / Whisper)
4. Implement offline model download flow (show size, progress bar)
5. Add automatic language detection toggle (Whisper-only feature)
6. Test battery impact and performance on low-end devices
7. Consider fine-tuning Whisper on Nepali dataset for better accuracy

**Phase 4: Advanced Features** (out of scope)
- Per-note language metadata (store in org-mode properties drawer)
- Mixed-language support within same note (Whisper auto-detection)
- Additional romanization schemes (IAST, Harvard-Kyoto) via plugin system
- Custom transliteration rules (user-defined mappings)
- Support for additional Indic languages (Hindi, Bengali, etc.)
- Cloud sync for Whisper fine-tuned models

### Open Questions

1. **Should transliteration be per-note or global setting?**
   - Recommendation: Global setting initially (simpler). Add per-note override later if needed.
   - Alternative: Store transliteration preference in org-mode properties drawer per note.

2. **How to handle mixed content (English words in Nepali speech)?**
   - Google's speech API typically handles this automatically (e.g., "मेरो meeting छ" → "मेरो meeting छ")
   - Transliteration should preserve Latin words as-is (detect script, skip Latin portions)
   - Whisper handles this natively with language detection.

3. **What if user's device doesn't support Nepali speech recognition?**
   - Show clear error: "Nepali speech recognition not available. Install Google app or switch to English."
   - Graceful fallback: offer manual text input
   - Future: Suggest installing Whisper-based offline model.

4. **Should we support other Indic languages (Hindi, Bengali, etc.)?**
   - Defer to user demand. Same architecture applies (just add language codes to UI).
   - Risk: Testing burden grows linearly with languages.
   - Whisper supports all Indic languages (simplifies future expansion).

5. **Which Whisper model size should we default to in Phase 3?**
   - Options: tiny (~75MB), base (~150MB), small (~500MB)
   - Recommendation: Let user choose based on device storage/performance
   - Provide clear comparison: "Tiny: Fast, 85% accuracy | Base: Balanced | Small: Slow, 95% accuracy"

6. **Should Phase 3 replace Google RecognizerIntent or offer both?**
   - Recommendation: Offer both as "Speech Engine" setting
   - Default: Google (familiar, reliable)
   - Advanced: Whisper (offline, auto-detection)
   - Let users switch based on their needs (online vs offline, privacy, accuracy)

7. **How to handle Whisper model updates?**
   - Option A: Bundle model in APK (increases app size)
   - Option B: Download on first run (better, but requires internet once)
   - Option C: Offer optional model updates in Settings (check for new versions)
   - Recommendation: Option B with fallback to Google if download fails.

8. **Should we fine-tune Whisper on Nepali dataset?**
   - Research shows [fine-tuning reduces WER significantly](https://arxiv.org/abs/2411.12587)
   - Requires: Nepali speech dataset, GPU training time, ML expertise
   - Recommendation: Start with pre-trained Whisper, offer fine-tuned model as optional download later
   - Could crowdsource training data from users (opt-in, privacy-preserving)

## References

### Speech Recognition APIs
- [Android RecognizerIntent Documentation](https://developer.android.com/reference/android/speech/RecognizerIntent) - EXTRA_LANGUAGE parameter
- [Google Cloud Speech-to-Text V2 Supported Languages](https://docs.cloud.google.com/speech-to-text/docs/speech-to-text-supported-languages) - Confirms Nepali (ne) support
- [Google Voice Typing Nepali Support](https://techlekh.com/google-voice-typing-supports-nepali-voice/) - Confirms Android Gboard supports Nepali
- [Google Cloud Chirp 3 Multilingual Model](https://cloud.google.com/speech-to-text/docs/models/chirp-3) - Latest model with auto-detection
- [Google Cloud Automatic Language Detection](https://cloud.google.com/speech-to-text/v2/docs/multiple-languages) - Multi-language transcription

### Whisper (OpenAI)
- [OpenAI Whisper Official Repository](https://github.com/openai/whisper) - Main project (99 languages, MIT license)
- [Whisper Fine-tuning on Nepali Language (Research Paper)](https://arxiv.org/abs/2411.12587) - Shows WER improvements for Nepali
- [Whisper Android (TensorFlow Lite)](https://github.com/vilassn/whisper_android) - Android implementation
- [WhisperInput (Offline Keyboard)](https://github.com/alex-vt/WhisperInput) - Offline voice input panel for Android
- [Whisper.cpp](https://github.com/ggerganov/whisper.cpp) - C++ implementation (useful for Android JNI)

### Alternative Engines
- [Vosk Offline Speech Recognition](https://alphacephei.com/vosk/) - Lightweight offline solution
- [Vosk Available Models](https://alphacephei.com/vosk/models) - 20+ languages (no Nepali)
- [Vosk Android Integration](https://alphacephei.com/vosk/android) - Maven library for Android
- [Mozilla DeepSpeech (Archived)](https://github.com/mozilla/DeepSpeech) - Discontinued project
- [Speech Recognition Open Source Comparison](https://qcall.ai/speech-to-text-open-source) - Comprehensive comparison of 21 projects

### Transliteration
- [Android ICU Transliterator](https://developer.android.com/reference/android/icu/text/Transliterator) - Built-in transliteration API
- [ICU Transforms Documentation](https://unicode-org.github.io/icu/userguide/transforms/general/) - Devanagari-Latin support
- [ISO 15919 Standard](https://en.wikipedia.org/wiki/ISO_15919) - International standard for Indic romanization
- [Devanagari Transliterate Library](https://github.com/Vyshantha/devanagaritransliterate) - IAST, Harvard-Kyoto, SLP-1, ISO-15919

### Emacs/Org-Mode Integration
- [Emacs Nepali Romanized Input Method](https://github.com/bishesh/emacs-nepali-romanized) - Emacs supports both Devanagari and romanized Nepali
- [Emacs International Support](https://www.gnu.org/software/emacs/manual/html_node/emacs/International.html) - Multi-language support in Emacs

## Decision Date

2026-02-28

## Decision Makers

[To be filled: Product owner, developers involved]

## Related ADRs

- ADR 001: PAT Over OAuth for Authentication (establishes precedent for simplicity over complexity)
