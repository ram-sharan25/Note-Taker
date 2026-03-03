# Current State Summary — Note Taker App
**Date:** 2026-03-01
**Version:** 0.8.0
**Milestone:** M44 (Org-Mode Agenda View Phase 1-2)

---

## Executive Summary

The Note Taker app is a fully functional Android application (81 Kotlin files, ~15,000 lines of code) for capturing voice and text notes that sync to GitHub or save locally as org-mode files. The latest implementation (M44) adds a complete agenda system for viewing scheduled and deadline items from local org files with support for recurring tasks.

**Build Status:** ✅ `./gradlew assembleDebug` → BUILD SUCCESSFUL
**Deployment:** Three-branch CI/CD pipeline to Google Play (develop → staging → master)
**Test Device:** Samsung Galaxy S24 Ultra (Android 16, OneUI 8.0)

---

## What's Been Implemented (M1-M44)

### Core Features (V1)

1. **Voice-First Note Input** (M17, M22, M28b)
   - Continuous speech recognition with real-time streaming
   - Auto-starts on screen appear
   - Mode switching: tap text field (keyboard) or mic (voice)
   - Language support: English (en-US) and Nepali (ne-NP) with manual toggle
   - Growing text field that fills available screen space
   - Submit button with animated states (Submit → Saving → Sent! → Queued)

2. **GitHub Integration** (M5, M6, M31-M40)
   - **Primary:** GitHub App OAuth with PKCE protection
   - **Fallback:** Fine-grained Personal Access Token
   - Push notes to `inbox/` folder as markdown files
   - ISO 8601 timestamp filenames (e.g., `2026-02-09T143200-0500.md`)
   - Sticky topic display (fetches `.current_topic` from repo)
   - Browse notes (read-only repo browser with markdown rendering via Markwon)
   - Token revocation on sign-out (OAuth only)
   - Re-authentication flow preserves installation_id
   - Repo selection dialog when GitHub App has access to multiple repos

3. **Local Org-Mode Files** (M44, plus earlier work)
   - Storage Access Framework (SAF) for folder selection
   - Two capture modes:
     - **Voice dictation** → Creates timestamped `.org` files
     - **Inbox capture** → Appends TODO entries to single inbox file
   - Full subdirectory support (e.g., `Brain/tasks.org`, `Work/projects.org`)
   - Improved dictation format: first sentence as heading, rest as body
   - High-fidelity org viewer with folding, colors, TODO chips, planning lines

4. **Org-Mode Agenda View** (M44) ⭐ **NEW**
   - Database-centric architecture (Orgzly-inspired)
   - Normalized Room schema: notes, timestamps, planning, file metadata
   - SHA-256 file hashing for efficient sync (skip unchanged files)
   - Recurring task expansion: supports `++1d`, `.+1w`, `+1m` repeaters
   - Bucketed day view: Overdue, Today, Upcoming
   - TODO state chips with semantic colors
   - Status filter chips (toggle specific TODO states)
   - Manual sync button
   - Background sync worker (OrgFileSyncWorker via WorkManager)
   - Configuration: agenda files list, time period (1-30 days), TODO keywords
   - Smart duplicate detection (same note with SCHEDULED + DEADLINE shows once)

5. **Offline Support** (M13, M14, M37)
   - Queue-first submission: notes always saved to Room before GitHub push
   - WorkManager retry with network constraint and exponential backoff
   - Pending notes counter UI
   - Auth failure detection (401/403 stops retry, shows "Session expired")
   - Conflict handling (422 duplicate filename → append suffix)

6. **Lock Screen Launch** (M8, M11)
   - VoiceInteractionService for side button long-press
   - Launches over lock screen (showWhenLocked + turnScreenOn)
   - Two-tier security: capture without auth, settings requires unlock
   - Digital assistant setup guide in Settings (two-step: set default + configure side button)

7. **Security & Privacy** (M20, M21, M31, M43)
   - EncryptedSharedPreferences for token storage (Android Keystore-backed)
   - R8/ProGuard minification in release builds
   - HTTP body logging disabled in release (prevents PAT leaks)
   - ADB backup disabled (`allowBackup="false"`)
   - OAuth PKCE flow with state validation
   - Audio focus management (prevents audiobook blips during speech recognition)

