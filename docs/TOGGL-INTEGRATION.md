# Toggl Track Integration

## Overview

Note Taker now includes **automatic time tracking** via Toggl Track API v9. This integration mirrors the Emacs `toggl.el` implementation, automatically starting and stopping timers based on TODO state changes.

**Version**: Implemented in v0.8.0+  
**Status**: ✅ Complete and Working

---

## Features

### Automatic Time Tracking

- **Start Timer**: Automatically starts a Toggl timer when a TODO task state changes **TO** `IN-PROGRESS`:
  - Any state → `IN-PROGRESS` (e.g., `TODO` → `IN-PROGRESS`, `DONE` → `IN-PROGRESS`)

- **Stop Timer**: Automatically stops the running Toggl timer when a task state changes **FROM** `IN-PROGRESS` to any other state:
  - `IN-PROGRESS` → Any other state (e.g., `IN-PROGRESS` → `DONE`, `IN-PROGRESS` → `TODO`)

**Note**: Only the `IN-PROGRESS` state triggers Toggl operations. Other states like `DOING` or `STARTED` do not affect time tracking.

### Task Metadata Support

- **Task Description**: Uses the headline title as the time entry description
- **Project Mapping**: Extracts `TOGGL_PROJECT_ID` property from headline to assign project
- **Tags**: Extracts org-mode tags (`:tag1:tag2:`) and sends them to Toggl
- **Fallback**: Uses default project if no `TOGGL_PROJECT_ID` property found

### Manual Project Selection (New in v0.8.1)

When changing a task status to `IN-PROGRESS`, users can **manually select a project** if:
- Toggl integration is enabled
- The task doesn't have a `TOGGL_PROJECT_ID` property
- Projects are available in the cache

**User Flow**:
1. User taps task in Agenda screen
2. User selects "IN-PROGRESS" status
3. **Project picker appears** showing all active Toggl projects
4. User selects a project and taps "Start Timer"
5. Timer starts with the selected project

**Key Features**:
- **Session-only selection**: Project choice applies to current timer only, not persisted
- **Embedded picker**: Smooth transition within state dialog (no dialog stacking)
- **Visual project indicators**: Small colored circles show Toggl project colors
- **Smart caching**: Projects fetched once on app start, cached in ViewModel
- **Back button**: Return to state selection if user changes mind

**UI Design**:
- Status chips: Bold colored chips with status as background (TODO=blue, IN-PROGRESS=purple)
- Project chips: Subtle surface background with colored circle indicators
- FlowRow layout: Chips wrap to multiple rows automatically
- Enhanced elevation: Selected items have prominent shadow (8dp tonal + 4dp shadow for status, 4dp for projects)

**When Project Picker DOESN'T Appear**:
- Task already has `TOGGL_PROJECT_ID` property → Uses saved project
- Toggl is disabled → Falls back to default behavior
- No projects available → Uses default project or no project

### Settings UI

- **API Token Configuration**: Secure input dialog with password masking
- **Enable/Disable Toggle**: Turn time tracking on/off without losing configuration
- **Project Sync**: Manual sync button to fetch projects from Toggl
- **Connection Status**: Visual indicator showing configuration state
- **Error Display**: Shows API errors and success messages

---

## Architecture

### Components

