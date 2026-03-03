# Note Taker — Android App Requirements

## Overview

A minimal Android app for capturing notes (typed or voice-dictated via keyboard mic) and pushing them to a GitHub repository. Notes are processed by an LLM agent in the notes repo.

## System Context

The app is one piece of a three-part system:

1. **This app** — captures notes, pushes to GitHub
2. **notes repo** (`ram-sharan25/notes`) — stores raw and processed notes as markdown files and does LLM processing
3. **Processor** (separate, runs locally) — processes existing notes from signal messages

The app's only job is getting text into the `inbox/` folder of the notes repo.

## Core Flow

1. User long-presses side key (or opens app normally)
2. Note input screen appears immediately (over lock screen if triggered from keyguard)
3. App fetches current sticky topic from the repo and displays it at the top
4. User types a note (or dictates via keyboard mic button)
5. User taps submit
6. App creates a new file in `inbox/` via the GitHub API
7. Input field clears, brief success animation, user stays on the same screen ready for the next note
8. If launched from lock screen, pressing Back returns to the lock screen

## Screens

### 1. Note Input (Home)
The default and only landing screen. Always opens here.

- **Top bar**: current sticky topic (read-only), icon to navigate to Settings
- **Body**: single text input field, full height available
- **Bottom**: submit button
- On submit: push to GitHub, clear the field, show brief success feedback (animation/snackbar), stay on this screen
- On error: show error message (no network, auth failure, etc.)

### 2. Settings
Accessible from the top bar of the note input screen.

- GitHub account — sign in / disconnect
- Repository — shown read-only (disconnect to change)
- Digital assistant setup — two-step guide: (1) set as default assistant with role detection, (2) set side button to use digital assistant (Samsung `SideKeySettings` intent with fallback)
- Delete all data — wipe all local data (Room DB, DataStore, WorkManager jobs) with confirmation dialog

## Functional Requirements

### FR1: Note Input ✅
- Single text input field
- User types or uses the Android keyboard's built-in mic for voice-to-text
- No custom speech recognition — rely entirely on the keyboard
- Submit button to send the note
- On submit: clear field, brief success animation, stay on same screen

### FR2: Sticky Topic Display ✅
- On app open, fetch `.current_topic` from the configured repo via GitHub API
- Display the current topic at the top of the screen (read-only)
- If no topic is set, display "No topic set"
- Topic changes happen through note content (e.g., "new topic, Frankenstein"), processed by the LLM — the app does not need topic-setting UI

### FR3: Push to GitHub ✅
- On submit, create a new file in the `inbox/` directory of the configured repo
- Use the GitHub REST API (Contents API) to create the file
- **Filename**: ISO 8601 timestamp with local timezone (e.g., `2026-02-09T143200-0500.md`)
- **Content**: the raw note text, nothing else
- Handle errors gracefully (no network, auth failure, conflict)
- Show error feedback to user

### FR4: Submission History ✅
- After each successful push, save a local record: timestamp, first ~50 characters of note text, success/failure
- Display the last 5-10 submissions in a compact list on the note input screen (collapsible section below the input field)
- Stored locally (SharedPreferences or Room) — not fetched from GitHub
- Persists across app restarts

### FR5: Authentication & Configuration ✅
- **Primary: GitHub App OAuth** — "Sign in with GitHub" button launches the GitHub App installation flow:
  1. Chrome Custom Tab opens to GitHub's App install page
  2. User picks their GitHub account and selects a repository
  3. GitHub redirects through a Pages bounce page (`ram-sharan25.github.io/gitjot-oauth/callback`) back to the app via `notetaker://` custom scheme
  4. App exchanges the authorization code for an access token (PKCE-protected)
  5. App discovers the installed repo via `/user/installations` API
  6. Token stored in EncryptedSharedPreferences (Android Keystore-backed)
  7. Non-expiring user tokens — no refresh logic needed
