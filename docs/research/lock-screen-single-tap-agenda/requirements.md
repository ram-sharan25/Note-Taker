# Lock Screen Single Tap Agenda Launch - Requirements

**Date:** 2026-03-02

## User Requirements

### Goals
1. **Single tap power button** → Opens app with Agenda screen on lock screen
2. **Lock screen widget** → Also opens Agenda screen on lock screen
3. **Preserve existing**: Long-press power button still opens Dictation screen

### Constraints
- **Target device**: Samsung Galaxy S24 Ultra (Android 16, OneUI 8.0)
- **Min SDK**: API 29 (Android 10)
- Must work over lock screen (no unlock required)
- Keep existing VoiceInteractionService implementation for long-press

### Context
- App currently uses VoiceInteractionService for long-press side button → launches NoteCaptureActivity (Dictation)
- MainActivity has HorizontalPager with Agenda as default home screen (page 1)
- Need to differentiate between lock screen entry points (Dictation vs Agenda)

## Research Questions

1. **Power Button Single Tap**:
   - Is it possible to intercept single tap power button on Android?
   - What APIs or approaches exist (if any)?
   - Samsung-specific capabilities?
   - Limitations and tradeoffs?

2. **Lock Screen Widget**:
   - Android 16 lock screen widget support status
   - Implementation requirements (API, manifest, provider)
   - Samsung OneUI 8 compatibility
   - Interaction with lock screen (tap to launch intent)

3. **Architecture**:
   - How to route to different screens based on launch source?
   - Intent extras to differentiate lock screen entry points?
   - Should we create a separate Activity for Agenda lock screen launch?
