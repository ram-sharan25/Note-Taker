# Pomodoro Timer - Remaining Implementation Work

**Status**: Phase 4 Complete (ViewModel Integration) - Build Successful ✅  
**Current Version**: 0.8.0  
**Target Version**: 0.9.0 (with Pomodoro feature)  
**Last Updated**: 2026-03-02

---

## Overview

A comprehensive Pomodoro timer feature for the agenda page is being implemented. **Phase 1-4 are complete** (data layer, service layer, UI layer, ViewModel integration). The project builds successfully and is ready for UI integration.

---

## ✅ Completed Work (Phases 1-4)

### Phase 1 - Data Layer (3/3 tasks) ✅
- ✓ `PomodoroPreferencesManager.kt` - DataStore for timer settings (25/5/10 min defaults)
- ✓ `PomodoroTimerState.kt` - Data classes for timer state, completion actions, service events
- ✓ Hilt DI integration (automatic via @Inject constructor)

### Phase 2 - Service Layer (4/4 tasks) ✅
- ✓ `PomodoroNotificationHelper.kt` - Notification builder with sound/vibration, pause/stop actions
- ✓ `PomodoroTimerService.kt` - Foreground service with 1-second countdown, WakeLock, broadcast events
- ✓ `AndroidManifest.xml` - Added 4 permissions + service declaration (specialUse type)
- ✓ Service uses Android system icons (ic_menu_recent_history, ic_dialog_info)

### Phase 3 - UI Layer (4/4 tasks) ✅
- ✓ `PomodoroTimerScreen.kt` - Fullscreen composable with green/blue themes, circular progress, KEEP_SCREEN_ON
- ✓ `TaskInfoOverlay.kt` - Semi-transparent task card (title, tags, priority badge)
- ✓ `PomodoroCompletionDialog.kt` - AlertDialog with 4 action buttons

### Phase 4 - ViewModel Integration (6/6 tasks) ✅
- ✓ Changed `AgendaViewModel` from `ViewModel` to `AndroidViewModel`
- ✓ Added `PomodoroPreferencesManager` injection to constructor
- ✓ Implemented BroadcastReceiver for service events (TICK, COMPLETED, PAUSED, RESUMED, STOPPED)
- ✓ Added `pomodoroState` StateFlow to expose timer state to UI
- ✓ Added Pomodoro control methods: `startPomodoro()`, `pausePomodoro()`, `resumePomodoro()`, `stopPomodoro()`, `handlePomodoroCompletionAction()`
- ✓ Added repository methods: `AgendaRepository.getTask()` and `AgendaRepository.getInProgressTasks()`
- ✓ Fixed companion object placement in `AgendaRepository` (moved to top for TAG availability)
- ✓ Build successful with no compilation errors

---

## 🚧 Remaining Work (Phases 5-9)

### Phase 5 - UI Integration (3 tasks) ⏳

#### Task 5.1: Modify StateSelectionDialog
**File**: `app/src/main/kotlin/com/rrimal/notetaker/ui/screens/agenda/StateSelectionDialog.kt`

**Requirements**:
- Add "Start Pomodoro" button to state selection dialog
- Only show button when current state is `IN-PROGRESS`
- Button should launch Pomodoro timer for the selected task
- Call `viewModel.startPomodoro(noteId, isBreak = false)` on button click

**Implementation Notes**:
```kotlin
// Add button after state selection buttons
if (currentState == "IN-PROGRESS") {
    Button(
        onClick = {
            coroutineScope.launch {
                viewModel.startPomodoro(noteId, isBreak = false)
                onDismiss()
            }
        },
        colors = ButtonDefaults.buttonColors(
            containerColor = Green80 // Use green theme for focus timer
        )
    ) {
        Icon(Icons.Default.Timer, contentDescription = null)
        Spacer(modifier = Modifier.width(8.dp))
        Text("Start Pomodoro")
    }
}
```

#### Task 5.2: Add PomodoroTimerScreen to AgendaScreen
**File**: `app/src/main/kotlin/com/rrimal/notetaker/ui/screens/agenda/AgendaScreen.kt`

**Requirements**:
- Observe `viewModel.pomodoroState` in AgendaScreen
- When `pomodoroState` is not null, show `PomodoroTimerScreen` as overlay
- When `pomodoroState.isComplete == true`, show `PomodoroCompletionDialog`
- Pass control callbacks to PomodoroTimerScreen: onPause, onResume, onStop