```
Toggl Integration
├── API Layer (data/api/TogglApi.kt)
│   ├── Retrofit interface for Toggl Track API v9
│   ├── Request/Response data models
│   └── Basic Auth implementation
│
├── Repository Layer (data/repository/TogglRepository.kt)
│   ├── Business logic for time entries
│   ├── Project syncing
│   └── Token validation
│
├── Preferences Layer (data/preferences/TogglPreferencesManager.kt)
│   ├── API token storage (EncryptedSharedPreferences)
│   ├── Workspace/project configuration (DataStore)
│   └── Enable/disable flags
│
├── Integration Hook (data/repository/AgendaRepository.kt)
│   ├── handleTogglStateChange() method
│   ├── Tracks active time entries by headline ID
│   ├── Accepts optional projectId parameter (session-only override)
│   ├── getNoteById() helper for project selection checks
│   └── Called after successful updateTodoState()
│
├── ViewModel Layer (ui/viewmodels/AgendaViewModel.kt)
│   ├── Caches Toggl projects on app init
│   ├── needsProjectSelection() checks if picker should appear
│   ├── updateTodoState() accepts optional projectId parameter
│   └── Exposes togglProjects and isTogglEnabled state
│
├── UI Layer (ui/screens/agenda/AgendaScreen.kt)
│   ├── StateSelectionDialog with embedded project picker
│   ├── FlowRow layout for status and project chips
│   ├── Project color indicators and selection state
│   └── LaunchedEffect to check project selection requirement
│
└── Settings UI (ui/screens/toggl/TogglSettingsCard.kt)
    ├── API token configuration dialog
    ├── Enable/disable toggle
    ├── Project sync button
    └── Connection status display
```

### Data Flow

#### Standard Flow (Task with TOGGL_PROJECT_ID)
```
User changes TODO state in Agenda
    ↓
AgendaRepository.updateTodoState(noteId, newState)
    ↓
State change persisted to database
    ↓
AgendaRepository.handleTogglStateChange(note, oldState, newState)
    ↓
Extract TOGGL_PROJECT_ID from note.properties
    ↓
TogglRepository.startTimeEntry(description, projectId, tags)
    ↓
Toggl Track API v9
    ↓
Time entry created/stopped in Toggl
```

#### Manual Project Selection Flow (Task without TOGGL_PROJECT_ID)
```
User taps task in Agenda
    ↓
StateSelectionDialog opens
    ↓
LaunchedEffect checks: viewModel.needsProjectSelection(noteId)
    ↓
User selects "IN-PROGRESS" status
    ↓
Dialog transitions to project picker (embedded FlowRow)
    ↓
User selects project from cached list
    ↓
User taps "Start Timer"
    ↓
AgendaRepository.updateTodoState(noteId, "IN-PROGRESS", projectId)
    ↓
State change persisted to database
    ↓
AgendaRepository.handleTogglStateChange(note, oldState, "IN-PROGRESS", projectId)
    ↓
projectId parameter takes precedence over TOGGL_PROJECT_ID property
    ↓
TogglRepository.startTimeEntry(description, projectId, tags)
    ↓
Toggl Track API v9
    ↓
Time entry created with selected project
    ↓
projectId NOT saved to database (session-only)
```

---

## API Integration

### Toggl Track API v9

**Base URL**: `https://api.track.toggl.com/api/v9/`

**Authentication**: Basic Auth
- Format: `Authorization: Basic base64(api_token:api_token)`
- Token stored in `EncryptedSharedPreferences` (Android Keystore-backed)

### Endpoints Used

| Endpoint | Method | Purpose |
|----------|--------|---------|
| `/me?with_related_data=true` | GET | Fetch user info and projects |
| `/workspaces/{workspace_id}/time_entries` | POST | Start time entry |
| `/workspaces/{workspace_id}/time_entries/{id}/stop` | PATCH | Stop time entry |
| `/me/time_entries/current` | GET | Get running time entry |

### Request Format (POST Time Entry)

```json
{
  "description": "Task headline title",
  "start": "2026-03-02T14:30:00Z",
  "duration": -1,
  "workspace_id": 8843824,
  "project_id": 123456,
  "tags": ["tag1", "tag2"],
  "created_with": "Note Taker Android App"
}
```

**Important**: 
- `duration: -1` indicates a running timer (not yet stopped)
- `start` must be ISO 8601 format in UTC
- `created_with` is **required** by API v9
- Fields with default values must be encoded (see Json configuration)

---

## Implementation Details

### Critical Fixes Applied

#### 1. JSON Serialization Configuration

