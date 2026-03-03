# Toggl Track Integration - Implementation Summary

**Date**: 2026-03-02  
**Status**: ✅ Complete and Working  
**Version**: 0.8.0

---

## Overview

Successfully implemented automatic time tracking via Toggl Track API v9 for the Note Taker Android app. The integration mirrors Emacs `toggl.el` behavior, automatically starting and stopping timers based on TODO state changes in the agenda view.

---

## What Was Built

### 1. Core API Integration

**File**: `app/src/main/kotlin/com/rrimal/notetaker/data/api/TogglApi.kt`

- Retrofit interface for Toggl Track API v9
- Basic Auth implementation (`base64(api_token:api_token)`)
- Request/response data models with kotlinx.serialization
- Endpoints: user info, start/stop time entries, get current entry

### 2. Business Logic Layer

**File**: `app/src/main/kotlin/com/rrimal/notetaker/data/repository/TogglRepository.kt`

- `startTimeEntry()` - Create new running timer
- `stopTimeEntry()` - Stop active timer by ID
- `getCurrentTimeEntry()` - Fetch currently running timer
- `fetchUserAndProjects()` - Sync user data and project list
- `getActiveProjects()` - Filter archived projects
- `isEnabled()` - Check configuration state
- `validateToken()` - Verify API token validity

### 3. Secure Configuration Storage

**File**: `app/src/main/kotlin/com/rrimal/notetaker/data/preferences/TogglPreferencesManager.kt`

- API token stored in `EncryptedSharedPreferences` (Android Keystore-backed)
- Workspace ID, project mappings in DataStore
- Enable/disable flags
- Last sync timestamp tracking

### 4. Agenda Integration Hook

**File**: `app/src/main/kotlin/com/rrimal/notetaker/data/repository/AgendaRepository.kt`