**Implementation Notes**:
```kotlin
val pomodoroState by viewModel.pomodoroState.collectAsState()

// Main agenda content
AgendaContent(...)

// Pomodoro overlay
pomodoroState?.let { state ->
    PomodoroTimerScreen(
        state = state,
        onPause = { viewModel.pausePomodoro() },
        onResume = { viewModel.resumePomodoro() },
        onStop = { viewModel.stopPomodoro() }
    )
    
    // Show completion dialog when timer finishes
    if (state.isComplete) {
        PomodoroCompletionDialog(
            isBreak = state.isBreak,
            taskTitle = state.taskTitle,
            onAction = { action ->
                viewModel.handlePomodoroCompletionAction(action)
            }
        )
    }
}
```

#### Task 5.3: Create TaskPickerDialog
**File**: `app/src/main/kotlin/com/rrimal/notetaker/ui/screens/pomodoro/TaskPickerDialog.kt` (NEW FILE)

**Requirements**:
- Show when user wants to start Pomodoro but there are multiple IN-PROGRESS tasks
- List all IN-PROGRESS tasks with title, tags, and priority
- User selects one task to start timer
- Call `viewModel.startPomodoro(selectedTaskId, isBreak = false)` on selection

**Implementation Notes**:
```kotlin
@Composable
fun TaskPickerDialog(
    tasks: List<AgendaItem.Note>,
    onTaskSelected: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Task for Pomodoro") },
        text = {
            LazyColumn {
                items(tasks) { task ->
                    TaskPickerItem(
                        task = task,
                        onClick = { onTaskSelected(task.noteId) }
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
```

---

### Phase 6 - Settings UI (3 tasks) ⏳

#### Task 6.1: Create PomodoroSettingsCard
**File**: `app/src/main/kotlin/com/rrimal/notetaker/ui/screens/pomodoro/PomodoroSettingsCard.kt` (NEW FILE)

**Requirements**:
- Card with 3 sliders for Pomodoro duration, short break, long break
- Default values: 25 minutes (focus), 5 minutes (short break), 10 minutes (long break)
- Range: 1-60 minutes for all sliders
- Save to DataStore via PomodoroPreferencesManager

**Implementation Notes**:
```kotlin
@Composable
fun PomodoroSettingsCard(
    pomodoroDuration: Int,
    shortBreakDuration: Int,
    longBreakDuration: Int,
    onPomodoroDurationChange: (Int) -> Unit,
    onShortBreakDurationChange: (Int) -> Unit,
    onLongBreakDurationChange: (Int) -> Unit
) {
    Card {
        Column {
            Text("Pomodoro Timer Settings")
            
            // Pomodoro duration slider
            DurationSlider(
                label = "Focus Duration",
                value = pomodoroDuration,
                onValueChange = onPomodoroDurationChange
            )
            
            // Short break slider
            DurationSlider(
                label = "Short Break",
                value = shortBreakDuration,
                onValueChange = onShortBreakDurationChange
            )
            
            // Long break slider (future use)
            DurationSlider(
                label = "Long Break",
                value = longBreakDuration,
                onValueChange = onLongBreakDurationChange
            )
        }
    }
}
```

#### Task 6.2: Add PomodoroSettingsCard to SettingsScreen
**File**: `app/src/main/kotlin/com/rrimal/notetaker/ui/screens/SettingsScreen.kt`

**Requirements**:
- Add PomodoroSettingsCard after Toggl settings card
- Observe duration preferences from ViewModel
- Update preferences on slider changes

**Implementation Notes**:
```kotlin
// In SettingsScreen, after Toggl card:
val pomodoroDuration by viewModel.pomodoroDuration.collectAsState()
val shortBreakDuration by viewModel.shortBreakDuration.collectAsState()
val longBreakDuration by viewModel.longBreakDuration.collectAsState()

PomodoroSettingsCard(
    pomodoroDuration = pomodoroDuration,
    shortBreakDuration = shortBreakDuration,
    longBreakDuration = longBreakDuration,
    onPomodoroDurationChange = { viewModel.setPomodoroDuration(it) },
    onShortBreakDurationChange = { viewModel.setShortBreakDuration(it) },
    onLongBreakDurationChange = { viewModel.setLongBreakDuration(it) }
)
```

#### Task 6.3: Inject PomodoroPreferencesManager into SettingsViewModel
**File**: `app/src/main/kotlin/com/rrimal/notetaker/ui/viewmodels/SettingsViewModel.kt`

**Requirements**:
- Add PomodoroPreferencesManager to constructor
- Expose duration preferences as StateFlows
- Add methods to update preferences: `setPomodoroDuration()`, `setShortBreakDuration()`, `setLongBreakDuration()`