**Problem**: Kotlinx.serialization by default skips fields with default values during encoding.

**Solution**: Added `encodeDefaults = true` to Json configuration in `AppModule.kt`:

```kotlin
fun provideJson(): Json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    encodeDefaults = true // Required for Toggl API
}
```

Without this, fields like `created_with`, `tags`, and `duration` were not sent, causing 400 errors.

#### 2. Request Body Structure

**Problem**: Initially tried wrapping request in `time_entry` key (common in other APIs).

**Solution**: Toggl API v9 expects **flat JSON structure** directly in request body (no wrapper):

```kotlin
@Serializable
data class StartTimeEntryRequest(
    val description: String,
    @SerialName("project_id") val projectId: Long? = null,
    val tags: List<String> = emptyList(),
    @SerialName("created_with") val createdWith: String = "Note Taker Android App",
    val start: String,
    val duration: Long = -1,
    @SerialName("workspace_id") val workspaceId: Long
)
```

#### 3. Dagger/Hilt Multiple Retrofit Instances

**Problem**: App uses both GitHub API and Toggl API, causing duplicate Retrofit bindings.

**Solution**: Created `@Qualifier` annotations to distinguish instances:

```kotlin
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class GitHubRetrofit

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class TogglRetrofit

@Provides
@GitHubRetrofit
fun provideGitHubRetrofit(...): Retrofit { ... }

@Provides
@TogglRetrofit
fun provideTogglRetrofit(...): Retrofit { ... }
```

#### 4. Non-Blocking Error Handling

**Problem**: Toggl API failures should not break TODO state changes.

**Solution**: All Toggl operations wrapped in `runCatching { }` with error logging:

```kotlin
val result = togglRepository.startTimeEntry(...)
result.onFailure { error ->
    Log.e(TAG, "Failed to start Toggl timer: ${error.message}")
    // State change already persisted - continue gracefully
}
```

---

## Configuration

### User Setup

1. **Get Toggl API Token**:
   - Visit https://track.toggl.com/profile
   - Scroll to "API Token" section
   - Copy token

2. **Configure in Note Taker**:
   - Open Settings screen
   - Scroll to "Toggl Track Time Tracking" section
   - Tap "Configure API Token"
   - Paste token
   - Tap "Save"
   - Enable time tracking toggle
   - Tap "Sync Projects" (optional)

3. **Workspace Auto-Detection**:
   - App fetches user's default workspace ID automatically on first API call
   - Stored in DataStore for future use

### Org-Mode Integration

#### Default Project

Set a default project ID in Settings (future feature) or let it default to `null` (no project assignment).

#### Per-Task Project Assignment

Add `TOGGL_PROJECT_ID` property to headline:

```org
* IN-PROGRESS Build Toggl integration
:PROPERTIES:
:TOGGL_PROJECT_ID: 123456789
:END:
```

When this task transitions to `IN-PROGRESS`, the timer will be assigned to project `123456789`.

#### Tags

Org-mode tags are automatically sent to Toggl:

```org
* IN-PROGRESS Fix bug in agenda view   :bug:urgent:
```

Toggl time entry will have tags: `["bug", "urgent"]`

---

## Security

### Token Storage

- **API Token**: Stored in `EncryptedSharedPreferences`
  - Backed by Android Keystore
  - Hardware-backed encryption on supported devices
  - Never logged or exposed in UI (password-masked input)

- **Configuration**: Stored in DataStore (Preferences DataStore)
  - Workspace ID
  - Default project ID
  - Enable/disable flags
  - Last sync timestamp

### HTTP Security

- **OkHttp Logging**: Body logging enabled in DEBUG builds only
- **Release Builds**: No HTTP logging, ProGuard/R8 minification enabled
- **HTTPS Only**: All API calls use TLS 1.2+ (enforced by OkHttp defaults)

---

## Testing

### Manual Testing Checklist

