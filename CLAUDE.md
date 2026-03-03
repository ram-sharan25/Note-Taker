# CLAUDE.md - AI Assistant Context

This file provides context for AI assistants (Claude, ChatGPT, etc.) working on this project.

---

## Project Overview

**Name**: Note Taker (GitJot)
**Type**: Android mobile application
**Tech Stack**: Kotlin, Jetpack Compose, Android SDK, Room, WorkManager, Retrofit
**Purpose**: Voice-first note capture app that syncs to GitHub or local org-mode files
**Owner**: Ram Sharan Rimal (rimal.ram25@gmail.com)
**Target Device**: Samsung Galaxy S24 Ultra (Android 16, OneUI 8.0)
**Min SDK**: API 29 (Android 10)

**Current Version**: 0.8.0
**Next Version**: 0.9.0 (JSON Sync - In Planning)

---

## Important: Upcoming JSON Sync Implementation

**Status:** Planning Complete - Ready for Implementation

A major architectural change is planned for version 0.9.0 that will simplify the agenda system:

- **What's changing:** Mobile app will write state changes as JSON files instead of directly editing org files
- **Why:** Eliminates race conditions, simplifies codebase (~800 lines removed), makes Emacs the single writer
- **Documentation:** 
  - Full plan: `docs/JSON_SYNC_IMPLEMENTATION_PLAN.md`
  - Quick reference: `docs/JSON_SYNC_QUICK_REFERENCE.md`
  - Visual diagrams: `docs/JSON_SYNC_DIAGRAMS.md`

**Before implementing agenda changes, review the JSON sync documentation to understand the planned architecture.**

---

## System Architecture

Note Taker is one piece of a three-part system:

1. **This app** — Captures notes (voice/text), pushes to GitHub or local storage
2. **Notes repo** (`ram-sharan25/notes`) — Stores raw and processed notes as markdown/org files
3. **LLM Processor** (separate, runs locally) — Processes notes from signal messages

The app's only job is getting text into the `inbox/` folder of the notes repo or saving to local org files.

---

## Critical Rules

### **1. NEVER COMMIT CODE WITHOUT EXPLICIT USER REQUEST**

**CRITICAL**: Only create git commits when the user explicitly asks you to.

```bash
# ❌ FORBIDDEN - Never do this unless explicitly requested:
git add .
git commit -m "..."
git push

# ✅ ALLOWED when requested:
# Follow the git commit protocol in the system prompt
# - Run git status, git diff, git log first
# - Draft commit message
# - Stage specific files
# - Create commit with Co-Authored-By
# - Verify with git status
```

**Why**: Unauthorized commits are unhelpful and can result in lost work.

---

### **2. Read Documentation First**

Before making changes:
1. Read relevant docs in `docs/`
2. Understand the architecture
3. Follow established patterns
4. Check ADRs for key design decisions
5. Don't break existing functionality

---

## Project Structure

```
note-taker/
├── app/src/main/
│   ├── kotlin/com/rrimal/notetaker/
│   │   ├── ui/                      # Jetpack Compose UI
│   │   │   ├── screens/             # Main screens (NoteInputScreen, BrowseScreen, SettingsScreen)
│   │   │   │   ├── agenda/          # AgendaScreen, StateSelectionDialog
│   │   │   │   └── pomodoro/        # PomodoroTimerScreen, TaskInfoOverlay, PomodoroCompletionDialog
│   │   │   ├── viewmodels/          # ViewModels (NoteViewModel, BrowseViewModel, AgendaViewModel)
│   │   │   ├── orgview/             # Org-mode viewer components
│   │   │   └── theme/               # Compose theme
│   │   ├── data/                    # Data layer
│   │   │   ├── models/              # Data models
│   │   │   ├── api/                 # GitHub API (Retrofit)
│   │   │   ├── local/               # Room database
│   │   │   ├── preferences/         # DataStore & EncryptedSharedPreferences
│   │   │   └── repository/          # Repository pattern
│   │   ├── auth/                    # Authentication (OAuth, PAT)
│   │   ├── assist/                  # VoiceInteractionService (lock screen launch)
│   │   ├── speech/                  # SpeechRecognizerManager
│   │   ├── storage/                 # Local org files (SAF)
│   │   ├── pomodoro/                # PomodoroTimerService, PomodoroTimerState
│   │   └── workers/                 # WorkManager (offline retry)
│   └── res/                         # Android resources
├── docs/                            # Project documentation
│   ├── REQUIREMENTS.md              # Feature requirements
│   ├── ROADMAP.md                   # Future features
│   ├── DEPLOYMENT.md                # CI/CD & Play Store
│   ├── APP-TRIGGER.md               # Lock screen launch implementation
│   ├── PAT-SETUP.md                 # GitHub PAT guide
│   ├── adr/                         # Architecture Decision Records
│   │   ├── 001-pat-over-oauth.md    # PAT vs OAuth decision
│   │   ├── 002-nepali-language-support.md  # Nepali speech recognition
│   │   ├── 003-agenda-view-with-orgzly-architecture.md  # Agenda implementation plan
│   │   └── 004-high-fidelity-org-viewer.md  # Org viewer architecture
│   ├── research/                    # Research documentation
│   └── playstore/                   # Play Store assets
├── .github/workflows/deploy.yml     # CI/CD pipeline
├── app/build.gradle.kts             # Gradle build config
└── CLAUDE.md                        # This file
```

