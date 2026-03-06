# Changelog

## v0.9.0 (Unreleased)

**What's New**
- **Instant Tasks (Quick Task)** — Create and start tasks directly from the agenda with an elegant bottom sheet UI
- **Quick.org in Agenda View** — Instant tasks from `quick.org` appear alongside scheduled items in the daily agenda
- **Pomodoro Auto-HOLD** — Stopping a Pomodoro early automatically sets the task to HOLD
- **Phone Time Tracking (Phase 1)** — Track work sessions directly from your phone's agenda view

**Instant Task Improvements**
- **Elegant Bottom Sheet** — Replaced the old dialog with a `ModalBottomSheet`: large "What are you working on?" input, project chips, Pomodoro toggle, and Start button
- **Expandable Details** — Tap "+ Add details" to reveal an optional description field (collapsed by default to keep the UI minimal)
- **Fixed Race Condition** — `saveQuickTask()` now calls `syncQuickFile()` instead of `clearAndResyncAll()`, so the task is immediately available for Pomodoro start
- **Quick.org Always Visible** — `quick.org` tasks are shown in the agenda alongside user-configured org files without needing to add it to settings
- **Direct File Updates** — State changes on quick.org tasks update the file directly (no JSON sync) since Emacs does not manage `quick.org`

**Pomodoro Improvements**
- **Auto-HOLD on Early Stop** — Pressing ⏹ during a focus session sets the linked task to HOLD (not on breaks, and not on natural completion)