**Implementation Notes**:
```kotlin
class SettingsViewModel @Inject constructor(
    // ... existing params
    private val pomodoroPreferencesManager: PomodoroPreferencesManager
) : ViewModel() {
    
    val pomodoroDuration = pomodoroPreferencesManager.pomodoroDuration
        .stateIn(viewModelScope, SharingStarted.Eagerly, 25)
    
    val shortBreakDuration = pomodoroPreferencesManager.shortBreakDuration
        .stateIn(viewModelScope, SharingStarted.Eagerly, 5)
    
    val longBreakDuration = pomodoroPreferencesManager.longBreakDuration
        .stateIn(viewModelScope, SharingStarted.Eagerly, 10)
    
    fun setPomodoroDuration(minutes: Int) {
        viewModelScope.launch {
            pomodoroPreferencesManager.setPomodoroDuration(minutes)
        }
    }
    
    fun setShortBreakDuration(minutes: Int) {
        viewModelScope.launch {
            pomodoroPreferencesManager.setShortBreakDuration(minutes)
        }
    }
    
    fun setLongBreakDuration(minutes: Int) {
        viewModelScope.launch {
            pomodoroPreferencesManager.setLongBreakDuration(minutes)
        }
    }
}
```

---

### Phase 7 - Fullscreen Mode (3 tasks) ⏳

#### Task 7.1: Add enterFullscreenMode/exitFullscreenMode to MainActivity
**File**: `app/src/main/kotlin/com/rrimal/notetaker/MainActivity.kt`

**Requirements**:
- Add methods to enter/exit fullscreen (hide system bars)
- Use `WindowInsetsControllerCompat` to hide status bar and navigation bar
- Call `enterFullscreenMode()` when Pomodoro starts
- Call `exitFullscreenMode()` when Pomodoro stops

**Implementation Notes**:
```kotlin
class MainActivity : ComponentActivity() {
    
    private fun enterFullscreenMode() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).let { controller ->
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
    
    private fun exitFullscreenMode() {
        WindowCompat.setDecorFitsSystemWindows(window, true)
        WindowInsetsControllerCompat(window, window.decorView).show(WindowInsetsCompat.Type.systemBars())
    }
}
```

#### Task 7.2: Disable HorizontalPager swipe when Pomodoro active
**File**: `app/src/main/kotlin/com/rrimal/notetaker/ui/screens/MainScreen.kt`

**Requirements**:
- Observe `pomodoroState` from AgendaViewModel
- When `pomodoroState != null`, set `pagerState.userScrollEnabled = false`
- Restore to `true` when Pomodoro stops

**Implementation Notes**:
```kotlin
val pomodoroState by agendaViewModel.pomodoroState.collectAsState()

HorizontalPager(
    state = pagerState,
    userScrollEnabled = pomodoroState == null, // Disable swipe when timer active
    modifier = Modifier.fillMaxSize()
) { page ->
    // ... pages
}
```

#### Task 7.3: Hide page indicators during Pomodoro
**File**: `app/src/main/kotlin/com/rrimal/notetaker/ui/screens/MainScreen.kt`

**Requirements**:
- Hide bottom page indicators when Pomodoro is active
- Show indicators when Pomodoro stops

**Implementation Notes**:
```kotlin
// Bottom indicators
if (pomodoroState == null) {
    PageIndicators(
        pagerState = pagerState,
        modifier = Modifier.align(Alignment.BottomCenter)
    )
}
```

---

### Phase 8 - History Tracking (3 tasks) ⏳

#### Task 8.1: Create PomodoroHistoryEntity
**File**: `app/src/main/kotlin/com/rrimal/notetaker/data/local/PomodoroHistoryEntity.kt` (NEW FILE)

**Requirements**:
- Room entity to track completed Pomodoros
- Fields: id, taskId, taskTitle, startTime, endTime, durationSeconds, isBreak, completedSuccessfully

**Implementation Notes**:
```kotlin
@Entity(tableName = "pomodoro_history")
data class PomodoroHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val taskId: Long,
    val taskTitle: String,
    val startTime: Long,
    val endTime: Long,
    val durationSeconds: Int,
    val isBreak: Boolean,
    val completedSuccessfully: Boolean // false if user cancelled
)
```

#### Task 8.2: Create PomodoroHistoryDao
**File**: `app/src/main/kotlin/com/rrimal/notetaker/data/local/PomodoroHistoryDao.kt` (NEW FILE)

**Requirements**:
- DAO for PomodoroHistoryEntity
- Methods: insert, getAll, getByTaskId, deleteAll