8. **UI/UX Polish** (M26, M27, M28b, M32, M33)
   - Material 3 dark theme with teal/blue/green accents
   - Purple-tinted dark surfaces matching app icon
   - Pill-shaped submit button
   - Cards with surfaceContainer backgrounds (Settings, Auth)
   - Sticky surface containers for section headers
   - Two-card auth screen: "Create Your Notes Repo" + "Connect Your Repo"
   - Help dialogs with `(?)` icons (token security, repo format, permissions)
   - "Need help?" link to YouTube setup walkthrough
   - Growing text field with scrollbar on overflow
   - Digital assistant onboarding dialog on first launch

9. **CI/CD & Deployment** (M18, M25, M35, M43)
   - Three-branch pipeline: `develop` (no deploy) → `staging` (internal testing) → `master` (production)
   - GitHub Actions workflow: build signed AAB, upload to Google Play
   - Version management: versionCode from `run_number + 100`, versionName manual semver
   - Play Store materials: screenshots (7), store listing, privacy policy, data safety declaration
   - Native debug symbols included in AAB (R8 symbolication)
   - OAuth credentials via `local.properties` (not env vars due to `GITHUB_` prefix restriction)

---

## Architecture Overview

### Tech Stack
- **Language:** Kotlin 2.2.10 (AGP 9.0.0 built-in)
- **UI:** Jetpack Compose (BOM 2026.01.01), Material 3
- **DI:** Hilt 2.59.1
- **Database:** Room 2.8.4 (4 migrations: v1→v2 pending_notes, v2→v3 [unknown], v3→v4 agenda tables)
- **Network:** Retrofit 3.0.0 + OkHttp 5.3.0 + kotlinx.serialization 1.8.0
- **Build:** Gradle 9.1.0, KSP 2.3.5, AGP 9.0.0
- **Security:** EncryptedSharedPreferences (Tink 1.0.0)
- **Background:** WorkManager 2.10.1
- **Markdown:** Markwon 4.6.2

### Project Structure (81 Kotlin files)