**Bug Fixes**
- **UUID Regex Fix** — `isValidSyncFilename()` now accepts lowercase hex UUIDs (`[A-Fa-f0-9]`); Android generates lowercase UUIDs, so the old regex silently rejected ALL TODO state changes via JSON sync
- **Quick Tasks Skip JSON Sync** — State changes on `quick.org` tasks no longer write `sync/*.json` files (they were orphaned since Emacs doesn't watch `quick.org`)

**Time Tracking Features**
- **Automatic Time Recording** — When you cycle a task to IN-PROGRESS, app records start time in org properties
- **Session Duration Display** — IN-PROGRESS tasks show "Active: Xh Ym" with timer icon
- **Org-Mode Integration** — Records timestamps in `PHONE_STARTED` and `PHONE_ENDED` properties
- **Simple Property Format** — Phone writes simple properties; Emacs can convert to LOGBOOK entries
- **Multiple Sessions Support** — Phase 1 creates numbered properties (PHONE_STARTED_2, etc.) for multiple work periods

**Technical Details**
- Added `properties` field to `NoteEntity` with TypeConverter for Map<String, String>
- Database migration v4 → v5 adds properties column
- Org-mode timestamp format: `[YYYY-MM-DD Day HH:mm]` (e.g., `[2026-03-02 Sun 14:00]`)
- Active states: IN-PROGRESS, DOING, STARTED trigger time tracking
- Duration calculation updates in real-time during recomposition
- `AgendaRepository.syncQuickFile()` — syncs only `quick.org` without touching agenda DB
- `AgendaRepository.updateQuickTaskStateInFile()` — updates `quick.org` directly using `CREATED` property as stable key
- `AgendaViewModel.stopPomodoroEarly()` — stops timer and sets task to HOLD (focus sessions only)
- See `docs/PHONE-TIME-TRACKING-PLAN.md` for full implementation details

## v0.8.0

**What's New**
- **Swipeable Navigation & UX Redesign** — Complete navigation overhaul with gesture-based interface
- **Agenda as Default Home Screen** — Agenda opens by default instead of dictation
- **Minimalist Inbox Capture** — Redesigned with expandable sections and cleaner UI
- **Org-Mode Agenda View (Phase 1-2)** — Full agenda system for viewing scheduled and deadline items from local org files

**Swipeable Navigation**
- **HorizontalPager** with 3 screens: Dictation | Agenda (default) | Inbox Capture
- **Gesture-based**: Swipe left/right to navigate between screens
- **Visual indicators**: Animated page indicators at bottom with current page labels
- **Simplified top bars**: Removed redundant navigation buttons (use swipe instead)
- **Lock screen unchanged**: Side button still launches dictation directly

**Minimalist Inbox Capture**
- Clean interface with mic icon and "What needs to be done?" prompt
- Single title input with voice button (🎤) for quick entry
- **Expandable sections**:
  - "+ Details" for optional description text
  - "+ Schedule" for quick date selection (Today, Tomorrow, date picker)
- Auto-collapse expanded sections after successful submit
- Large "Add Task" button with visual feedback (Adding..., Added!, Queued)

**Agenda Features**
- **Bucketed List View** — Group items by day: Today, Tomorrow, and upcoming days.
- **Overdue Items** — Dedicated section for items with past SCHEDULED or DEADLINE timestamps.
- **Recurring Tasks** — Full support for org-mode repeaters (`+1d`, `++1w`, `.+1m`, etc.) with efficient in-memory expansion.
- **Fast Search & Filter** — Uses a normalized Room database for instant queries across thousands of notes.
- **Agenda Configuration** — New Settings section to manage agenda files, range (1-30 days), and TODO keywords.
- **Background Sync** — Automatic syncing of configured .org files to the local database via WorkManager.
- **Visual Polish** — TODO state chips, colored timestamp labels, tags, and sticky day headers.

**Technical Details**
- Normalized database schema (notes, timestamps, planning) based on Orgzly's architecture.
- SHA-256 hashing for file change detection during sync.
- AST-driven parsing via `OrgParser` and `OrgTimestampParser`.
- See ADR 003 for full architecture details.

## v0.7.0

**What's New**
- **Nepali Language Support** — Manual language switching between English and Nepali for speech recognition
- **High-Fidelity Org-Mode Viewer** — Beautiful rendering of .org files with folding, colors, and structured display

**Language Switching**
- Toggle button to switch between English (🇺🇸) and Nepali (🇳🇵) speech recognition
- Language preference saved and remembered across sessions
- Works with Android's built-in speech recognition (requires internet connection)
- Language indicator shown during voice input with current language
- Tap the language chip to switch between languages
- Language can be changed while actively listening (recognizer auto-restarts)

**Technical Details**
- Uses `RecognizerIntent.EXTRA_LANGUAGE` with "en-US" and "ne-NP" language codes
- Language preference stored securely in EncryptedSharedPreferences
- Default language: English (en-US)
- Supports Devanagari script output (नेपाली)
- See ADR 002 for implementation details and future phases (transliteration, Whisper integration)

**Org-Mode Viewer** (Phase 1 Complete)
- Browse screen now renders .org files with high-fidelity structured display
- Visual hierarchy with level-based color coding (blue, green, yellow, red for different levels)
- Collapsible headlines - tap to expand/collapse sections
- Colored TODO state chips (TODO=red, DONE=green, IN-PROGRESS=yellow, etc.)
- Priority badges displayed prominently ([#A], [#B], [#C])
- Tags shown on the right (`:work:urgent:`)
- Planning lines rendered with colors (SCHEDULED=green, DEADLINE=red, CLOSED=gray)
- Collapsible property drawers (`:PROPERTIES:...`:END:`)
- Full support for nested headlines with proper indentation
- Recursive rendering handles any depth of nesting
- Inspired by Orgro's viewer architecture, optimized for Android/Compose
- Plain text edit mode still available via Edit button
- See ADR 004 for full architecture and future phases (inline markup, editing features)

## v0.6.0

**What's New**
- **Inbox Capture** — Dedicated TODO capture with title + description fields
- **Local Org Files Support** — Full local file storage with org-mode formatting
- **Improved Dictation Format** — First sentence as heading, rest as body
- **Subdirectory Support** — Organize notes in folders (e.g., Brain/inbox.org)

**Inbox Capture**
- New ✓ icon in top bar opens inbox capture screen
- Two-field interface: Title (required) and Description (optional)
- Appends all entries to a single configurable inbox file
- Automatic TODO state with Emacs-style timestamp format `[YYYY-MM-DD DDD HH:mm]`
- Description automatically formatted as bullet points
- Standard org-mode preamble (#+STARTUP, #+FILETAGS, #+PROPERTY)
- Configure inbox file path in Settings (supports subdirectories)

**Local Org Files Storage**
- Complete local file storage backend using Android Storage Access Framework
- Choose any folder on your device for note storage
- Two capture modes work independently:
  - Voice dictation → Creates new timestamped .org files in capture folder
  - Inbox capture → Appends TODO entries to single inbox file
- Automatic detection based on input method (no mode switching needed)
- Full subdirectory support for organizing notes (Brain/, Work/, etc.)

**Better Dictation Format**
- First sentence automatically becomes the headline
- Remaining text becomes the body content
- Smart sentence detection (handles ., ?, ! with proper spacing)
- Fallback logic for content without sentence endings:
  - Multiple lines → First line as title, rest as body
  - Long single line → First 80 chars as title, full content in body
  - Short single line → Entire text as title, no body
- Maximum headline length: 200 characters

**Settings Improvements**
- "Inbox Configuration" section for configuring TODO capture
- Inbox file path supports subdirectories (inbox.org, Brain/inbox.org, Work/todos.org)
- Clear descriptions for each capture method
- Capture folder configuration for dictation notes
- Visual indicators showing where each type of note will be saved

**Technical Improvements**
- Proper file path handling for subdirectories
- Fixed duplicate file creation bug in inbox append
- Improved file existence checking before create/update operations
- Path parsing for nested folder structures
- Optimized release build (5.2MB vs 67MB debug build)

## v0.5.2

**Bug Fix**
- Fixed OAuth sign-in on Play Store builds — renamed secrets from `GITHUB_CLIENT_ID` to `OAUTH_CLIENT_ID` (GitHub reserves the `GITHUB_` prefix for its own use, silently blocking secret creation) and switched CI to write credentials to `local.properties` instead of env vars

## v0.5.1

**Bug Fix**
- Attempted fix for OAuth sign-in on Play Store builds — added env vars to CI but the `GITHUB_`-prefixed secrets could not be created (see v0.5.2)

## v0.5.0

**What's New**
- Settings restructured into "Device Connection" and "Note Taker on GitHub" cards
- "Disconnect" replaces "Sign Out" — honest about what it does
- One-tap reconnect after disconnecting (no need to reinstall the GitHub App)
- Repo selection dialog when Note Taker has access to multiple repositories
- Session expired errors when your token is revoked or invalid
- "Manage on GitHub" button to change repos or uninstall Note Taker

**Clearer Settings**
- "Device Connection" card shows your username, repo, and auth method with a Disconnect button
- Helper text explains that disconnecting only removes credentials from this device and reconnecting is one tap
- "Note Taker on GitHub" card (OAuth only) describes exact permissions — read & write file contents, nothing else
- "Manage on GitHub" opens GitHub's installation settings to change repos or uninstall

**Disconnect Flow**
- "Disconnect" terminology replaces "Sign Out" — this device disconnects, Note Taker stays on GitHub
- Confirmation dialog warns about unsent notes (all auth types)
- OAuth token revoked on GitHub's side
- Reconnecting is one tap since the GitHub App stays installed

**Auth Improvements**
- Repo selection dialog when the GitHub App has access to multiple repos
- Session expired errors on note submit and browse when token is invalid
- Worker stops retrying uploads on auth failure instead of looping
- Better error messages for OAuth edge cases (stale installation, no repos)

## v0.4.0

**What's New**
- Sign in with GitHub (OAuth) — one tap to connect, no tokens to manage
- Redesigned setup screen with a cleaner two-step card layout
- Help icons explain the notes repo template and GitHub permissions
- Sign-out revokes access and prompts to uninstall the GitHub App

**GitHub OAuth**
- "Sign in with GitHub" as the primary auth method — installs the Note Taker GitHub App on one repo you choose
- PKCE-protected OAuth flow with EncryptedSharedPreferences token storage
- Personal Access Token remains available as a manual fallback

**Setup Screen Redesign**
- Two clear cards: "1. Create Your Notes Repo" and "2. Connect Your Repo"
- Each card has a description and a single action button
- Help icon on card 1 explains the template repo and the Claude Code inbox processor agent
- Help icon on card 2 explains exactly what permissions Note Taker gets (read/write one repo, nothing else)
- PAT flow toggles inline within the connect card instead of expanding a separate section
- Updated tagline: "Your voice notes, saved to Git, organized by AI."

**Clean Sign-Out**
- OAuth sign-out shows a confirmation dialog explaining that the GitHub App will remain installed
- "Uninstall from GitHub" button opens GitHub Settings to fully remove the app
- Access token is revoked on GitHub's side when you sign out
- PAT users sign out immediately with no changes to their experience

## v0.3.0

**What's New**
- "Need help?" link on the setup screen opens a YouTube walkthrough video
- Settings now walks you through both steps to enable the side button shortcut

**Setup Help Video**
- Added a "Need help? Watch the setup walkthrough" link at the bottom of the auth screen
- Opens a YouTube video that walks through the full setup process

**Side Button Setup Guide**
- The Digital Assistant settings card now has two clearly numbered steps
- Step 1: Set Note Taker as your default digital assistant (existing)
- Step 2: Change your side button's long-press from Bixby to Digital assistant (new)
- "Open Side Button Settings" takes you directly to Samsung's side key settings
- Graceful fallback on non-Samsung devices

## v0.2.0

**What's New**
- Step-by-step setup walkthrough with help icons and PAT instructions
- Distinct error messages for invalid token vs missing repository
- Note input field expands to fill the screen with a scrollbar on overflow
- First-launch dialog introduces the side-button shortcut
- Settings accessible from every screen

**Guided Setup Flow**
- Redesigned the auth screen as a clear 4-step walkthrough: fork the notes repo, enter your repository, generate a token, paste it in
- Each step is numbered with help icons that explain what to enter and how your token is stored
- "Generate Token on GitHub" now shows detailed instructions before opening the browser
- Repository field accepts `owner/repo` or a full GitHub URL — no more guessing the format

**Better Error Messages**
- Invalid token and wrong repository now show distinct error messages instead of a generic failure
- "Personal access token is invalid" vs "Repository not found — check the name and token permissions"

**Digital Assistant Onboarding**
- First-time dialog explains the side-button shortcut and offers to open system settings to enable it
- Dismissed permanently until you sign out and back in

**Growing Text Field**
- Note input field now expands to fill available screen space instead of a fixed height
- Scrollbar appears when text overflows and fades out after scrolling

**Settings Access**
- Settings gear icon added to the Browse screen — accessible from every authenticated screen