**Implementation Notes**:
```kotlin
@Dao
interface PomodoroHistoryDao {
    @Insert
    suspend fun insert(history: PomodoroHistoryEntity)
    
    @Query("SELECT * FROM pomodoro_history ORDER BY endTime DESC")
    fun getAll(): Flow<List<PomodoroHistoryEntity>>
    
    @Query("SELECT * FROM pomodoro_history WHERE taskId = :taskId ORDER BY endTime DESC")
    fun getByTaskId(taskId: Long): Flow<List<PomodoroHistoryEntity>>
    
    @Query("DELETE FROM pomodoro_history")
    suspend fun deleteAll()
}
```

#### Task 8.3: Add history tracking to PomodoroTimerService
**File**: `app/src/main/kotlin/com/rrimal/notetaker/pomodoro/PomodoroTimerService.kt`

**Requirements**:
- Inject `PomodoroHistoryDao` into service
- Track start time when timer begins
- On completion, save history entry with completion status
- On cancellation, save history entry with `completedSuccessfully = false`

**Implementation Notes**:
```kotlin
// In PomodoroTimerService
private var startTime: Long = 0

private fun startTimer(...) {
    startTime = System.currentTimeMillis()
    // ... existing code
}

private fun onTimerComplete() {
    serviceScope.launch {
        historyDao.insert(
            PomodoroHistoryEntity(
                taskId = state.value.taskId ?: -1,
                taskTitle = state.value.taskTitle ?: "",
                startTime = startTime,
                endTime = System.currentTimeMillis(),
                durationSeconds = state.value.durationSeconds,
                isBreak = state.value.isBreak,
                completedSuccessfully = true
            )
        )
    }
}
```

---

### Phase 9 - Testing & Polish (6 tasks) ⏳

#### Task 9.1: Add ProGuard rules
**File**: `app/proguard-rules.pro`

**Requirements**:
- Keep PomodoroTimerService class and methods
- Keep PomodoroTimerState and related data classes
- Keep PomodoroPreferencesManager

**Implementation Notes**:
```proguard
# Pomodoro Timer
-keep class com.rrimal.notetaker.pomodoro.PomodoroTimerService { *; }
-keep class com.rrimal.notetaker.pomodoro.PomodoroTimerState { *; }
-keep class com.rrimal.notetaker.pomodoro.PomodoroServiceEvent { *; }
-keep class com.rrimal.notetaker.data.preferences.PomodoroPreferencesManager { *; }
```

#### Task 9.2: Test timer persistence
**Test Scenario**:
1. Start Pomodoro timer
2. Press home button (app goes to background)
3. Wait 1 minute
4. Reopen app
5. Verify timer is still running with correct remaining time

#### Task 9.3: Test notification functionality
**Test Scenario**:
1. Start Pomodoro timer
2. Press home button
3. Verify notification shows in status bar with correct time
4. Tap "Pause" button in notification
5. Verify timer pauses
6. Tap "Resume" button
7. Verify timer resumes
8. Tap "Stop" button
9. Verify timer stops and notification disappears

#### Task 9.4: Test completion flow
**Test Scenario**:
1. Start Pomodoro timer with short duration (e.g., 1 minute for testing)
2. Wait for timer to complete
3. Verify completion dialog shows with 4 buttons
4. Test each button:
   - "Start Break" → starts break timer
   - "Another Pomodoro" → starts new focus timer
   - "Mark DONE" → marks task as DONE and stops timer
   - "Cancel" → stops timer and returns to agenda

#### Task 9.5: Test fullscreen mode
**Test Scenario**:
1. Start Pomodoro timer
2. Verify app enters fullscreen (status bar hidden)
3. Verify HorizontalPager swipe is disabled
4. Verify page indicators are hidden
5. Stop timer
6. Verify app exits fullscreen
7. Verify pager swipe is re-enabled
8. Verify page indicators are visible

#### Task 9.6: Test theme switching
**Test Scenario**:
1. Start focus timer (25 min)
2. Verify green theme (Green80/Green40)
3. Complete timer and select "Start Break"
4. Verify blue theme (Blue80/Blue40)
5. Complete break and select "Another Pomodoro"
6. Verify green theme again

---

## File Structure Summary