```
app/src/main/kotlin/com/rrimal/notetaker/
├── ui/                                  # Jetpack Compose UI
│   ├── screens/
│   │   ├── AuthScreen.kt                # Two-card OAuth + PAT setup
│   │   ├── NoteInputScreen.kt           # Voice-first note capture
│   │   ├── InboxCaptureScreen.kt        # TODO capture (title + description)
│   │   ├── BrowseScreen.kt              # Read-only repo browser
│   │   ├── SettingsScreen.kt            # Settings (auth, digital assistant, agenda config)
│   │   └── agenda/
│   │       ├── AgendaScreen.kt          # Bucketed day view
│   │       └── AgendaItem.kt            # Sealed class (Day, Note)
│   ├── viewmodels/
│   │   ├── AuthViewModel.kt             # OAuth flow + PAT validation
│   │   ├── NoteViewModel.kt             # Note input state + submission
│   │   ├── InboxCaptureViewModel.kt     # Inbox TODO capture
│   │   ├── BrowseViewModel.kt           # Repo browser navigation
│   │   ├── SettingsViewModel.kt         # Settings + agenda config
│   │   └── AgendaViewModel.kt           # Agenda list building
│   ├── components/
│   │   ├── TopicBar.kt                  # Sticky topic + settings/browse/agenda icons
│   │   └── SubmissionHistory.kt         # Collapsible recent submissions
│   ├── orgview/                         # Org-mode viewer components
│   │   ├── OrgFileViewer.kt             # High-fidelity structured display
│   │   ├── OrgHeadlineRow.kt            # Headline with folding + colors
│   │   └── [other org viewer components]
│   ├── theme/
│   │   ├── Color.kt                     # Teal/blue/green + dark purple surfaces
│   │   └── Theme.kt                     # Material 3 dark color scheme
│   └── navigation/
│       └── NavGraph.kt                  # Type-safe Compose Navigation
├── data/
│   ├── auth/
│   │   ├── AuthManager.kt               # Token storage (EncryptedSharedPreferences + DataStore)
│   │   ├── OAuthConfig.kt               # OAuth constants + PKCE helpers
│   │   ├── OAuthTokenExchanger.kt       # Token exchange + revocation
│   │   └── OAuthCallbackHolder.kt       # OAuth callback shuttle (Activity → ViewModel)
│   ├── api/
│   │   ├── GitHubApi.kt                 # Retrofit interface (Contents, User, Installations)
│   │   └── GitHubInstallationApi.kt     # Installation discovery
│   ├── local/                           # Room database
│   │   ├── AppDatabase.kt               # 7 tables (submissions, pending_notes, notes, timestamps, planning, file_metadata, todo_keywords)
│   │   ├── PendingNoteEntity.kt + Dao   # Offline note queue
│   │   ├── SubmissionEntity.kt + Dao    # Submission history
│   │   ├── NoteEntity.kt + Dao          # Agenda notes
│   │   ├── OrgTimestampEntity.kt + Dao  # Parsed timestamps
│   │   ├── NotePlanningEntity.kt + Dao  # SCHEDULED/DEADLINE links
│   │   ├── FileMetadataEntity.kt + Dao  # Sync state tracking
│   │   └── TodoKeywordsConfigEntity.kt + Dao  # TODO state sequences
│   ├── repository/
│   │   ├── NoteRepository.kt            # Submit, fetch topic, browse
│   │   └── AgendaRepository.kt          # Sync org files, expand recurring tasks, build agenda list
│   ├── orgmode/
│   │   ├── OrgParser.kt                 # Full org-mode parser
│   │   ├── OrgWriter.kt                 # Write org files
│   │   ├── OrgTimestampParser.kt        # Parse org timestamps (SCHEDULED, DEADLINE, etc.)
│   │   └── [OrgNode, OrgFile models]
│   ├── storage/
│   │   ├── LocalFileManager.kt          # SAF file operations
│   │   └── LocalOrgStorageBackend.kt    # Org-mode file backend
│   ├── preferences/
│   │   └── AgendaConfigManager.kt       # Agenda settings (files, range, keywords, filters)
│   └── worker/
│       ├── NoteUploadWorker.kt          # Retry failed GitHub uploads
│       └── OrgFileSyncWorker.kt         # Background agenda sync
├── speech/
│   └── SpeechRecognizerManager.kt       # Continuous speech recognition + audio focus
├── assist/
│   ├── NoteAssistService.kt             # VoiceInteractionService
│   ├── NoteAssistSessionService.kt      # Session factory
│   ├── NoteAssistSession.kt             # Unlocked launch handler
│   └── NoteRecognitionService.kt        # Stub recognizer (required for ROLE_ASSISTANT)
├── di/
│   └── AppModule.kt                     # Hilt DI (provides all DAOs, repositories, APIs)
├── NoteApp.kt                           # HiltAndroidApp + WorkManager config + token migration
├── MainActivity.kt                      # Entry point (normal launch)
├── NoteCaptureActivity.kt               # Entry point (lock screen launch)
└── OAuthCallbackActivity.kt             # OAuth redirect handler (notetaker://callback)
```

---

## Database Schema (Room v4)

### Tables

1. **submissions** — Submission history (last 10 items)
2. **pending_notes** — Offline queue for failed GitHub uploads
3. **notes** — Agenda notes (parsed from org files)
4. **org_timestamps** — Parsed timestamps (SCHEDULED, DEADLINE, CLOSED)
5. **note_planning** — Links notes to timestamps (foreign keys)
6. **file_metadata** — Sync state (filename, lastSynced, lastModified, SHA-256 hash)
7. **todo_keywords_config** — User-defined TODO state sequence (singleton)

### Migrations

- **v1→v2:** Added `pending_notes` table
- **v2→v3:** [Not documented in IMPLEMENTATION_LOG]
- **v3→v4:** Added all 5 agenda tables (notes, org_timestamps, note_planning, file_metadata, todo_keywords_config)

---

## Key Implementation Patterns

### 1. Agenda Architecture (M44)