- [x] Start timer: TODO → IN-PROGRESS
- [x] Stop timer: IN-PROGRESS → TODO
- [x] Stop timer: IN-PROGRESS → DONE
- [x] Multiple state changes on same task
- [x] Project ID from `TOGGL_PROJECT_ID` property
- [x] Tags extraction from headline
- [x] No project ID (uses default)
- [x] API token validation
- [x] Network error handling
- [x] Auth failure (401/403)
- [x] Toggl API errors (400, 500)
- [x] Offline behavior (graceful failure)
- [x] Settings UI configuration flow

### Unit Tests

Tests updated to mock `TogglRepository`:

- `AgendaConfigurationTest.kt` - Added `TogglRepository` mock
- `AgendaDataSourceConsistencyTest.kt` - Added `TogglRepository` mock

Future: Add dedicated `TogglRepositoryTest.kt` for time entry logic.

---

## Troubleshooting

### Common Issues

#### 1. "Failed to start time entry: 400"

**Causes**:
- `created_with` not sent → Check `encodeDefaults = true` in `AppModule.kt`
- `start` not sent or invalid format → Verify ISO 8601 format: `2026-03-02T14:30:00Z`
- Invalid `workspace_id` → Check token is valid and workspace exists

**Solution**: Check ADB logs for detailed error body:
```bash
adb logcat -d | grep "TogglRepository"
```

#### 2. "No Toggl API token configured"

**Cause**: User hasn't set up API token yet.

**Solution**: Go to Settings → Toggl Track → Configure API Token

#### 3. Timer not starting automatically

**Checks**:
- Is time tracking enabled in Settings? (toggle must be ON)
- Is task state changing to `IN-PROGRESS`, `DOING`, or `STARTED`?
- Check ADB logs: `adb logcat -d | grep "handleTogglStateChange"`

#### 4. "Failed to stop time entry: no running timer"

**Cause**: Expected behavior when no timer is running.

**Solution**: This is a non-fatal warning. App checks for running timer and stops it if found.

### Debug Commands

```bash
# Watch Toggl logs in real-time
adb logcat | grep -E "(Toggl|handleTogglStateChange)"

# Get recent Toggl errors
adb logcat -d | grep -E "(TogglRepository|Failed to)" | tail -50

# Check if time tracking is enabled
adb logcat -d | grep "Toggl isEnabled"

# See full HTTP requests/responses (DEBUG builds only)
adb logcat -d | grep "OkHttp"
```

---

## Future Enhancements

### Planned Features

1. **Project Picker UI** (v0.9.0)
   - Settings screen shows list of projects
   - Set default project from UI
   - Per-task project assignment in org-mode editor

2. **Active Timer Display** (v0.9.0)
   - Show timer duration in agenda item UI
   - Real-time countdown/countup
   - Visual indicator for tasks with running timers

3. **Offline Queue** (v1.0.0)
   - Queue failed time entry operations
   - Retry with WorkManager when network available
   - Similar to note upload queue

4. **Sync Time Entries Back to Org** (v1.0.0)
   - Fetch completed time entries from Toggl
   - Write to `:LOGBOOK:` drawer in org files
   - Format: `CLOCK: [2026-03-02 Sun 14:30]--[2026-03-02 Sun 15:45] =>  1:15`

5. **Statistics Dashboard** (v1.1.0)
   - Show total time tracked today/week
   - Time by project breakdown
   - Integration with agenda view

### Optional Enhancements

- **Auto-stop on phone lock** (configurable)
- **Pomodoro timer integration**
- **Toggl workspace selection** (for users with multiple workspaces)
- **Billable flag** (extract from org property)
- **Description templates** (e.g., prepend project name)

---

## Related Documentation

- **Toggl Track API v9 Docs**: https://developers.track.toggl.com/docs/api/time_entries
- **Emacs `toggl.el`**: Reference implementation for auto-tracking behavior
- **ADR 003**: Agenda View with Orgzly Architecture (explains TODO state management)
- **REQUIREMENTS.md**: Feature requirements (FR1-FR15)
- **CLAUDE.md**: Project overview and AI assistant context