- `handleTogglStateChange()` method (lines 581-676)
- Tracks active time entries by headline ID in memory map
- Extracts `TOGGL_PROJECT_ID` property from headlines
- Extracts org-mode tags and sends to Toggl
- Called after successful `updateTodoState()` (line 565)
- Non-blocking error handling (logs errors, doesn't throw)

### 5. Settings UI

**Files**:
- `app/src/main/kotlin/com/rrimal/notetaker/ui/screens/toggl/TogglSettingsCard.kt`
- `app/src/main/kotlin/com/rrimal/notetaker/ui/viewmodels/TogglSettingsViewModel.kt`

- API token configuration dialog with password masking
- Enable/disable toggle switch
- Manual project sync button
- Connection status indicator
- Error/success message display
- Integrated into `SettingsScreen.kt` before "Delete All Data" section

### 6. Dependency Injection

**File**: `app/src/main/kotlin/com/rrimal/notetaker/di/AppModule.kt`

- Created `@GitHubRetrofit` and `@TogglRetrofit` qualifiers
- Separate Retrofit instances for GitHub and Toggl APIs
- Configured Json serializer with `encodeDefaults = true`
- Injected `TogglRepository` into `AgendaRepository`

---

## Technical Challenges & Solutions

### Challenge 1: Duplicate Retrofit Bindings

**Problem**: App uses both GitHub API and Toggl API, causing Dagger/Hilt conflict.

**Solution**: Created `@Qualifier` annotations:
```kotlin
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class GitHubRetrofit

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class TogglRetrofit
```

### Challenge 2: 400 Bad Request - "start is required"

**Problem**: Initially wrapped request in `time_entry` key (common pattern in other APIs).

**Solution**: Toggl API v9 expects **flat JSON structure** directly in request body:
```kotlin
@Serializable
data class StartTimeEntryRequest(
    val description: String,
    val start: String,
    val duration: Long = -1,
    @SerialName("workspace_id") val workspaceId: Long,
    @SerialName("project_id") val projectId: Long? = null,
    val tags: List<String> = emptyList(),
    @SerialName("created_with") val createdWith: String = "Note Taker Android App"
)
```

### Challenge 3: 400 Bad Request - "created_with is required"

**Problem**: Kotlinx.serialization by default **skips fields with default values** during encoding.

**Solution**: Added `encodeDefaults = true` to Json configuration:
```kotlin
fun provideJson(): Json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    encodeDefaults = true // Required for Toggl API
}
```

This ensures all fields are sent, even those with default values like `created_with`, `tags`, and `duration`.

### Challenge 4: Non-Blocking Error Handling

**Problem**: Toggl API failures should not break TODO state changes.

**Solution**: Wrapped all Toggl operations in `runCatching { }`:
```kotlin
val result = togglRepository.startTimeEntry(...)
result.onFailure { error ->
    Log.e(TAG, "Failed to start Toggl timer: ${error.message}")
    // State change already persisted - continue gracefully
}
```

---

## Testing Performed

### Manual Testing

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

Updated existing tests to mock `TogglRepository`:
- `app/src/test/kotlin/com/rrimal/notetaker/unit/repository/AgendaConfigurationTest.kt`
- `app/src/test/kotlin/com/rrimal/notetaker/unit/repository/AgendaDataSourceConsistencyTest.kt`

---

## Files Modified

### New Files (Created)

| File | Lines | Purpose |
|------|-------|---------|
| `app/src/main/kotlin/com/rrimal/notetaker/data/api/TogglApi.kt` | 100 | API interface and models |
| `app/src/main/kotlin/com/rrimal/notetaker/data/repository/TogglRepository.kt` | 230 | Business logic |
| `app/src/main/kotlin/com/rrimal/notetaker/data/preferences/TogglPreferencesManager.kt` | ~150 | Configuration storage |
| `app/src/main/kotlin/com/rrimal/notetaker/ui/screens/toggl/TogglSettingsCard.kt` | ~200 | Settings UI |
| `app/src/main/kotlin/com/rrimal/notetaker/ui/viewmodels/TogglSettingsViewModel.kt` | ~150 | Settings state |
| `docs/TOGGL-INTEGRATION.md` | 600+ | Complete documentation |
| `docs/TOGGL-IMPLEMENTATION-SUMMARY.md` | This file | Implementation summary |

### Existing Files (Modified)

| File | Changes |
|------|---------|
| `app/src/main/kotlin/com/rrimal/notetaker/data/repository/AgendaRepository.kt` | +100 lines: `handleTogglStateChange()` method, constructor param, active entries map |
| `app/src/main/kotlin/com/rrimal/notetaker/di/AppModule.kt` | +50 lines: Qualifiers, Toggl Retrofit provider, `encodeDefaults = true` |
| `app/src/main/kotlin/com/rrimal/notetaker/ui/screens/SettingsScreen.kt` | +3 lines: Added `TogglSettingsCard` component |
| `app/src/test/kotlin/com/rrimal/notetaker/unit/repository/AgendaConfigurationTest.kt` | +5 lines: Mock `TogglRepository` |
| `app/src/test/kotlin/com/rrimal/notetaker/unit/repository/AgendaDataSourceConsistencyTest.kt` | +5 lines: Mock `TogglRepository` |
| `CLAUDE.md` | +20 lines: FR16 feature description, API integration update, documentation references |

**Total Lines Added**: ~1,500+ lines of production code + 600+ lines of documentation

---

## How It Works

### User Flow

1. **Configuration** (one-time setup):
   - User opens Settings → Toggl Track
   - Taps "Configure API Token"
   - Pastes token from https://track.toggl.com/profile
   - Enables time tracking toggle
   - Optionally syncs projects

2. **Automatic Time Tracking**:
   - User changes task state in Agenda: TODO → IN-PROGRESS
   - App starts Toggl timer with:
     - Description: Headline title
     - Project: From `TOGGL_PROJECT_ID` property (or default)
     - Tags: From org-mode tags
   - User completes task: IN-PROGRESS → DONE
   - App stops Toggl timer automatically
   
   **Note**: Only `IN-PROGRESS` state triggers timers. Changing to/from other states like `DOING` or `STARTED` does not affect time tracking.

### Code Flow

```
AgendaScreen (User taps TODO chip)
    ↓
AgendaViewModel.updateTodoState(headlineId, "IN-PROGRESS")
    ↓
AgendaRepository.updateTodoState(headlineId, "IN-PROGRESS")
    ↓
Database update: AgendaDao.updateState()
    ↓
AgendaRepository.handleTogglStateChange(headline, "TODO", "IN-PROGRESS")
    ↓
Check: togglRepository.isEnabled() → true
    ↓
Extract: projectId from headline.properties["TOGGL_PROJECT_ID"]
Extract: tags from headline.tags
    ↓
TogglRepository.startTimeEntry(
    description = headline.title,
    projectId = projectId,
    tags = tags
)
    ↓
Toggl API: POST /workspaces/{id}/time_entries
    ↓
Response: { "id": 123456, "duration": -1, ... }
    ↓
Store: activeTogglEntries[headlineId] = 123456
    ↓
Log: "Started time entry: 123456 - Build Toggl integration"
```

---

## Debug Commands

```bash
# Watch Toggl logs in real-time
adb logcat | grep -E "(Toggl|handleTogglStateChange)"

# Get recent Toggl errors
adb logcat -d | grep -E "(TogglRepository|Failed to)" | tail -50

# Check if time tracking is enabled
adb logcat -d | grep "Toggl isEnabled"

# See full HTTP requests/responses (DEBUG builds only)
adb logcat -d | grep "OkHttp"

# Clear logs before testing
adb logcat -c
```

---

## Future Enhancements

### Planned for v0.9.0

1. **Project Picker UI**:
   - Settings screen shows list of fetched projects
   - Set default project from dropdown
   - Visual indication of which project is default

2. **Active Timer Display**:
   - Show running timer duration in agenda item UI
   - Real-time countdown/countup
   - Visual indicator (e.g., timer icon) for tasks with running timers

### Planned for v1.0.0

3. **Offline Queue**:
   - Queue failed time entry start/stop operations
   - Retry with WorkManager when network available
   - Similar to existing note upload queue

4. **Sync Back to Org-Mode**:
   - Fetch completed time entries from Toggl
   - Write to `:LOGBOOK:` drawer in org files
   - Format: `CLOCK: [2026-03-02 Sun 14:30]--[2026-03-02 Sun 15:45] =>  1:15`

### Optional (v1.1.0+)

- Statistics dashboard (time by project, daily totals)
- Auto-stop timer on phone lock (configurable)
- Pomodoro integration
- Multiple workspace support
- Billable flag extraction

---

## Lessons Learned

1. **Read API docs thoroughly**: Initial assumption about request structure (wrapper key) was wrong. Toggl v9 uses flat JSON.

2. **kotlinx.serialization defaults**: By default, fields with default values are **not encoded**. Must explicitly set `encodeDefaults = true`.

3. **Multiple Retrofit instances**: Use `@Qualifier` annotations to distinguish between different API instances in Dagger/Hilt.

4. **Non-blocking integration**: Time tracking failures should never break core functionality. Always use `runCatching { }` and log errors.

5. **Test with real API**: Mock testing is insufficient for API integrations. Always test with actual API calls to catch serialization issues.

---

## Documentation References

- **Complete Guide**: `docs/TOGGL-INTEGRATION.md` (600+ lines)
- **API Docs**: https://developers.track.toggl.com/docs/api/time_entries
- **Project Context**: `CLAUDE.md` (FR16 section)
- **Emacs Reference**: `toggl.el` package (behavior inspiration)

---

## Acknowledgments

**Implementation**: Ram Sharan Rimal (rimal.ram25@gmail.com)  
**Date**: 2026-03-02  
**Status**: ✅ Complete and Working

---

*End of Implementation Summary*