---

## Core Features

### FR1: Note Input ✅
- Single text input field
- Voice-first: Auto-starts speech recognition on screen appear
- Continuous listening with real-time streaming
- Mode switching: tap text field (keyboard) or tap mic (voice)
- Submit button to send note
- On submit: clear field, brief success animation, stay on same screen

### FR2: Sticky Topic Display ✅ (GitHub mode only)
- On app open, fetch `.current_topic` from configured repo via GitHub API
- Display current topic at top of screen (read-only)
- If no topic set, display "No topic set"
- Topic changes through note content (e.g., "new topic, Frankenstein"), processed by LLM

### FR3: Push to GitHub ✅
- On submit, create new file in `inbox/` directory of configured repo
- Use GitHub REST API (Contents API) to create file
- **Filename**: ISO 8601 timestamp with local timezone (e.g., `2026-02-09T143200-0500.md`)
- **Content**: raw note text, nothing else
- Handle errors gracefully (no network, auth failure, conflict)

### FR4: Submission History ✅
- After each successful push, save local record: timestamp, first ~50 chars of note text
- Display last 5-10 submissions in compact list on note input screen
- Stored locally (Room) — not fetched from GitHub
- Persists across app restarts

### FR5: Authentication & Configuration ✅
- **Primary: GitHub App OAuth** — "Sign in with GitHub" installs Note Taker GitHub App
- **Fallback: Fine-grained PAT** — Manual token generation on github.com
- Both auth types store tokens in EncryptedSharedPreferences
- Settings screen shows "Connected via GitHub" or "Connected via Personal Access Token"
- OAuth disconnect revokes access token via GitHub API (best-effort)
- See `docs/github-app-oauth-implementation.md` for details

### FR6: Lock Screen Launch ✅
- App registers as Android digital assistant via `VoiceInteractionService`
- Long-press side key launches note capture over lock screen (no unlock required)
- Works from locked and unlocked states
- See `docs/APP-TRIGGER.md` for full implementation details

### FR7: Offline Note Queuing ✅
- Notes always saved to local Room queue before GitHub push
- On success, note removed from queue and recorded in submission history
- On failure, note stays queued and WorkManager schedules retry with network constraint
- On auth failure (401/403), worker marks note as `auth_failed` and stops retrying
- Handles 422 conflict (duplicate filename) by appending suffix
- UI shows "Queued" animation and pending count badge

### FR8: Browse Notes ✅
- Read-only repo browser accessible from note input screen's top bar
- Fetches repo contents via GitHub REST API (Contents API)
- Displays folder/file tree — tap folder to navigate, tap file to view
- Renders markdown files via Markwon, non-markdown in monospace
- BackHandler for in-screen navigation (file → directory → parent → exit)

### FR9: Voice-First Input ✅
- App auto-starts speech recognition when NoteInputScreen appears
- Words stream into text field in real time (partial → finalized segments)
- Continuous listening: no timeout, auto-restarts between speech segments
- Mode switching: tap text field → keyboard mode; tap mic button → voice mode
- Permission denied or SpeechRecognizer unavailable → falls back to keyboard-only mode
- Submit while listening: stops voice, submits, clears, restarts voice

### FR10: Local Org Files Storage ✅
- Full local file storage backend using Android Storage Access Framework (SAF)
- **Two-tier folder selection**:
  1. **Root folder** - User selects via SAF (for browsing all org files)
  2. **Phone inbox folder** - User selects via SAF (contains standardized subdirectories)
- **Standardized phone inbox structure** (hardcoded subdirectories):
  ```
  phone_inbox/              # User-selected via SAF
  ├── dictations/           # Voice/quick notes (*.org files)
  ├── inbox/                # Inbox directory
  │   └── inbox.org        # TODO inbox entries
  ├── sync/                 # JSON state changes for Emacs
  └── agenda.org            # Emacs-generated agenda view
  ```
- Two independent capture methods:
  1. **Voice dictation / Quick notes** → Creates new `.org` files in `phone_inbox/dictations/`
  2. **Inbox capture** → Appends TODO entries to `phone_inbox/inbox/inbox.org`
- Automatic mode detection based on metadata (no manual mode switching)
- Full subdirectory support for organizing notes in root folder (e.g., `Brain/`, `Work/Projects/`)

### FR11: Inbox Capture (Org-Mode TODO) ✅
- Dedicated screen accessed via ✓ (AddTask) icon in top bar
- Two-field interface: **Title** (required), **Description** (optional)
- Appends all entries to `phone_inbox/inbox/inbox.org`
- Org-mode format: TODO state, CREATED property, bullet-formatted descriptions
- Example: `* TODO Buy groceries\n:PROPERTIES:\n:CREATED: [2026-02-28 Fri 16:38]\n:END:\n- Milk\n- Bread`

