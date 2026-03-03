# Note Taker — Roadmap

## Completed (V2-V3)

### Offline Note Queuing ✅
Notes are queued locally in Room and retried via WorkManager when network is available. UI shows "Queued" animation and pending count badge.

### Browse Notes ✅
Read-only repo browser with directory listing, file viewer, and Markwon markdown rendering. Accessible from the top bar and from lock screen (with keyguard dismiss).

### GitHub App OAuth ✅
GitHub App OAuth as primary auth (M31). One-tap "Sign in with GitHub" installs the Note Taker GitHub App on a user-chosen repo. PKCE-protected flow, EncryptedSharedPreferences token storage, PAT as fallback. Token revocation on disconnect (M34).

### Local Org Files Storage ✅
Full local file storage backend using Android SAF. User picks any folder on device. Two independent capture methods: voice dictation creates timestamped .org files, inbox capture appends to single file. Subdirectory support (Brain/, Work/). Settings for storage mode and folder selection.

### Inbox Capture ✅
Dedicated TODO capture screen (✓ icon in top bar) with title + description fields. Appends to single configurable inbox file. Org-mode format with TODO state, Emacs-style timestamps, bullet-formatted descriptions, standard preamble. Supports subdirectories (Brain/inbox.org).

### Improved Dictation Format ✅
First sentence becomes org headline, rest becomes body. Smart sentence detection with fallbacks for various content patterns. Structured org-mode output with CREATED property.

### Nepali Language Support (Phase 1) ✅
Manual language switching between English and Nepali for speech recognition. Language toggle UI with flag indicators (🇳🇵 / 🇺🇸). Uses Google's RecognizerIntent with ne language code. Preference saved in EncryptedSharedPreferences. See ADR 002 for details.

### High-Fidelity Org-Mode Viewer (Phase 1) ✅
Browse screen renders .org files with structured display inspired by Orgro. Visual hierarchy with level-based colors, collapsible headlines, TODO state chips, priority badges, tags, planning lines (SCHEDULED/DEADLINE/CLOSED), collapsible property drawers. AST-driven Compose rendering using OrgParser. Future phases include inline markup (bold/italic/links), editing features, and advanced elements (tables/blocks). See ADR 004 for architecture.
### High-Fidelity Org-Mode Viewer (Phase 1) ✅
...
### Org-Mode Agenda View (Phase 1-2) ✅
Full agenda system for viewing scheduled/deadline items from local org files. Based on Orgzly's proven architecture.
- **Phase 1: Database & Sync** - Normalized Room schema, SHA-256 hashing, background sync worker.
- **Phase 2: Agenda UI** - Bucketed day view (Today, Upcoming, Overdue), recurring task expansion (`++1d`, etc.), sticky headers.

### Swipeable Navigation (v0.8.0) ✅
HorizontalPager with 3 screens: Dictation | Agenda (default) | Inbox Capture. Gesture-based navigation with visual page indicators at bottom. Agenda opens by default on app launch. Lock screen capture still launches dictation directly. See `UX_REDESIGN_SUMMARY.md` for details.

### Minimalist Inbox Capture (v0.8.0) ✅
Redesigned with clean, focused interface. Single title input with voice button (🎤). Expandable "+ Details" and "+ Schedule" sections (animated). Auto-collapse after submit. Large "Add Task" button with visual feedback.

### Toggl Track Integration ✅
Automatic time tracking via Toggl Track API v9. Mirrors Emacs `toggl.el`. Starts timer on `IN-PROGRESS`, stops on state change away. Manual project selection dialog embedded in state-selection flow. API token stored in EncryptedSharedPreferences.

### Pomodoro Timer (v0.9.0) ✅
Built-in Pomodoro timer integrated with the agenda and TODO state system.
- Fullscreen overlay (green = focus, blue = break) with circular progress arc and large countdown
- Start from state-selection dialog: select `IN-PROGRESS` → "Start Pomodoro"
- Minimize to live chip in Agenda top bar (🍅/☕ + countdown); tap chip to restore
- Tappable task card on overlay opens state-selection dialog:
  - DONE/CANCELLED → stop timer + update state
  - WAITING/HOLD/TODO → pause timer + update state
  - IN-PROGRESS → dismiss (timer keeps running)
- Completion dialog: Start Break, Another Pomodoro, Mark Done, Cancel
- `PomodoroTimerService` foreground service with broadcast events
- `AgendaViewModel` owns all timer state via `StateFlow`
- `StateSelectionDialog` extracted to own file for reuse across screens

## V3+ Features

### Org-Mode Agenda View (Remaining Phases)
**Phase 4: Lock Screen & Polish**
- Persistent notification showing next 3-5 agenda items
- "Mark Done" action button in notification
- Polish UI (animations, loading states, error handling)

**Phase 5: Advanced Features**
- Saved searches ("Overdue", "This week", "High priority")
- Agenda widget (home screen)
- Bulk operations (mark multiple done)
- Calendar view (month view with dots for scheduled days)

---

### Pomodoro Timer — Remaining Phases
**Phase 2: Settings UI**
- PomodoroSettingsCard in SettingsScreen (focus duration, break duration, long break after N pomodoros)
- Stored in DataStore via dedicated settings class

**Phase 3: Fullscreen Mode**
- Hide system bars (status bar + navigation bar) while overlay is active
- Restore on minimize or stop
- `WindowInsetsControllerCompat` in `MainActivity`

**Phase 4: History Tracking**
- Local Room table recording completed Pomodoro sessions (task, duration, timestamp)
- Summary view in agenda or settings showing daily/weekly totals

---

### Nepali Language Support - Phase 2 & 3
**Phase 2: Optional Transliteration**
- Use Android's ICU Transliterator for Devanagari→Latin conversion
- Settings toggle: "Romanize Nepali" (default: off, saves in Devanagari)
- ISO 15919 transliteration scheme
- Apply after speech recognition, before saving to org file

**Phase 3: Whisper Integration**
- OpenAI Whisper for fully offline speech recognition
- Automatic language detection (eliminate manual switching)
- Better offline support (local-first philosophy)
- Offer both engines in Settings (Google or Whisper)
- Estimated model size: 75MB (tiny) to 1.5GB (large)
- See ADR 002 for full implementation plan

### Multi-Repo Support
Ability to connect more than one GitHub repository and switch between them in-app. Currently requires disconnect/reconnect to change repos. Would need a repo switcher UI in Settings and changes to how AuthManager stores repo configuration.

### Donate / Tip Button
In-app option for users to support development. Could be a simple link to GitHub Sponsors, Buy Me a Coffee, or similar. No in-app purchases — just an external link.

### Smarter Topic Refresh
Currently the topic refreshes on app launch and after each note submission. This won't catch topic changes that happen between submissions (e.g., the LLM agent processes a "new topic" note while the app is sitting open). Need a better mechanism:

- Periodic polling (e.g., every 60s while the app is in the foreground)
- GitHub webhook via push notification (requires server infrastructure)
- ETag/If-None-Match on the Contents API to make polling cheap