**Problem:** Parsing org files on every screen open is slow (200-500ms for 1000 headlines).

**Solution:** Database-centric approach inspired by Orgzly:
1. Parse org files → insert into Room database (one-time, or on file change)
2. Query database with indexed timestamps (fast: <10ms)
3. Expand recurring tasks in-memory (only for visible date range)
4. Display in bucketed day view (Today, Upcoming, Overdue)

**Benefits:**
- Instant agenda loading (SQL query vs file parsing)
- Scales to thousands of notes
- Supports recurring tasks (`++1d`, `.+1w`, `+1m`)
- Battery efficient (no repeated parsing)
- Offline support (cached in database)

**Key Components:**
- `OrgTimestampParser` — Regex-based timestamp parser
- `expandRecurringTimestamp()` — In-memory recurring expansion with "jump-ahead" logic
- `syncFileToDatabase()` — Parse → insert with SHA-256 change detection
- `buildAgendaList()` — Query + expand + bucket into Day/Note items

### 2. OAuth Flow (M31-M40)

**Flow:**
1. User taps "Sign in with GitHub"
2. App generates PKCE verifier + state, saves to SavedStateHandle
3. Chrome Custom Tab opens GitHub App install page
4. User picks account + repo
5. GitHub redirects → Pages bounce → `notetaker://callback`
6. `OAuthCallbackActivity` extracts code + state, passes to `OAuthCallbackHolder`
7. `AuthViewModel` validates state, exchanges code for token (PKCE)
8. Discovers installation + repo via `/user/installations` API
9. Saves token (EncryptedSharedPreferences), metadata (DataStore), installation_id (DataStore)

**Re-authentication:**
- Sign-out preserves `installation_id`
- Next sign-in uses authorize URL (not install URL) if installation_id exists
- Stale installation detection: if `/user/installations` returns empty, clears installation_id and shows install flow

**Token Revocation:**
- OAuth sign-out calls `DELETE /applications/{client_id}/token` with Basic auth
- Best-effort (5s timeout), continues even if revocation fails

### 3. Voice Recognition (M17, M21, M22)

**Continuous Listening:**
- Auto-restarts on `onResults()`, `ERROR_NO_MATCH`, `ERROR_SPEECH_TIMEOUT`
- Real errors (audio, network, server) stop listening and notify UI
- Partial text updates in real-time
- Finalized segments joined with spaces

**Audio Focus:**
- Holds `AUDIOFOCUS_GAIN_TRANSIENT` throughout listening session
- Prevents audiobook blips during 150ms restart gap
- Released on stop

**Language Switching:**
- English (en-US) / Nepali (ne-NP) toggle
- Stored in EncryptedSharedPreferences
- Recognizer restarts with new language on toggle

### 4. Offline Queue (M13, M14, M37)

**Queue-First Submission:**
1. Always insert to `pending_notes` first (status: `pending`)
2. Try immediate GitHub push
3. On success: delete from queue, add to `submissions` history
4. On failure: mark as `failed`, WorkManager schedules retry
5. On auth failure (401/403): mark as `auth_failed`, stop retrying

**Worker Retry:**
- Network connectivity constraint
- Exponential backoff
- Processes all pending notes in batch
- Handles 422 conflict (duplicate filename) by appending `-1` suffix

**UI Indicators:**
- Submit button shows "Queued" state with clock icon
- Pending count badge below submit button
- Sign-out warning dialog when `pendingCount > 0`

---

## Testing Status

### What's Been Tested

✅ **Build:** `./gradlew assembleDebug` and `./gradlew bundleRelease` both succeed
✅ **Installation:** APK installs on Samsung Galaxy S24 Ultra (Android 16)
✅ **Database Migrations:** v1→v2→v3→v4 all pass on fresh install and upgrade
✅ **Agenda Parsing:** Correctly parses SCHEDULED/DEADLINE timestamps with repeaters
✅ **Recurring Expansion:** All repeater types (`++`, `.+`, `+`) and units (h, d, w, m, y) work
✅ **Duplicate Detection:** Notes with both SCHEDULED + DEADLINE only appear once per day
✅ **File Sync:** SHA-256 hashing correctly skips unchanged files