### FR12: Improved Dictation Format ✅
- Dictation notes use structured org-mode format
- **First sentence** becomes headline (max 200 chars)
- **Remaining text** becomes body content
- Smart sentence detection using `.`, `?`, `!` with proper spacing validation
- Fallback logic when no sentence ending found

### FR13: Nepali Language Support (Phase 1) ✅
- Manual language switching between English (en-US) and Nepali (ne-NP)
- Language toggle UI with flag indicators (🇳🇵 / 🇺🇸)
- Language preference stored in EncryptedSharedPreferences
- Uses Android's `RecognizerIntent.EXTRA_LANGUAGE` parameter
- Devanagari script output (नेपाली) for Nepali speech
- Requires internet connection (Google's cloud-based speech recognition)
- Future phases: Optional transliteration (Phase 2), Whisper integration (Phase 3)
- See `docs/adr/002-nepali-language-support.md` for full plan

### FR14: High-Fidelity Org-Mode Viewer (Phase 1) ✅
- Browse screen renders `.org` files with structured, beautiful display (instead of plain text)
- **Visual hierarchy**: Level-based color coding (blue → green → yellow → red for levels 1-4)
- **Collapsible headlines**: Tap to expand/collapse, chevron icons show state
- **TODO state chips**: Colored badges (TODO=red, DONE=green, IN-PROGRESS=yellow, etc.)
- **Priority badges**: Displayed as `[#A]`, `[#B]`, `[#C]` in monospace
- **Tags**: Right-aligned in `:tag1:tag2:` format
- **Planning lines**: SCHEDULED (green), DEADLINE (red), CLOSED (gray) with timestamps
- **Property drawers**: Collapsible `:PROPERTIES:...`:END:` sections
- **Nested headlines**: Full recursive rendering with indentation
- **Edit mode**: Tap Edit button to switch to raw text editing with monospace TextField
- Architecture: AST-driven Compose rendering using existing `OrgParser`
- Inspired by Orgro's viewer, optimized for Android/Compose
- Future phases: Inline markup (`*bold*`, `/italic/`, `[[links]]`), editing features, tables/blocks
- See `docs/adr/004-high-fidelity-org-viewer.md` for full architecture

### FR15: Swipeable Navigation with Agenda-First Design ✅
- **HorizontalPager** with 3 screens: Dictation | Agenda | Inbox Capture
- **Agenda as default home screen** - Opens on app launch
- **Gesture-based navigation**: Swipe left/right to switch screens
- **Visual page indicators**: Bottom indicators show current page with animated labels
- **Minimalist Inbox Capture**: Redesigned with expandable Details and Schedule sections
- **Cleaner UI**: Removed redundant navigation buttons, focus on primary actions per screen
- **Lock screen unchanged**: Side button still launches dictation directly (no pager)
- Architecture: Single MainRoute with HorizontalPager, simplified navigation graph
- See `UX_REDESIGN_SUMMARY.md` for full details

### FR16: Toggl Track Time Tracking ✅
- **Automatic timer start/stop** based on TODO state changes (mirrors Emacs `toggl.el`)
- **Start trigger**: Any state → `IN-PROGRESS` starts Toggl timer
- **Stop trigger**: `IN-PROGRESS` → Any other state stops Toggl timer
- **Task metadata**: Uses headline title as description, extracts `TOGGL_PROJECT_ID` property, sends org tags
- **Toggl Track API v9**: Full integration with start/stop endpoints
- **Settings UI**: API token configuration, enable/disable toggle, project sync
- **Secure storage**: API token in EncryptedSharedPreferences (Android Keystore-backed)
- **Non-blocking**: Toggl failures do not break TODO state changes
- **Error handling**: Graceful fallback with detailed logging
- See `docs/TOGGL-INTEGRATION.md` for full documentation

### FR17: Pomodoro Timer ✅
- **Foreground service**: `PomodoroTimerService` runs as a foreground service, broadcasts tick/paused/resumed/stopped/completed events
- **AgendaViewModel owns all state**: `pomodoroState: StateFlow<PomodoroTimerState?>`, `isTimerMinimized: StateFlow<Boolean>`, `togglProjects`; listens via `BroadcastReceiver`
- **Fullscreen overlay**: `PomodoroTimerScreen` rendered inside `MainScreen`'s root `Box` above the `HorizontalPager` — true fullscreen, not clipped by pager
- **Minimize/maximize**: Chevron-down button minimizes timer; tappable live chip (🍅/☕ + countdown) in `AgendaScreen` top bar restores it
- **Pager locked**: `userScrollEnabled = !isPomodoroActive`, page indicators hidden during active timer
- **Task info overlay**: `TaskInfoOverlay` inside `PomodoroTimerScreen` shows linked task; tapping it opens `StateSelectionDialog`
- **State dialog integration**: Tapping IN-PROGRESS in `StateSelectionDialog` shows Pomodoro prompt ("Start Pomodoro" / "Done"); `isPomodoroActive = true` hides the prompt when already running
- **Stop/pause on state change**: Changing task state away from IN-PROGRESS stops timer; WAITING/HOLD/TODO pauses it
- **Samsung OneUI fix**: All 5 broadcasts use `setPackage(packageName)` — required for `RECEIVER_NOT_EXPORTED`
- **`StateSelectionDialog` extracted**: Moved to `ui/screens/agenda/StateSelectionDialog.kt` (own file, same package) so both `AgendaScreen` and `MainScreen` can use it
- **`PomodoroTimerState.taskId`**: `Long?` (nullable) — null-checked before passing to dialog

### Lock Screen Security ✅
Two-tier model (same pattern as camera app):
1. **Quick capture (no auth)** — note input works over lock screen
2. **Full app access (auth required)** — settings requires `requestDismissKeyguard()` for biometric/PIN

---

## Tech Stack

### Core
- **Language**: Kotlin 2.1.0
- **UI Framework**: Jetpack Compose (Material 3)
- **Build Tool**: Gradle (Kotlin DSL)
- **Dependency Injection**: Hilt (Dagger)

### Android Components
- **Min SDK**: API 29 (Android 10)
- **Target SDK**: API 36 (Android 16)
- **Compile SDK**: API 36

### Key Libraries
- **Networking**: Retrofit + OkHttp + kotlinx.serialization
- **Local Database**: Room (note queue, submission history)
- **Background Work**: WorkManager (offline retry, network constraints)
- **Security**: EncryptedSharedPreferences (token storage, Android Keystore-backed)
- **Storage**: Storage Access Framework (local org files)
- **Markdown Rendering**: Markwon (browse screen)
- **Speech Recognition**: Android SpeechRecognizer API (Google's cloud-based)
- **Navigation**: Jetpack Navigation Compose
- **Lifecycle**: AndroidX Lifecycle (ViewModel, Compose integration)

### API Integration
- **GitHub REST API**: Contents API (file creation, repo browsing)
- **GitHub OAuth**: PKCE-protected flow
- **Toggl Track API v9**: Time entries (start/stop timers)
- **Authentication**: OAuth 2.0 (GitHub App) or Fine-grained PAT (GitHub), Basic Auth (Toggl)

---

## Key Design Decisions (ADRs)

### ADR 001: Fine-Grained PAT Over OAuth ✅ (Updated to GitHub App OAuth)
- **Original Decision**: Use fine-grained PAT instead of OAuth App device flow
- **Rationale**: Zero OAuth infrastructure, user controls exact repo scope, simpler code
- **Consequences**: User must manually create token on github.com, no repo list to pick from
- **Update**: GitHub App OAuth implemented as **primary** auth method (v0.4.0)
- **Current State**: OAuth as primary, PAT as fallback
- Location: `docs/adr/001-pat-over-oauth.md`

### ADR 002: Nepali Language Support with Transliteration ✅ (Phase 1 Complete)
- **Decision**: Phased approach for Nepali speech recognition
- **Phase 1 (✅ Complete)**: Manual language switching with Google RecognizerIntent
- **Phase 2 (Future)**: Optional transliteration (Devanagari → Latin using ICU)
- **Phase 3 (Future)**: Whisper integration for offline + auto-detection
- **Rationale**: Quick win with proven solution, future-proof for offline
- Location: `docs/adr/002-nepali-language-support.md`

### ADR 003: Agenda View with Orgzly-Inspired Architecture ✅ (Phase 1-3 Complete)
- **Decision**: Database-centric architecture for org-mode agenda
- **Approach**: Parse org files once into Room database, query with fast SQL
- **Benefits**: Instant agenda loading (<10ms vs 200-500ms), scales to thousands of notes
- **Components**: Normalized schema, recurring task expansion, TODO state management
- **Refresh Functionality**: Manual clear and re-sync via refresh button
  - Clears all agenda database data (notes, timestamps, metadata)
  - Forces full re-parse and re-sync from org files
  - Guarantees fresh data after external edits (Emacs, Syncthing, etc.)
- **Task Item Display**: Minimal — TODO state chip + title only
  - Tags are not shown in agenda view
  - Filename and time type labels (`S:`, `D:`) are not shown
  - Scheduled time shown as a **green chip** (bottom-right of card)
  - Deadline time shown as a **red chip** (bottom-right of card)
  - No chip shown if task has no scheduled/deadline time
- **Status**: Implemented and working
- Location: `docs/adr/003-agenda-view-with-orgzly-architecture.md`

### ADR 004: High-Fidelity Org-Mode Viewer ✅ (Phase 1 Complete)
- **Decision**: Build Compose-native org viewer with AST-driven rendering
- **Approach**: Map OrgNode tree to Compose components, inspired by Orgro
- **Why Not WebView/Flutter**: APK size, performance, native integration
- **Phase 1 (✅ Complete)**: Read-only viewer with folding, colors, structured display
- **Future Phases**: Inline markup (Phase 2A), editing (Phase 2B), tables/blocks (Phase 3)
- **Architecture**: LazyColumn with flattened tree, stable headline IDs, mutableStateMapOf for folding
- Location: `docs/adr/004-high-fidelity-org-viewer.md`

### VoiceInteractionService for Lock Screen Launch
- **Decision**: Register as Android digital assistant via `VoiceInteractionService`
- **Why**: Long-press side key launches app over lock screen (no unlock required)
- **What User Gives Up**: Long-press no longer opens Google Assistant (Hey Google still works)
- **Setup**: Two-step process (set default assistant + configure side button)
- Location: `docs/APP-TRIGGER.md`

---

## Common Development Tasks

### Adding a New Feature

1. **Read documentation first**
   - Check `docs/REQUIREMENTS.md` for feature specs
   - Check `docs/ROADMAP.md` to see if feature is planned
   - Read relevant ADRs for design decisions

2. **Understand existing patterns**
   - Follow MVVM architecture (ViewModel + Repository pattern)
   - Use Hilt for dependency injection
   - Follow Jetpack Compose best practices
   - Use EncryptedSharedPreferences for sensitive data

3. **Make changes**
   - Edit Kotlin files in `app/src/main/kotlin/com/rrimal/notetaker/`
   - Update ViewModel if UI state changes
   - Update Repository if data layer changes
   - Add WorkManager if background work needed

4. **Test locally**
   - Build: `./gradlew assembleDebug`
   - Install: `./gradlew installDebug`
   - Test on physical device (Samsung Galaxy S24 Ultra preferred)

5. **DO NOT commit** unless explicitly requested by user

### Modifying UI (Jetpack Compose)

1. **Locate the screen**
   - Screens: `app/src/main/kotlin/com/rrimal/notetaker/ui/screens/`
   - ViewModels: `app/src/main/kotlin/com/rrimal/notetaker/ui/viewmodels/`

2. **Follow Compose patterns**
   - Use `@Composable` functions
   - Hoist state to ViewModel
   - Use Material 3 components
   - Follow dark mode theme

3. **Preview changes**
   - Add `@Preview` annotations
   - Use Android Studio Compose Preview

### Working with GitHub API

1. **Location**: `app/src/main/kotlin/com/rrimal/notetaker/data/api/GitHubApi.kt`
2. **Authentication**: Bearer token in `Authorization` header
3. **Key Endpoints**:
   - `GET /repos/{owner}/{repo}/contents/{path}` — fetch file/folder contents
   - `PUT /repos/{owner}/{repo}/contents/{path}` — create/update file
   - `GET /user` — validate token
   - `GET /user/installations` — OAuth installation discovery
4. **Error Handling**: 401/403 → auth failure, 404 → not found, 422 → conflict

### Working with Room Database

1. **Location**: `app/src/main/kotlin/com/rrimal/notetaker/data/local/`
2. **Entities**: `NoteEntity`, `SubmissionHistoryEntity`
3. **DAOs**: `NoteDao`, `SubmissionHistoryDao`
4. **Usage**: Repository pattern wraps DAO operations
5. **Migrations**: Handled in `AppDatabase.kt`

### Working with WorkManager

1. **Location**: `app/src/main/kotlin/com/rrimal/notetaker/workers/NoteUploadWorker.kt`
2. **Purpose**: Retry failed GitHub uploads when network available
3. **Constraints**: Network connectivity required
4. **Retry Logic**: Exponential backoff, stops on auth failure
5. **Enqueue**: Called from Repository when note submit fails

### Updating Dependencies

1. **Location**: `gradle/libs.versions.toml`
2. **Update version**: Change version number in `[versions]` section
3. **Sync Gradle**: Android Studio → File → Sync Project with Gradle Files
4. **Test**: Build and run on physical device

---

## Release Process

### Branch Strategy
```
feature branches → develop → staging → master
                   (no deploy)  (closed testing)  (production track)
```

- **`develop`** — daily driver, push freely, nothing deploys
- **`staging`** — merge develop here to auto-deploy to Google Play **closed testing** track
- **`master`** — merge staging here to auto-deploy to Google Play **production** track
- **Feature branches** — `feature/<name>` off develop, merge back when done

### Versioning

**Version Code** (`versionCode`): Integer that must strictly increase with every Play Store upload
- **CI builds**: `github.run_number + 100`
- **Local builds**: `VERSION_CODE` in `local.properties` (currently 6)
- **Fallback**: Defaults to `1` if neither provided

**Version Name** (`versionName`): Semantic versioning `MAJOR.MINOR.PATCH`
- Hardcoded in `app/build.gradle.kts`
- Bumped manually as part of "prepare release" commit on `develop`
- Current: `0.7.0`

### Releasing a New Version

All three steps in single "prepare release" commit on `develop`:

1. **Bump `versionName`** in `app/build.gradle.kts`
2. **Add new section** to `CHANGELOG.md` (developer-facing, detailed)
3. **Update `whatsnew/whatsnew-en-US`** (Play Store user-facing, max 500 characters)

Then promote through branches:
```bash
# 1. Merge to staging → auto-deploys to internal testing
git checkout staging && git merge develop && git push

# 2. Install from Play Store internal link, verify on device

# 3. Merge to master → auto-deploys to production
git checkout master && git merge staging && git push
```

See `docs/DEPLOYMENT.md` for full CI/CD details.

---

## Security Considerations

### Token Storage
- **Never** log tokens or credentials
- **Never** commit tokens to git
- Use `EncryptedSharedPreferences` for token storage (Android Keystore-backed)
- HTTP logging **disabled** in release builds

### Permissions
- `RECORD_AUDIO` — for speech recognition (runtime permission)
- `INTERNET` — for GitHub API and speech recognition
- No location, camera, or contacts permissions needed

### ProGuard/R8
- Minification **enabled** in release builds
- ProGuard rules in `app/proguard-rules.pro`
- Keep Retrofit interfaces, Room entities, kotlinx.serialization

### Code Security
- **Never** hardcode API keys or secrets in source code
- Use BuildConfig for OAuth client ID/secret (loaded from `local.properties`)
- GitHub secrets used in CI/CD for signing keys and OAuth credentials

---

## Navigation Structure

```
MainActivity (Entry Point)
    ├─ MainScreen (Swipeable HorizontalPager - Home)
    │   ├─ Page 0: NoteInputScreen (Dictation)
    │   │   ├─ Voice/text note capture
    │   │   ├─ Submission history
    │   │   ├─ Topic display
    │   │   └─ Browse/Settings icons
    │   ├─ Page 1: AgendaScreen (DEFAULT - Center)
    │   │   ├─ Day-grouped tasks
    │   │   ├─ TODO state management
    │   │   ├─ Filter and refresh
    │   │   └─ Settings icon
    │   └─ Page 2: InboxCaptureScreen (TODO Entry)
    │       ├─ Minimalist quick task UI
    │       ├─ Expandable details section
    │       └─ Expandable schedule section
    ├─ PomodoroTimerScreen (fullscreen overlay, above HorizontalPager)
    │   ├─ Rendered in MainScreen root Box — not clipped by pager
    │   ├─ TaskInfoOverlay — tappable; opens StateSelectionDialog
    │   ├─ PomodoroCompletionDialog — shown when timer completes
    │   └─ Minimize button (chevron-down) — collapses to chip in AgendaScreen top bar
    ├─ BrowseScreen (accessed via top bar button)
    │   ├─ Folder navigation
    │   ├─ File viewer (org-mode rendering)
    │   └─ Settings Icon → SettingsScreen
    └─ SettingsScreen (accessed via top bar button)
        ├─ Auth configuration
        ├─ Digital assistant setup
        ├─ Storage mode selection
        ├─ Agenda configuration
        └─ Delete all data

NoteCaptureActivity (Lock Screen Entry)
    ├─ Launched via VoiceInteractionService
    ├─ Shows NoteInputScreen directly (no pager)
    └─ FLAG_ACTIVITY_NEW_TASK, showWhenLocked, turnScreenOn
```

### Swipeable Navigation (v0.8.0)
- **Center (Default)**: Agenda - Your command center
- **Swipe Left**: Dictation - Quick voice/text capture
- **Swipe Right**: Inbox Capture - Structured TODO entry
- **Visual Indicators**: Bottom page indicators show current position with labels

---

## Roadmap & Future Features

### Completed (V2-V3) ✅
- Offline Note Queuing
- Browse Notes (read-only repo browser)
- GitHub App OAuth
- Local Org Files Storage
- Inbox Capture (org-mode TODO)
- Improved Dictation Format
- Nepali Language Support (Phase 1)
- High-Fidelity Org-Mode Viewer (Phase 1)
- Agenda View (Orgzly-inspired database architecture)
- Swipeable Navigation (Agenda-first design)
- Toggl Track Time Tracking
- Pomodoro Timer (foreground service, fullscreen overlay, minimize/maximize, state dialog integration)

### V3 Features (Planned)

**Nepali Language Support - Phase 2 & 3**
- Phase 2: Optional transliteration (Devanagari → Latin using ICU)
- Phase 3: Whisper integration for offline + auto-detection

**Multi-Repo Support**
- Connect more than one GitHub repository
- Switch between repos in-app
- Currently requires disconnect/reconnect to change repos

**Donate / Tip Button**
- In-app option for users to support development
- External link to GitHub Sponsors or Buy Me a Coffee (no in-app purchases)

**Smarter Topic Refresh**
- Periodic polling (e.g., every 60s while app in foreground)
- GitHub webhook via push notification (requires server infrastructure)
- ETag/If-None-Match on Contents API to make polling cheap

See `docs/ROADMAP.md` for complete list.

---

## When User Asks For Changes

### Step 1: Understand

- Read the request carefully
- Ask clarifying questions if needed
- Check which files/features are affected
- Review relevant documentation (`docs/REQUIREMENTS.md`, ADRs, etc.)

### Step 2: Consult Documentation

- Read relevant `docs/*.md` files
- Understand current implementation
- Check for existing patterns (MVVM, Repository pattern)
- Check ADRs for design decisions

### Step 3: Make Changes

- Edit Kotlin files following established patterns
- Follow Jetpack Compose best practices
- Use Hilt for dependency injection
- Update ViewModel if UI state changes
- Update Repository if data layer changes
- Add WorkManager if background work needed
- Use EncryptedSharedPreferences for sensitive data
- Test logic mentally

### Step 4: Explain

- Show what you changed and where
- Explain why (architecture, best practices, etc.)
- Suggest how user can test locally
- Provide `./gradlew` commands if needed
- **DO NOT commit** unless explicitly requested

---


## Danger Zones

### DO NOT

❌ Run git commands (add, commit, push) unless explicitly requested
❌ Commit sensitive data (tokens, API keys, keystore passwords)
❌ Change established architecture patterns (MVVM, Repository)
❌ Remove or bypass security measures (EncryptedSharedPreferences, permissions)
❌ Break existing features when adding new ones
❌ Add features not requested or not in REQUIREMENTS.md
❌ Over-engineer solutions (keep it simple)
❌ Hardcode API keys or secrets in source code
❌ Disable ProGuard/R8 in release builds
❌ Change minSdk without good reason (breaks compatibility)

### DO

✅ Read documentation first (`docs/REQUIREMENTS.md`, ADRs, etc.)
✅ Follow MVVM architecture (ViewModel + Repository pattern)
✅ Use Hilt for dependency injection
✅ Use EncryptedSharedPreferences for sensitive data (tokens)
✅ Follow Jetpack Compose best practices
✅ Handle errors gracefully (401, 403, 404, 422)
✅ Test on physical device (Samsung Galaxy S24 Ultra preferred)
✅ Explain changes clearly
✅ Suggest git commands (don't run them unless requested)
✅ Ask questions when unclear
✅ Keep it simple

---

## Debugging Checklist

When something's not working:

1. **Build Issue?**
   - Check Gradle sync: Android Studio → File → Sync Project with Gradle Files
   - Check dependencies in `gradle/libs.versions.toml`
   - Check ProGuard rules in `app/proguard-rules.pro`
   - Run `./gradlew clean` and rebuild

2. **Authentication Issue?**
   - Check token in EncryptedSharedPreferences (not logged, use debugger)
   - Verify token via `GET /user` API call
   - Check for 401/403 errors in Logcat
   - Verify OAuth client ID/secret in `local.properties`

3. **GitHub API Issue?**
   - Check network connection
   - Check API endpoint in `GitHubApi.kt`
   - Check Authorization header (Bearer token)
   - Check Logcat for HTTP errors (401, 403, 404, 422)
   - Verify repo name format (`owner/repo`)

4. **UI Issue (Jetpack Compose)?**
   - Check state management in ViewModel
   - Verify StateFlow/Flow is being collected
   - Check recomposition (use `remember`, `derivedStateOf`)
   - Use Compose Preview to debug layout
   - Check theme colors in `app/src/main/kotlin/com/rrimal/notetaker/ui/theme/`

5. **Speech Recognition Issue?**
   - Check `RECORD_AUDIO` permission granted
   - Check `SpeechRecognizer.isRecognitionAvailable(context)`
   - Check Logcat for SpeechRecognizer errors
   - Verify language code (`en-US`, `ne-NP`)
   - Check network connection (Google's cloud-based models)

6. **Room Database Issue?**
   - Check entity definitions in `data/local/`
   - Check DAO queries
   - Verify database migrations in `AppDatabase.kt`
   - Use Android Studio Database Inspector

7. **WorkManager Issue?**
   - Check Logcat for WorkManager logs
   - Verify constraints (network connectivity)
   - Check retry logic in `NoteUploadWorker.kt`
   - Use WorkManager Inspector in Android Studio

8. **Lock Screen Launch Issue?**
   - Check if app is set as default digital assistant
   - Check side button settings (Samsung: Advanced Features → Side Button)
   - Verify `VoiceInteractionService` in AndroidManifest.xml
   - Check `showWhenLocked` and `turnScreenOn` flags
   - See `docs/APP-TRIGGER.md` for full setup

---

## Communication Style

### When Responding

**Be Clear**:
- Explain what you're doing and why
- Show the actual changes (file paths, line numbers)
- Provide context (architecture, design decisions, ADRs)

**Be Helpful**:
- Suggest next steps
- Provide Gradle commands for testing (`./gradlew assembleDebug`, `./gradlew installDebug`)
- Provide git commands (for user to run, not execute)
- Offer to make related changes or improvements

**Be Respectful**:
- Don't override established architecture patterns
- Ask before major changes or refactoring
- Acknowledge good ideas and user preferences

### Example Response

```markdown
I've added the new feature for [feature name].

**Changes made:**
- Created: app/src/main/kotlin/com/rrimal/notetaker/ui/screens/NewFeatureScreen.kt
- Created: app/src/main/kotlin/com/rrimal/notetaker/ui/viewmodels/NewFeatureViewModel.kt
- Updated: app/src/main/kotlin/com/rrimal/notetaker/MainActivity.kt (added navigation route)
- Updated: app/src/main/kotlin/com/rrimal/notetaker/data/repository/NoteRepository.kt (added new method)

**Architecture:**
- Follows MVVM pattern (ViewModel + Repository)
- Uses Hilt for dependency injection
- State management via StateFlow
- Error handling for 401/403/404

**To test:**
./gradlew assembleDebug
./gradlew installDebug

Then open the app and navigate to [feature location].

**To publish:**
git add app/src/main/kotlin/com/rrimal/notetaker/ui/screens/NewFeatureScreen.kt
git add app/src/main/kotlin/com/rrimal/notetaker/ui/viewmodels/NewFeatureViewModel.kt
git add app/src/main/kotlin/com/rrimal/notetaker/MainActivity.kt
git add app/src/main/kotlin/com/rrimal/notetaker/data/repository/NoteRepository.kt
git commit -m "Add [feature name]"
git push

Would you like me to add unit tests or update the documentation?
```

---

## Quick Reference

### Commands

```bash
# Development
./gradlew assembleDebug           # Build debug APK
./gradlew installDebug             # Install debug APK on connected device
./gradlew bundleRelease            # Build release AAB (for Play Store)
./gradlew clean                    # Clean build artifacts

# Testing
./gradlew test                     # Run unit tests
./gradlew connectedAndroidTest     # Run instrumentation tests (requires device)

# Deployment
# See docs/DEPLOYMENT.md for full CI/CD workflow
git checkout staging && git merge develop && git push  # Deploy to closed testing
git checkout master && git merge staging && git push    # Deploy to production
```

### Key Files

**Build Configuration**
- `app/build.gradle.kts` - Gradle build config (dependencies, versioning, signing)
- `gradle/libs.versions.toml` - Dependency versions
- `local.properties` - Local secrets (OAuth credentials, keystore paths)

**Core Application**
- `app/src/main/kotlin/com/rrimal/notetaker/MainActivity.kt` - Entry point, navigation
- `app/src/main/AndroidManifest.xml` - App manifest, permissions, services

**UI Layer**
- `app/src/main/kotlin/com/rrimal/notetaker/ui/screens/` - Composable screens
- `app/src/main/kotlin/com/rrimal/notetaker/ui/viewmodels/` - ViewModels (UI state)
- `app/src/main/kotlin/com/rrimal/notetaker/ui/orgview/` - Org-mode viewer components
- `app/src/main/kotlin/com/rrimal/notetaker/ui/theme/` - Material 3 theme

**Data Layer**
- `app/src/main/kotlin/com/rrimal/notetaker/data/api/GitHubApi.kt` - Retrofit API interface
- `app/src/main/kotlin/com/rrimal/notetaker/data/repository/` - Repository pattern
- `app/src/main/kotlin/com/rrimal/notetaker/data/local/` - Room database (note queue)

**Key Features**
- `app/src/main/kotlin/com/rrimal/notetaker/speech/SpeechRecognizerManager.kt` - Voice input
- `app/src/main/kotlin/com/rrimal/notetaker/assist/NoteAssistService.kt` - Lock screen launch
- `app/src/main/kotlin/com/rrimal/notetaker/workers/NoteUploadWorker.kt` - Offline retry
- `app/src/main/kotlin/com/rrimal/notetaker/storage/` - Local org files (SAF)
- `app/src/main/kotlin/com/rrimal/notetaker/ui/orgview/` - Org-mode viewer (Phase 1)
- `app/src/main/kotlin/com/rrimal/notetaker/data/orgmode/OrgParser.kt` - Org file parsing
- `app/src/main/kotlin/com/rrimal/notetaker/pomodoro/PomodoroTimerService.kt` - Pomodoro foreground service
- `app/src/main/kotlin/com/rrimal/notetaker/pomodoro/PomodoroTimerState.kt` - Pomodoro state model
- `app/src/main/kotlin/com/rrimal/notetaker/ui/screens/pomodoro/PomodoroTimerScreen.kt` - Pomodoro fullscreen overlay
- `app/src/main/kotlin/com/rrimal/notetaker/ui/screens/pomodoro/TaskInfoOverlay.kt` - Tappable task info inside timer
- `app/src/main/kotlin/com/rrimal/notetaker/ui/screens/agenda/StateSelectionDialog.kt` - TODO state dialog (shared by AgendaScreen + MainScreen)

**Documentation**
- `docs/` - All project documentation

### Documentation

Always check `docs/` before making changes:
- `docs/REQUIREMENTS.md` - **Start here** - Feature requirements and specs
- `docs/ROADMAP.md` - Completed features and future plans
- `docs/DEPLOYMENT.md` - CI/CD pipeline, versioning, Play Store publishing
- `docs/APP-TRIGGER.md` - Lock screen launch implementation (VoiceInteractionService)
- `docs/PAT-SETUP.md` - GitHub Personal Access Token setup guide
- `docs/TOGGL-INTEGRATION.md` - Toggl Track time tracking integration (complete guide)
- `docs/PHONE-TIME-TRACKING.md` - PHONE_STARTED/PHONE_ENDED property behavior
- `docs/adr/` - Architecture Decision Records (key design decisions)
  - `001-pat-over-oauth.md` - Authentication approach
  - `002-nepali-language-support.md` - Nepali speech recognition implementation
  - `003-agenda-view-with-orgzly-architecture.md` - Agenda implementation (complete)
  - `004-high-fidelity-org-viewer.md` - Org viewer architecture (Phase 1 complete)
- `docs/research/` - Research documentation for features
- `docs/playstore/` - Play Store assets (screenshots, store listing, privacy policy)

---

*Last Updated: 2026-03-02*
*Project Version: 0.8.0 (Swipeable Navigation + Agenda-First Design + Toggl Integration + Pomodoro Timer)*
*Documentation: Complete*