### Created Files (17 files)
```
app/src/main/kotlin/com/rrimal/notetaker/
├── data/
│   ├── preferences/
│   │   └── PomodoroPreferencesManager.kt ✅ (Phase 1)
│   └── local/
│       ├── PomodoroHistoryEntity.kt ⏳ (Phase 8 - TODO)
│       └── PomodoroHistoryDao.kt ⏳ (Phase 8 - TODO)
├── pomodoro/
│   ├── PomodoroTimerState.kt ✅ (Phase 1)
│   ├── PomodoroTimerService.kt ✅ (Phase 2)
│   └── PomodoroNotificationHelper.kt ✅ (Phase 2)
└── ui/screens/pomodoro/
    ├── PomodoroTimerScreen.kt ✅ (Phase 3)
    ├── TaskInfoOverlay.kt ✅ (Phase 3)
    ├── PomodoroCompletionDialog.kt ✅ (Phase 3)
    ├── TaskPickerDialog.kt ⏳ (Phase 5 - TODO)
    └── PomodoroSettingsCard.kt ⏳ (Phase 6 - TODO)
```

### Modified Files (7 files)
```
app/src/main/
├── AndroidManifest.xml ✅ (Phase 2)
├── kotlin/com/rrimal/notetaker/
│   ├── MainActivity.kt ⏳ (Phase 7 - TODO)
│   ├── data/
│   │   ├── local/
│   │   │   ├── AppDatabase.kt ⏳ (Phase 8 - TODO)
│   │   │   └── NoteDao.kt ✅ (Phase 4)
│   │   └── repository/
│   │       └── AgendaRepository.kt ✅ (Phase 4)
│   └── ui/
│       ├── screens/
│       │   ├── MainScreen.kt ⏳ (Phase 7 - TODO)
│       │   ├── SettingsScreen.kt ⏳ (Phase 6 - TODO)
│       │   └── agenda/
│       │       ├── AgendaScreen.kt ⏳ (Phase 5 - TODO)
│       │       └── StateSelectionDialog.kt ⏳ (Phase 5 - TODO)
│       └── viewmodels/
│           ├── AgendaViewModel.kt ✅ (Phase 4)
│           └── SettingsViewModel.kt ⏳ (Phase 6 - TODO)
└── proguard-rules.pro ⏳ (Phase 9 - TODO)
```

---

## Key Design Decisions

### User Preferences (Confirmed)
- **Notification sound**: Play sound + vibration on completion ✓
- **Break behavior**: Auto-start breaks immediately when user selects "Start Break" ✓
- **History tracking**: Maintain database of completed Pomodoros ✓
- **Screen always-on**: Use KEEP_SCREEN_ON flag during timer ✓
- **Theme colors**: Green for focus timer, blue for break timer ✓

### Technical Architecture
- **Service architecture**: Foreground service with WakeLock for persistence during device sleep
- **Communication pattern**: BroadcastReceiver from service → ViewModel → UI (reactive StateFlow)
- **State management**: Single source of truth in `AgendaViewModel.pomodoroState`
- **Timer accuracy**: 1-second tick broadcast, UI updates reactively
- **Notification actions**: Pause/Resume/Stop buttons in notification
- **Completion flow**: Dialog with 4 actions (Start Break, Another Pomodoro, Mark DONE, Cancel)

### Known Simplifications
- Task lookup methods (`getTask`, `getInProgressTasks`) return basic info without timestamp resolution
- This is intentional to avoid complex OrgTimestamp entity lookups
- Timestamps are not critical for Pomodoro timer UI (task title, tags, priority are sufficient)

---

## Next Session Action Items

1. **Start with Phase 5 - UI Integration**
   - Begin with Task 5.1 (StateSelectionDialog)
   - Then Task 5.2 (AgendaScreen integration)
   - Finally Task 5.3 (TaskPickerDialog)

2. **Test after each phase completion**
   - Run `./gradlew assembleDebug` to verify build
   - Install on device: `./gradlew installDebug`
   - Test on Samsung Galaxy S24 Ultra (preferred)

3. **Reference files for patterns**
   - Look at Toggl integration for similar timer patterns
   - Check existing dialog implementations for UI patterns
   - Follow Material 3 design guidelines for consistency

---

## Build Status
✅ **Project builds successfully** - No compilation errors  
✅ **Phase 1-4 complete** - Data, service, UI, and ViewModel layers done  
⏳ **Phase 5-9 remaining** - UI integration, settings, fullscreen, history, testing

---

## Documentation Updates Needed (After Completion)

1. **CLAUDE.md** - Add Pomodoro feature to FR list (FR17?)
2. **REQUIREMENTS.md** - Document Pomodoro requirements
3. **CHANGELOG.md** - Add v0.9.0 entry with Pomodoro feature
4. **whatsnew/whatsnew-en-US** - Update Play Store release notes
5. **README** (if exists) - Update feature list

---

## Questions / Clarifications Needed

None currently - all design decisions confirmed with user.

---

**End of Document**