### What Needs Testing

⏳ **OAuth Flow:** End-to-end on device (install → auth → discover repo → save token)
⏳ **OAuth Re-auth:** Sign-out → re-auth (authorize URL), delete all → sign-in (install URL)
⏳ **Stale Installation:** Uninstall GitHub App on GitHub → sign-in (should detect stale installation_id)
⏳ **Repo Selection:** Install GitHub App on 2 repos → verify selection dialog appears
⏳ **Agenda TODO State:** Tap item → change state → verify file write-back works
⏳ **Conflict Resolution:** Edit org file externally (Emacs/Syncthing) → change TODO state in app → verify conflict detection + retry
⏳ **Worker Retry:** Turn off network → submit note → verify WorkManager retry when network returns
⏳ **Auth Failure:** Revoke PAT on GitHub → submit note → verify "Session expired" error

---

## Known Issues & Limitations

### Agenda View
- ⚠️ **TODO state dialog is view-only** — Shows current state but no update button (write-back logic exists in `AgendaRepository.updateTodoState()` but not wired to UI)
- ⚠️ **No lock screen notification** — No persistent notification showing next 3-5 items
- ⚠️ **No saved searches** — Can't create custom filters ("Overdue", "This week", "High priority")
- ⚠️ **No bulk operations** — Can't mark multiple items done at once
- ⚠️ **No agenda widget** — No home screen widget

### Org-Mode Viewer
- ⚠️ **No inline markup** — `*bold*`, `/italic/`, `[[links]]` not rendered (Phase 2A not started)
- ⚠️ **Read-only** — Can't edit headlines in viewer (Phase 2B not started)
- ⚠️ **No tables/code blocks** — Not rendered (Phase 3 not started)

### General
- ⚠️ **Single GitHub repo** — Can't connect multiple repos, must disconnect to change
- ⚠️ **No topic refresh** — Topic only fetches on app open, not periodically
- ⚠️ **No transliteration** — Nepali speech always outputs Devanagari script (Phase 2 not started)
- ⚠️ **Cloud-based speech** — Requires internet, no offline Whisper integration (Phase 3 not started)

---

## Next Steps (Recommended Priority)

### High Priority (User-Facing Impact)

1. **Wire up TODO state editing in Agenda** (Phase 3)
   - Add "Update" button to state selection dialog
   - Call `AgendaRepository.cycleTodoState(noteId)` on tap
   - Show success/error snackbar
   - Refresh agenda list after state change
   - **Estimate:** 2-3 hours

2. **Lock screen agenda notification** (Phase 4)
   - Persistent notification showing next 3-5 items
   - "Mark Done" action button
   - Update notification on state changes
   - Query database without waking app
   - **Estimate:** 1 week

3. **Multi-repo support**
   - Add repo picker UI in Settings
   - Store multiple repos in DataStore (list of `{name, owner, auth_type, token, installation_id}`)
   - Switch active repo without disconnecting
   - **Estimate:** 1 week

### Medium Priority (Polish & UX)

4. **Inline markup in org viewer** (Phase 2A)
   - Render `*bold*`, `/italic/`, `_underline_`, `~code~`, `[[links]]`
   - Use AnnotatedString with SpanStyle
   - **Estimate:** 1 week

5. **Saved searches in Agenda**
   - Predefined filters: "Overdue", "This week", "High priority", "All TODOs"
   - Saved search UI (bottom sheet with filter chips)
   - Persist selected filter in DataStore
   - **Estimate:** 3-4 days

6. **Smarter topic refresh**
   - Periodic polling (every 60s while app in foreground)
   - ETag/If-None-Match support to make polling cheap
   - **Estimate:** 2-3 days

### Low Priority (Nice to Have)

7. **Agenda home screen widget**
   - Android Glance API (Jetpack Compose for widgets)
   - Shows next 3-5 items
   - Tap to open app
   - **Estimate:** 1-2 weeks

8. **Bulk operations in Agenda**
   - Swipe to mark done
   - Select multiple → mark done / delete / reschedule
   - **Estimate:** 1 week