---

## File Reference

### Core Implementation

| File | Purpose | Lines |
|------|---------|-------|
| `app/src/main/kotlin/com/rrimal/notetaker/data/api/TogglApi.kt` | Retrofit API interface and data models | 100 |
| `app/src/main/kotlin/com/rrimal/notetaker/data/repository/TogglRepository.kt` | Business logic for time tracking | 230 |
| `app/src/main/kotlin/com/rrimal/notetaker/data/preferences/TogglPreferencesManager.kt` | Configuration storage | ~150 |
| `app/src/main/kotlin/com/rrimal/notetaker/data/repository/AgendaRepository.kt` | Integration hook (`handleTogglStateChange`) | +100 lines |
| `app/src/main/kotlin/com/rrimal/notetaker/ui/screens/toggl/TogglSettingsCard.kt` | Settings UI component | ~200 |
| `app/src/main/kotlin/com/rrimal/notetaker/ui/viewmodels/TogglSettingsViewModel.kt` | Settings UI state management | ~150 |
| `app/src/main/kotlin/com/rrimal/notetaker/di/AppModule.kt` | Dagger/Hilt dependency injection | +50 lines |

### Modified Files

| File | Changes |
|------|---------|
| `app/src/main/kotlin/com/rrimal/notetaker/ui/screens/SettingsScreen.kt` | Added `TogglSettingsCard` component |
| `app/src/test/kotlin/com/rrimal/notetaker/unit/repository/AgendaConfigurationTest.kt` | Added `TogglRepository` mock |
| `app/src/test/kotlin/com/rrimal/notetaker/unit/repository/AgendaDataSourceConsistencyTest.kt` | Added `TogglRepository` mock |
| `app/src/main/kotlin/com/rrimal/notetaker/ui/viewmodels/AgendaViewModel.kt` | Added project caching, needsProjectSelection(), projectId parameter (v0.8.1) |
| `app/src/main/kotlin/com/rrimal/notetaker/data/repository/AgendaRepository.kt` | Added optional projectId parameter, getNoteById() helper (v0.8.1) |
| `app/src/main/kotlin/com/rrimal/notetaker/ui/screens/agenda/AgendaScreen.kt` | Added embedded project picker, FlowRow layout for chips (v0.8.1) |

---

## UI/UX Design (Manual Project Selection)

### StateSelectionDialog

The state selection dialog uses a two-screen approach with smooth transitions:

#### Screen 1: Status Selection
```
┌─────────────────────────────────┐
│      Change Status              │
├─────────────────────────────────┤
│  Current: TODO                  │
│                                 │
│  [TODO ✓]  [IN-PROGRESS]        │
│  [WAITING] [HOLD]               │
│  [DONE]    [CANCELLED]          │
│                                 │
│                     [Cancel]    │
└─────────────────────────────────┘
```

**Design Details**:
- **Layout**: FlowRow with 8dp spacing (horizontal & vertical)
- **Chip Style**: Single Surface with status color as background
- **Colors**: Bold semantic colors (TODO=primary, IN-PROGRESS=tertiary, DONE=secondaryContainer, CANCELLED=error)
- **Padding**: 16dp horizontal × 12dp vertical per chip
- **Elevation**: Selected items: 8dp tonal + 4dp shadow; Normal: 2dp tonal
- **Typography**: labelLarge, Bold for selected, Medium for normal
- **Checkmark**: White/contrasting color, shown on current state

#### Screen 2: Project Selection (Only when needed)
```
┌─────────────────────────────────┐
│      Select Project             │
├─────────────────────────────────┤
│  Select a project for this      │
│  timer:                         │
│                                 │
│  [● Work ✓]  [● Personal]       │
│  [● Project X] [● Client Y]     │
│                                 │
│  [Back]         [Start Timer]   │
└─────────────────────────────────┘
```