- **Re-authentication after disconnect** — uses the standard OAuth authorize URL (not the install URL), since the GitHub App remains installed on the user's GitHub account. The app preserves the `installation_id` across disconnect to detect returning users. If the user uninstalled the app from GitHub, the stale installation is detected and the install flow is used instead. "Delete All Data" clears everything including the installation ID (factory reset).
- **Shared prerequisite** — Fork the template notes repo on GitHub (step 1, visible above both auth methods)
- **Fallback: Fine-grained PAT** — collapsible 4-step guided flow (AnimatedVisibility):
  1. Enter repository as `owner/repo` or paste full GitHub URL (auto-parsed)
  3. Generate a PAT with step-by-step instructions dialog
  4. Paste the token (password-masked with visibility toggle)
  5. App validates token via `GET /user` (401 → "token is invalid"), then validates repo via `GET /repos/{owner}/{repo}` (404 → "repo not found")
- Both auth types store tokens in EncryptedSharedPreferences, metadata in DataStore
- Settings screen shows "Connected via GitHub" or "Connected via Personal Access Token"
- "What am I agreeing to?" link on OAuth flow explains permissions in plain language (one-repo read/write only, revocable)
- Help `(?)` icons explain token security and repo format
- "Need help?" link at bottom opens a YouTube setup walkthrough video
- To change repo or rotate token: disconnect in settings and re-enter
- OAuth disconnect revokes the access token via GitHub API (best-effort, 5s timeout) and shows a confirmation dialog explaining Note Taker stays installed on GitHub
- Disconnect dialog warns about pending unsent notes (all auth types) in red error text; single "Disconnect" button (red) + "Cancel"
- "Manage on GitHub" button in Settings for OAuth users opens GitHub's installation settings
- Reactive 401/403 detection: note submit shows "Session expired" and preserves note text; browse shows "Session expired"; worker stops retrying
- Repo selection dialog when GitHub App has access to multiple repositories
- Two-tap OAuth hint: "Already installed Note Taker on GitHub? Tap again to continue."
- See `docs/github-app-oauth-implementation.md` for implementation plan

### FR6: Lock Screen Launch ✅
- App registers as an Android digital assistant via `VoiceInteractionService`
- Long-press side key launches note capture over the lock screen (no unlock required)
- Works from both locked and unlocked states
- See `APP-TRIGGER.md` for full implementation details

### FR7: Offline Note Queuing ✅
- Notes are always saved to a local Room queue before attempting GitHub push
- On success, note is removed from queue and recorded in submission history
- On failure (no network, API error), note stays queued and WorkManager schedules retry with network connectivity constraint
- On auth failure (401/403), worker marks note as `auth_failed` and stops retrying; note submit shows "Session expired" and preserves user's text
- Handles 422 conflict (duplicate filename) by appending suffix
- UI shows "Queued" animation on submit failure and pending count badge
- Count drops to 0 automatically when WorkManager succeeds in the background

### FR8: Browse Notes ✅
- Read-only repo browser accessible from the note input screen's top bar
- Fetches repo contents via GitHub REST API (Contents API)
- Displays folder/file tree — tap folder to navigate, tap file to view
- Renders markdown files via Markwon, non-markdown in monospace
- BackHandler for in-screen navigation (file → directory → parent → exit)
- From lock screen, browse icon triggers `requestDismissKeyguard()` → opens in MainActivity
- Empty state, error state with retry, loading indicator

### FR9: Voice-First Input ✅
- App auto-starts speech recognition when NoteInputScreen appears (on resume)
- Words stream into the text field in real time (partial → finalized segments)
- Continuous listening: no timeout, auto-restarts between speech segments
- Mode switching: tap text field → keyboard mode; tap mic button → voice mode; text preserved across switches
- Permission denied or SpeechRecognizer unavailable → falls back to keyboard-only mode
- Submit while listening: stops voice, submits, clears, restarts voice
- App backgrounded: ON_PAUSE stops recognizer, ON_RESUME restarts
- App always returns to NoteInputScreen when brought to foreground

### FR10: Local Org Files Storage ✅
- Full local file storage backend using Android Storage Access Framework (SAF)
- User selects any folder on their device (persistent permission via `takePersistableUriPermission`)
- Two independent capture methods:
  1. **Voice dictation / Quick notes** → Creates new `.org` files with timestamp naming (`YYYY-MM-DD'T'HHmmssZ.org`)
  2. **Inbox capture** → Appends TODO entries to a single inbox file