9. **Nepali transliteration** (Phase 2)
   - Optional Devanagari → Latin transliteration using ICU library
   - Toggle in Settings
   - **Estimate:** 3-4 days

10. **Whisper integration** (Phase 3)
    - Offline speech recognition using Whisper Android
    - Auto-detect language (English vs Nepali)
    - **Estimate:** 2-3 weeks (includes Whisper model integration + testing)

---

## Documentation Files

### User-Facing
- `docs/REQUIREMENTS.md` — Feature requirements (FR1-FR15)
- `docs/ROADMAP.md` — Completed features + future plans
- `docs/DEPLOYMENT.md` — CI/CD guide (branching, versioning, Play Store)
- `docs/PAT-SETUP.md` — Manual PAT generation guide
- `docs/APP-TRIGGER.md` — Lock screen launch setup
- `docs/playstore/` — Play Store materials (listing, privacy policy, screenshots)

### Technical
- `IMPLEMENTATION_LOG.md` — Milestone-by-milestone build log (M1-M44)
- `HANDOFF.md` — Quick reference for project state
- `CHANGELOG.md` — Version changelog (v0.1.0 - v0.8.0)
- `CLAUDE.md` — AI assistant context (project overview, rules, structure)
- `docs/adr/` — Architecture Decision Records
  - `001-pat-over-oauth.md` — PAT vs OAuth decision (updated with OAuth as primary)
  - `002-nepali-language-support.md` — Phased Nepali speech approach
  - `003-agenda-view-with-orgzly-architecture.md` — Database-centric agenda design
  - `004-high-fidelity-org-viewer.md` — AST-driven org viewer
  - `005-agenda-architecture-alternatives.md` — Comparison of agenda approaches
- `docs/research/orgzly-architecture-analysis.md` — Research on Orgzly's database design

---

## Build Commands

```bash
# Development
./gradlew assembleDebug            # Build debug APK
./gradlew installDebug              # Install on connected device
./gradlew clean                     # Clean build artifacts

# Release
./gradlew bundleRelease             # Build signed AAB (requires signing config)

# Testing
./gradlew test                      # Run unit tests
./gradlew connectedAndroidTest      # Run instrumentation tests (requires device)

# Deploy (automatic via GitHub Actions)
git push origin staging             # Deploy to Play Store internal testing
git push origin master              # Deploy to Play Store production
```

---

## File Counts

- **Total Kotlin files:** 81
- **UI components:** 18 (screens, viewmodels, theme, navigation)
- **Data layer:** 35 (entities, DAOs, repositories, API interfaces, models)
- **Background:** 2 (workers)
- **Speech:** 1 (SpeechRecognizerManager)
- **Auth:** 4 (AuthManager, OAuth components)
- **Assist:** 4 (VoiceInteractionService components)
- **DI:** 1 (AppModule)
- **App:** 4 (NoteApp, MainActivity, NoteCaptureActivity, OAuthCallbackActivity)

---

## Summary

The Note Taker app has evolved from a simple voice-to-GitHub tool (M1-M10) to a comprehensive org-mode note management system with agenda, recurring tasks, OAuth authentication, and offline support. The latest M44 milestone adds a production-ready agenda view with database-centric architecture that scales to thousands of notes.

**Key Strengths:**
- ✅ Production-ready codebase with clean architecture (MVVM, Repository pattern, Hilt DI)
- ✅ Comprehensive documentation (15+ docs files, 4 ADRs, detailed implementation log)
- ✅ Robust error handling (auth failures, network errors, conflicts)
- ✅ Security-first design (encrypted tokens, R8 minification, no debug logging in release)
- ✅ Automated CI/CD (three-branch pipeline to Google Play)
- ✅ Scalable agenda system (database-centric, handles thousands of notes)

**Next Priority:**
1. Wire up TODO state editing in Agenda (high user impact, 2-3 hours)
2. Lock screen agenda notification (Phase 4, 1 week)
3. Multi-repo support (1 week)

---

**Last Updated:** 2026-03-01
**By:** Claude Sonnet 4.5 (documentation assistant)