**Design Details**:
- **Layout**: FlowRow with 8dp spacing (horizontal & vertical)
- **Chip Style**: Surface background with small colored circle indicator
- **Color Indicator**: 10dp circle showing Toggl project color
- **Background**: primaryContainer (selected) or surface (normal)
- **Padding**: 16dp horizontal × 12dp vertical per chip
- **Elevation**: Selected: 4dp tonal; Normal: 0dp
- **Typography**: bodyMedium, Bold for selected, Medium for normal
- **Checkmark**: Primary color, shown on selected project
- **Buttons**: "Back" returns to status selection, "Start Timer" enabled only when project selected

### Dialog Sizing

**Constraints**:
- Minimum: 280dp width × 200dp height
- Maximum: 560dp width × 600dp height
- **Result**: Dialog appears centered, scales with content

### Transition Logic

```kotlin
when {
    state == "IN-PROGRESS" && needsProjectSelection && togglProjects.isNotEmpty() 
        → Show project picker
    
    state != currentState
        → Change state immediately
    
    else
        → Do nothing
}
```

### Visual Hierarchy

**Status Chips** (Bold & Colorful):
- Purpose: Draw attention, semantic meaning
- Use case: Limited states (6 total), quick identification
- Design: Full color background, high contrast

**Project Chips** (Subtle & Clean):
- Purpose: Readable text, color as accent
- Use case: Many projects (potentially 50+), readability priority
- Design: Neutral background, small color indicator

---

## Changelog

### v0.8.1 (2026-03-02)

**Added**:
- ✅ Manual project selection UI for tasks without TOGGL_PROJECT_ID
- ✅ Embedded project picker in StateSelectionDialog
- ✅ Project caching in AgendaViewModel (fetched on app start)
- ✅ FlowRow layout for status and project chips
- ✅ Session-only project selection (not persisted)
- ✅ Smart detection: Only shows picker when needed
- ✅ Visual project color indicators (10dp circles)
- ✅ Enhanced chip design with proper elevation

**Modified**:
- AgendaViewModel: Added TogglRepository injection and project caching
- AgendaRepository: Added optional projectId parameter to updateTodoState()
- StateSelectionDialog: Added embedded project picker with FlowRow layout
- AgendaScreen: Added project selection requirement checking

**Technical Details**:
- Projects cached in ViewModel state (fetched once on init)
- Override projectId takes precedence over TOGGL_PROJECT_ID property
- Non-blocking: Falls back gracefully if Toggl disabled or no projects
- Compose FlowRow for automatic chip wrapping

### v0.8.0 (2026-03-02)

**Added**:
- ✅ Toggl Track API v9 integration
- ✅ Automatic timer start/stop on TODO state changes
- ✅ Settings UI for API token configuration
- ✅ Project and tag extraction from org-mode
- ✅ Secure token storage (EncryptedSharedPreferences)
- ✅ Non-blocking error handling
- ✅ OkHttp logging for debugging

**Fixed**:
- ✅ Json serialization: Added `encodeDefaults = true`
- ✅ Request body structure: Flat JSON (no wrapper)
- ✅ Dagger/Hilt: Multiple Retrofit instances with qualifiers
- ✅ Unit tests: Mocked `TogglRepository` in agenda tests

**Technical Debt**:
- TODO: Add dedicated `TogglRepositoryTest.kt` unit tests
- TODO: Add offline queue for failed time entry operations
- ~~TODO: Implement project picker UI~~ ✅ Completed in v0.8.1

---

## Credits

**Implementation**: Ram Sharan Rimal (rimal.ram25@gmail.com)  
**Inspired by**: Emacs `toggl.el` package  
**API Provider**: Toggl Track (https://toggl.com)  
**Date**: 2026-03-02

---

*Last Updated: 2026-03-02*  
*Status: ✅ Complete and Working*