- Automatic mode detection based on metadata (no manual mode switching)
- Full subdirectory support for organizing notes (e.g., `Brain/`, `Work/Projects/`)
- File operations use DocumentsContract API for proper SAF integration
- Path parsing supports nested directories (`Brain/inbox.org`, `Work/todos.org`)
- Settings: Storage mode selection (GitHub Markdown vs Local Org Files), folder picker, capture folder configuration

### FR11: Inbox Capture (Org-Mode TODO) ✅
- Dedicated screen accessed via ✓ (AddTask) icon in top bar
- Two-field interface: **Title** (required), **Description** (optional)
- Appends all entries to a single configurable inbox file
- Org-mode format:
  - TODO state automatically added
  - CREATED property with Emacs-style timestamp: `[YYYY-MM-DD DDD HH:mm]`
  - Description formatted as bullet points (lines prefixed with `- `)
  - Standard preamble on file creation: `#+STARTUP`, `#+FILETAGS`, `#+PROPERTY`
- Configurable inbox file path in Settings (supports subdirectories)
- File read-create-update logic ensures single file append (no duplicates)
- Example output:
  ```org
  * What is the difference in file formats?
  :PROPERTIES:
  :CREATED: [2026-02-28 Fri 16:38]
  :END:
  - Why is binary file format so small?
  - Actually what goes inside a file formats?
  ```

### FR12: Improved Dictation Format ✅
- Dictation notes use structured org-mode format:
  - **First sentence** becomes the headline (max 200 chars)
  - **Remaining text** becomes the body content
- Smart sentence detection using `.`, `?`, `!` with proper spacing validation
- Fallback logic when no sentence ending found:
  - Multiple lines → first line as title, rest as body
  - Long single line (>80 chars) → first 77 chars + "..." as title, full content in body
  - Short single line → entire text as title, no body
- CREATED property uses ISO timestamp format
- Example output:
  ```org
  * This is a test note to see how it formats.
  :PROPERTIES:
  :CREATED: 2026-02-28T22:50:09.180119Z
  :END:
  The remaining content goes here as the body of the org headline.
  ```

### FR13: Nepali Language Support (Phase 1) ✅
- Manual language switching between English (en-US) and Nepali (ne)
- Language toggle UI with flag indicators (🇳🇵 / 🇺🇸)
- Language preference stored in EncryptedSharedPreferences and remembered across sessions
- Uses Android's `RecognizerIntent.EXTRA_LANGUAGE` parameter
- Language switching during active listening (auto-restarts recognizer)
- Devanagari script output (नेपाली) for Nepali speech
- Requires internet connection (Google's cloud-based speech recognition)
- Future phases: Optional transliteration (Phase 2), Whisper integration for offline + auto-detection (Phase 3)
- See `docs/adr/002-nepali-language-support.md` for full implementation plan

### FR14: High-Fidelity Org-Mode Viewer (Phase 1) ✅
- Browse screen renders `.org` files with structured, high-fidelity display
- **Visual hierarchy** with level-based color coding:
  - Level 1 (blue), Level 2 (green), Level 3 (yellow), Level 4 (red), continuing through 8 colors
  - Headline text size decreases by level (headlineSmall → titleLarge → titleMedium → bodyLarge)
- **Folding/unfolding** — tap any headline to expand or collapse its content
  - Folding state persisted per headline during session
  - Expand/collapse icons (chevron right/down) indicate current state
- **TODO state chips** — colored badges for task states:
  - TODO (red), DONE (green), IN-PROGRESS (yellow), WAITING (orange), CANCELLED (gray)
  - Custom TODO states supported with fallback styling
- **Priority badges** — displayed as `[#A]`, `[#B]`, `[#C]` in monospace font
- **Tags** — shown right-aligned in format `:tag1:tag2:` with monospace font
- **Planning lines** — SCHEDULED, DEADLINE, CLOSED with color coding:
  - SCHEDULED (green), DEADLINE (red), CLOSED (gray)
  - Monospace timestamp display
- **Property drawers** — collapsible `:PROPERTIES:...`:END:` sections
  - Tap to expand/collapse
  - Dimmed text styling to de-emphasize metadata
- **Body content** — plain text rendering (Phase 1)
  - Future: Inline markup parsing (`*bold*`, `/italic/`, `~code~`, `[[links]]`)
- **Recursive rendering** — full support for nested headlines at any depth
  - Indentation increases by 24dp per level
  - Children only shown when parent is expanded
- **Edit mode** — tap Edit button to switch to raw text editing
  - Plain TextField with monospace font
  - Save/Cancel buttons in top bar
- **Architecture** — AST-driven rendering using `OrgParser`
  - Compose-native (no WebView), LazyColumn for performance
  - Inspired by Orgro's modular design, adapted for Kotlin/Compose
- Future phases: Inline markup rendering (Phase 2A), editing features (Phase 2B), tables/blocks (Phase 3)
- See `docs/adr/004-high-fidelity-org-viewer.md` for full architecture and roadmap

### FR15: Org-Mode Agenda View (Phase 1-2) ✅
Database-centric agenda system inspired by Orgzly's architecture.

**Phase 1 Complete:**
- Normalized Room database schema (notes, timestamps, planning, file metadata)
- File sync with SHA-256 hash-based change detection
- Fast SQL queries for date ranges (indexed timestamps)
- Day-grouped list view with sticky headers
- Overdue items section
- TODO state badges and priority indicators
- Filter by status (TODO, IN-PROGRESS, WAITING, DONE, etc.)
- **Refresh button** — Manual database clear and full re-sync
  - Clears all agenda database data (notes, timestamps, metadata)
  - Forces full re-parse and re-sync from org files
  - Shows spinner during refresh
  - Guarantees fresh data after external edits (Emacs, Syncthing, etc.)
- Background sync via WorkManager (every 15 minutes)

**Phase 2 Complete:**
- Full recurring task support (org-mode repeaters: `+1d`, `++1w`, `.+1m`)
- In-memory expansion of recurring timestamps for visible date range
- Efficient "jump-ahead" logic for old recurring tasks
- TODO state management (tap to change state)
- State selection dialog with visual badges
- Configurable agenda range (1-30 days)
- Configurable TODO keywords in Settings

**Technical:**
- Orgzly-inspired architecture (see ADR 003)
- AST-driven parsing via `OrgParser` and `OrgTimestampParser`
- Write-back to org files preserves formatting
- Scales to thousands of notes (<10ms query time)

### FR16: Swipeable Navigation with Agenda-First Design ✅
Modern gesture-based navigation with Agenda as the home screen.

**Navigation Structure:**
- **HorizontalPager** with 3 swipeable screens:
  - Page 0 (Left): Dictation — Quick voice/text note capture
  - Page 1 (Center, Default): Agenda — Command center for scheduled tasks
  - Page 2 (Right): Inbox Capture — Structured TODO entry
- **Visual page indicators** at bottom with animated labels
- **Gesture-based**: Swipe left/right to navigate between screens
- **Lock screen unchanged**: Side button still launches dictation directly (no pager)

**Minimalist Inbox Capture Redesign:**
- Clean interface with mic icon (🎤) and "What needs to be done?" prompt
- Single-line title input with voice button (future: wire up to SpeechRecognizer)
- **Expandable sections** (animated with fadeIn/expandVertically):
  - "+ Details" — Optional description field (hidden by default)
  - "+ Schedule" — Quick scheduling (Today, Tomorrow, date picker) (future: wire up dates)
- Large "Add Task" button with visual states (Adding..., Added!, Queued)
- Auto-collapse expanded sections after successful submit
- Saves to configured inbox file as before

**Architecture Changes:**
- **MainScreen.kt** — New HorizontalPager container
- **NavGraph.kt** — Simplified to single MainRoute (replaces NoteRoute, AgendaRoute, InboxCaptureRoute)
- **TopicBar** — Added `showPagerNavigation` parameter (default: false) to hide Agenda/Inbox buttons in pager
- **AgendaScreen** — Removed back button (not needed in pager)
- **Browse/Settings** — Still accessible via top bar buttons

**Benefits:**
- Agenda-first design aligns with "view first, capture second" workflow
- Natural gesture-based navigation (muscle memory)
- Cleaner UI with removed redundant buttons
- More screen space for content
- Smooth native feel with animated transitions

**Implementation:**
- `initialPage = 1` (Agenda)
- Page indicators with animated size and opacity
- Current page label displayed
- Smooth swipe transitions between pages
- See `UX_REDESIGN_SUMMARY.md` for full details
- Database-centric agenda system for viewing scheduled and deadline items from local org files
- **Normalized Room Database** with 5 tables:
  - `notes` — Parsed headlines with title, TODO state, priority, tags, body, level, parent hierarchy
  - `org_timestamps` — Parsed timestamps with year/month/day/hour/minute, epoch milliseconds, repeater info
  - `note_planning` — Links notes to their SCHEDULED/DEADLINE/CLOSED timestamps
  - `file_metadata` — Sync state tracking (filename, lastSynced, lastModified, SHA-256 hash)
  - `todo_keywords_config` — User-configurable TODO state progression sequence
- **File Sync System**:
  - `AgendaRepository.syncFileToDatabase()` parses configured org files and inserts into database
  - SHA-256 hash-based change detection (skips re-parsing unchanged files)
  - Automatic cleanup of database entries for files removed from agenda configuration
  - Transaction-based sync ensures atomicity
  - Recursive headline parsing with parent-child relationships
- **Timestamp Parsing** via `OrgTimestampParser`:
  - Regex-based parser for org-mode timestamps (active `<>` vs inactive `[]`)
  - Extracts date components (year, month, day, hour, minute)
  - Parses repeater syntax (`++1d`, `.+1w`, `+1m`) with type, value, and unit
  - Converts to epoch milliseconds for fast SQL queries
- **Recurring Task Expansion**:
  - In-memory expansion for visible date range only (efficient for old recurring tasks)
  - Supports all org-mode repeater types: `++` (catch-up), `.+` (restart), `+` (cumulative)
  - Supports all time units: hour (h), day (d), week (w), month (m), year (y)
  - "Jump-ahead" logic skips past instances before start date
  - Example: `<2026-03-01 Sat 09:00 ++1d>` expands to daily 09:00 instances within configured range
- **Agenda UI** (AgendaScreen):
  - Bucketed day view with sticky headers using Compose `LazyColumn`
  - Three sections: Overdue (past due items), Today, Upcoming days
  - Each item shows: title, TODO state chip (colored), priority badge, timestamp label (SCHEDULED/DEADLINE), formatted time, tags
  - Pull-to-refresh for manual sync
  - Status filter chips to show only specific TODO states
  - Empty states with helpful messages
  - TODO state selection dialog (view-only in Phase 1-2)
- **Configuration** (Settings Screen):
  - "Agenda Configuration" card with three sections:
    - Agenda Files — Multi-line text field for file paths (one per line, e.g., `Brain/tasks.org`)
    - Time Period — Segmented button group: 1 Day, 3 Days, 7 Days, 1 Month
    - TODO Keywords — Configurable sequence like Emacs (e.g., `TODO IN-PROGRESS WAITING | DONE CANCELLED`)
  - "Sync Now" button for manual sync
  - All preferences stored in DataStore via `AgendaConfigManager`
- **Background Sync** via `OrgFileSyncWorker` (WorkManager)
- **Navigation**: Calendar icon in TopicBar navigates to agenda screen
- **Architecture**: Database-centric approach inspired by Orgzly (parse once, query many times)
  - Instant agenda loading (<10ms SQL query vs 200-500ms file parsing)
  - Scales to thousands of notes
  - Battery efficient (no repeated parsing)
- **Performance**:
  - Duplicate detection — tracks seen `(noteId, timestamp)` pairs (prevents showing same note twice when it has both SCHEDULED + DEADLINE)
  - Fast queries — `SELECT` with indexed epoch milliseconds timestamp range
  - Efficient expansion — "jump-ahead" logic skips thousands of past instances in constant time
- **TODO State Management** (partially implemented):
  - `AgendaRepository.updateTodoState()` and `cycleTodoState()` exist
  - File conflict detection — checks SHA-256 hash before writing, re-syncs if file changed externally
  - Recursive headline search by UUID-based ID property for stable references
  - **NOT YET WIRED TO UI** — Dialog is view-only (no update button)
- **Phases**:
  - ✅ Phase 1 (Database & Sync) — Complete
  - ✅ Phase 2 (Agenda UI & Navigation) — Complete
  - ⏳ Phase 3 (TODO State Editing) — Repository logic complete, UI not wired
  - 📋 Phase 4 (Lock Screen Notification) — Not Started
  - 📋 Phase 5 (Advanced Features) — Not Started (saved searches, widgets, bulk operations)
- See `docs/adr/003-agenda-view-with-orgzly-architecture.md` for full architecture documentation

### FR17: Pomodoro Timer ✅
Built-in Pomodoro timer tightly integrated with the agenda and TODO state system.

**Timer Start/Stop:**
- Start a Pomodoro session from the state-selection dialog: select `IN-PROGRESS` → "Start Pomodoro" button appears → tap to start
- Timer runs as an Android foreground service (`PomodoroTimerService`) with a persistent notification
- Stopping the timer (via Stop button or state change away from `IN-PROGRESS`) cancels the service

**Fullscreen Overlay:**
- When timer is active, a fullscreen overlay covers the `HorizontalPager`
- Green theme for focus sessions, blue theme for break sessions
- Large countdown display with circular progress arc
- Task info card at top shows task title, priority, and tags
- Pause/Resume and Stop buttons at the bottom
- Screen kept on (`KEEP_SCREEN_ON`) while overlay is visible
- Swiping between pager pages disabled while timer is active

**Minimize / Chip:**
- Chevron-down button (top-right of overlay) minimizes to a compact chip in the Agenda top bar
- Chip shows session emoji (🍅 focus / ☕ break) and live countdown
- Tapping the chip maximizes the overlay again

**Task Card Interaction:**
- Tapping the task info card at the top of the fullscreen overlay opens the state-selection dialog
- Dialog behavior when opened from the timer:
  - `DONE` or `CANCELLED` → stops the Pomodoro timer completely + updates task state
  - `WAITING`, `HOLD`, `TODO` → pauses the timer, updates task state
  - `IN-PROGRESS` → dismisses dialog (timer keeps running)
- "Start Pomodoro" button is hidden in this context (timer already running)

**Completion:**
- When timer runs to zero, a completion dialog appears (shown even if minimized)
- Actions: Start Break, Another Pomodoro, Mark Done, Cancel

**Architecture:**
- `PomodoroTimerService` — foreground service; broadcasts tick/pause/resume/stop/complete events
- `AgendaViewModel` — owns all Pomodoro state (`pomodoroState`, `isTimerMinimized`); listens to service broadcasts via `BroadcastReceiver`
- `PomodoroTimerScreen` — fullscreen overlay rendered in `MainScreen` above the `HorizontalPager`
- `StateSelectionDialog` — extracted to its own file (`ui/screens/agenda/StateSelectionDialog.kt`) so it can be used from both `AgendaScreen` and `MainScreen`
- See `ui/screens/pomodoro/` and `pomodoro/PomodoroTimerService.kt`

### Lock Screen Security ✅
Two-tier model (same pattern as the camera app):
1. **Quick capture (no auth)** — note input works over the lock screen
2. **Full app access (auth required)** — settings requires `requestDismissKeyguard()` to prompt for biometric/PIN

## Non-Functional Requirements

- **Platform**: Android only, Kotlin, Jetpack Compose
- **Min SDK**: API 29 (Android 10) — covers all Galaxy S21+ devices
- **Target device**: Samsung Galaxy S24 Ultra (SM-S928U1), Android 16, OneUI 8.0
- **Theme**: Dark mode only
- **Simplicity**: Minimal UI — this is a capture tool, not a note management app
- **Speed**: App should open and be ready to type within 1-2 seconds
- **Security**: HTTP logging disabled in release builds, ADB backup disabled, R8 minification enabled (M20 audit), tokens encrypted at rest via EncryptedSharedPreferences (M31)

## Out of Scope

- Note processing, cleaning, or organization (handled by the notes repo's LLM agent)
- Topic management UI (topics are set via note content)
- Smarter topic refresh (see `ROADMAP.md`)
- Web UI
- Multi-user support
- iOS

## Open Questions

(none currently)
