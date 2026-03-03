# Phone Time Tracking Implementation Plan

**Date:** 2026-03-02  
**Status:** Planning → Implementation  
**Related ADR:** ADR 003 (Agenda View)

---

## Context

The app currently supports agenda view with basic TODO state cycling (TODO → IN-PROGRESS → DONE), but doesn't track when tasks are started/completed. This is needed for time tracking integration with Emacs org-mode.

### User Requirements

1. **Track work time** - Know when tasks were started and completed
2. **Simple phone interface** - Minimal metadata tracking, not full Emacs LOGBOOK
3. **Emacs processing** - Phone writes simple properties, Emacs converts to proper LOGBOOK/CLOCK entries
4. **Multiple sessions** - Eventually track multiple start/stop cycles (TODO → IN-PROGRESS → WAITING → IN-PROGRESS → DONE)

### Design Principle

**Phone:** Simple time tracking (properties only)  
**Emacs:** Complex processing (LOGBOOK conversion, conflict resolution)

This separation keeps phone code simple while leveraging Emacs's sophisticated org-mode capabilities.

---

## Phased Approach

### Phase 1: Single Session Tracking (Immediate)

**Goal:** Prove the concept with minimal complexity

**Scope:**
- Track one work session per task (start + end time)
- Display current session duration in UI
- Write simple properties to org files
- Validate Emacs can process the data

**Timeline:** 4-6 hours of development

**Limitations:**
- Multiple start/stop cycles will create `PHONE_STARTED_2`, `PHONE_STARTED_3` properties
- No total time calculation across sessions
- Emacs must handle multiple property cleanup

### Phase 2: Multiple Sessions (Future Enhancement)

**Goal:** Accurately track multiple work periods

**Scope:**
- Comma-separated session list format
- Track all start/stop cycles
- Calculate total time across sessions
- Enhanced UI showing both current and total time

**Timeline:** 1-2 days of development

**Trigger:** After Phase 1 validated and Emacs processing workflow established

---

## Phase 1 Design

### Property Format

#### Task not started
```org
* TODO Write report
  SCHEDULED: <2026-03-02 Sun>
  :PROPERTIES:
  :ID: abc123
  :END:
```

#### Task in progress (started at 2:00 PM)
```org
* IN-PROGRESS Write report
  SCHEDULED: <2026-03-02 Sun>
  :PROPERTIES:
  :ID: abc123
  :PHONE_STARTED: [2026-03-02 Sun 14:00]
  :END:
```

#### Task completed or paused
```org
* DONE Write report
  SCHEDULED: <2026-03-02 Sun>
  :PROPERTIES:
  :ID: abc123
  :PHONE_STARTED: [2026-03-02 Sun 14:00]
  :PHONE_ENDED: [2026-03-02 Sun 17:00]
  :END:
```

#### Task restarted (Phase 1 limitation)
```org
* IN-PROGRESS Write report
  SCHEDULED: <2026-03-02 Sun>
  :PROPERTIES:
  :ID: abc123
  :PHONE_STARTED: [2026-03-02 Sun 14:00]
  :PHONE_ENDED: [2026-03-02 Sun 15:00]
  :PHONE_STARTED_2: [2026-03-02 Sun 16:00]
  :END:
```

*Note: This limitation will be addressed in Phase 2*

### State Transition Rules

| From State | To State | Action |
|------------|----------|--------|
| TODO/WAITING/HOLD | IN-PROGRESS | Write `PHONE_STARTED: [timestamp]` |
| IN-PROGRESS | DONE/WAITING/HOLD/CANCELLED | Write `PHONE_ENDED: [timestamp]` |
| IN-PROGRESS | IN-PROGRESS | No change |
| DONE | IN-PROGRESS | Write new `PHONE_STARTED` (creates conflict in Phase 1) |

**Active States:** IN-PROGRESS, DOING, STARTED  
**Inactive States:** TODO, WAITING, HOLD, DONE, CANCELLED

### UI Display

For IN-PROGRESS tasks, show current session duration:

```
┌─────────────────────────────────────┐
│ IN-PROGRESS Write report            │
│ 🕒 Active: 1h 15m                   │
│ SCHEDULED: Today 14:00              │
│ #work #urgent                       │
└─────────────────────────────────────┘
```

**Calculation:**
- Parse `PHONE_STARTED` timestamp
- Calculate duration from start to now
- Format as "Xh Ym" or "Ym" (if < 1 hour)
- Update in real-time (recomposition)

### Timestamp Format

**Org-mode standard:** `[YYYY-MM-DD Day HH:mm]`

**Examples:**
- `[2026-03-02 Sun 14:00]`
- `[2026-03-02 Sun 17:30]`
- `[2026-12-31 Wed 23:59]`

**Formatting:**
```kotlin
DateTimeFormatter.ofPattern("[yyyy-MM-dd EEE HH:mm]", Locale.ENGLISH)
```

---

## Implementation Tasks

### 1. Modify AgendaRepository.kt

**Location:** `app/src/main/kotlin/com/rrimal/notetaker/data/repository/AgendaRepository.kt`

**Changes to `updateTodoState()`:**

```kotlin
suspend fun updateTodoState(noteId: Long, newState: String): Result<Unit> = runCatching {
    // ... existing conflict detection code ...
    
    val note = noteDao.getById(noteId) ?: throw IllegalArgumentException("Note not found")
    val oldState = note.todoState
    
    // NEW: Determine time tracking properties
    val timeProperties = determineTimeTrackingProperties(note, oldState, newState)
    
    // Parse org file (existing)
    val currentContent = localFileManager.readFile(note.filename).getOrThrow()
    val orgFile = orgParser.parse(currentContent)
    
    // NEW: Update headline with state + properties
    val updatedHeadlines = updateHeadlineStateAndProperties(
        orgFile.headlines, 
        note.headlineId, 
        newState,
        timeProperties
    )
    
    // ... rest of existing write-back code ...
}
```

**New helper functions:**

```kotlin
/**
 * Determine which time tracking properties to add/update
 */
private fun determineTimeTrackingProperties(
    note: NoteEntity,
    oldState: String?,
    newState: String
): Map<String, String> {
    val properties = mutableMapOf<String, String>()
    val now = LocalDateTime.now()
    val timestamp = formatOrgTimestamp(now)
    
    val activeStates = setOf("IN-PROGRESS", "DOING", "STARTED")
    val wasActive = oldState in activeStates
    val isActive = newState in activeStates
    
    when {
        // Starting work: record start time
        !wasActive && isActive -> {
            // Only set if not already tracking (avoid overwrite on restart)
            if (!note.properties.containsKey("PHONE_STARTED")) {
                properties["PHONE_STARTED"] = timestamp
            } else {
                // Phase 1 limitation: create numbered property
                var counter = 2
                while (note.properties.containsKey("PHONE_STARTED_$counter")) {
                    counter++
                }
                properties["PHONE_STARTED_$counter"] = timestamp
            }
        }
        
        // Stopping work: record end time
        wasActive && !isActive -> {
            properties["PHONE_ENDED"] = timestamp
        }
    }
    
    return properties
}

/**
 * Format timestamp in org-mode format: [YYYY-MM-DD Day HH:mm]
 */
private fun formatOrgTimestamp(dt: LocalDateTime): String {
    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd EEE HH:mm", Locale.ENGLISH)
    return "[${dt.format(formatter)}]"
}

/**
 * Update headline with new state and properties
 * Replaces existing updateHeadlineState() function
 */
private fun updateHeadlineStateAndProperties(
    headlines: List<OrgNode.Headline>,
    headlineId: String,
    newState: String,
    properties: Map<String, String>
): List<OrgNode.Headline>? {
    headlines.forEach { headline ->
        if (headline.properties["ID"] == headlineId) {
            // Update state and merge properties
            val updatedProperties = headline.properties.toMutableMap()
            updatedProperties.putAll(properties)
            
            val updatedHeadline = headline.copy(
                todoState = newState,
                properties = updatedProperties
            )
            return headlines.map { if (it == headline) updatedHeadline else it }
        }
        
        // Recurse into children
        if (headline.children.isNotEmpty()) {
            val updatedChildren = updateHeadlineStateAndProperties(
                headline.children, headlineId, newState, properties
            )
            if (updatedChildren != null) {
                val updatedHeadline = headline.copy(children = updatedChildren)
                return headlines.map { if (it == headline) updatedHeadline else it }
            }
        }
    }
    return null
}
```

**Refactoring note:** Replace existing `updateHeadlineState()` with `updateHeadlineStateAndProperties()`

**Estimated time:** 2-3 hours

---

### 2. Add Duration Display to AgendaScreen.kt

**Location:** `app/src/main/kotlin/com/rrimal/notetaker/ui/screens/agenda/AgendaScreen.kt`

**Modify AgendaItemNote composable:**

```kotlin
@Composable
fun AgendaItemNote(
    item: AgendaItem.Note,
    onTap: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onTap),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            // Title
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            
            // NEW: Show active session duration for IN-PROGRESS tasks
            if (item.todoState in setOf("IN-PROGRESS", "DOING", "STARTED")) {
                item.properties["PHONE_STARTED"]?.let { startedStr ->
                    val duration = calculateDuration(startedStr)
                    if (duration != null) {
                        Row(
                            modifier = Modifier.padding(top = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Timer,
                                contentDescription = "Active time",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.secondary
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                "Active: $duration",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }
            }
            
            // Existing: TODO state badge, time type, tags, etc.
            // ...
        }
    }
}
```

**New helper functions (add to AgendaScreen.kt):**

```kotlin
/**
 * Calculate duration from start timestamp to now
 * Returns formatted string like "1h 15m" or "45m"
 */
private fun calculateDuration(startTimestamp: String): String? {
    return try {
        // Parse: [2026-03-02 Sun 14:00]
        val formatter = DateTimeFormatter.ofPattern("[yyyy-MM-dd EEE HH:mm]", Locale.ENGLISH)
        val startTime = LocalDateTime.parse(startTimestamp, formatter)
        val now = LocalDateTime.now()
        val duration = Duration.between(startTime, now)
        
        formatDuration(duration)
    } catch (e: Exception) {
        Log.e("AgendaScreen", "Failed to parse timestamp: $startTimestamp", e)
        null
    }
}

/**
 * Format duration as "Xh Ym" or "Ym"
 */
private fun formatDuration(duration: Duration): String {
    val hours = duration.toHours()
    val minutes = duration.toMinutes() % 60
    
    return when {
        hours > 0 -> "${hours}h ${minutes}m"
        minutes > 0 -> "${minutes}m"
        else -> "< 1m"
    }
}
```

**Imports to add:**
```kotlin
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Timer
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
```

**Note:** The duration will update on recomposition. For real-time updates, consider adding a LaunchedEffect with delay(60_000) to trigger recomposition every minute.

**Estimated time:** 1-2 hours

---

### 3. Update AgendaItem.Note Data Class

**Location:** `app/src/main/kotlin/com/rrimal/notetaker/ui/screens/agenda/AgendaItem.kt`

**Verify `AgendaItem.Note` includes properties:**

```kotlin
data class Note(
    override val id: Long,
    val noteId: Long,
    val title: String,
    val todoState: String?,
    val timeType: TimeType,
    val timestamp: Long,
    val formattedTime: String?,
    val isOverdue: Boolean = false,
    val tags: List<String> = emptyList(),
    val properties: Map<String, String> = emptyMap()  // ← Verify this exists
) : AgendaItem(id)
```

**If `properties` field missing, add it.**

**Update AgendaRepository.kt `buildAgendaList()`:**

Ensure properties are passed when creating `AgendaItem.Note`:

```kotlin
val item = AgendaItem.Note(
    id = generateId(),
    noteId = note.id,
    title = note.title,
    todoState = note.todoState,
    timeType = TimeType.SCHEDULED,
    timestamp = dateTime.toEpochSecond(),
    formattedTime = dateTime.format("HH:mm"),
    isOverdue = date < now,
    tags = note.tags.split(":").filter { it.isNotBlank() },
    properties = note.properties  // ← Add this
)
```

**Estimated time:** 30 minutes

---

### 4. Verify OrgParser & OrgWriter

**Verify OrgParser.kt correctly parses properties:**

Check that `OrgNode.Headline` has:
```kotlin
data class Headline(
    // ...
    val properties: Map<String, String>,
    // ...
)
```

And that parser populates this map from `:PROPERTIES:` drawer.

**Verify OrgWriter.kt correctly writes properties:**

Check that `writeHeadline()` serializes the `properties` map back to:
```org
:PROPERTIES:
:KEY: value
:END:
```

**Action:** Read these files and verify. If property writing is missing, implement it.

**Estimated time:** 1 hour (verification + potential fixes)

---

### 5. Testing

#### Unit Tests

**Create `AgendaRepositoryTimeTrackingTest.kt`:**

```kotlin
@Test
fun `updateTodoState adds PHONE_STARTED when starting work`() {
    // Given: Task in TODO state
    val note = createTestNote(id = 1, state = "TODO")
    
    // When: Change to IN-PROGRESS
    repository.updateTodoState(note.id, "IN-PROGRESS")
    
    // Then: PHONE_STARTED property added
    val updatedNote = repository.getNote(note.id)
    assertNotNull(updatedNote.properties["PHONE_STARTED"])
    assertTrue(updatedNote.properties["PHONE_STARTED"]!!.matches(timestampRegex))
}

@Test
fun `updateTodoState adds PHONE_ENDED when stopping work`() {
    // Given: Task in IN-PROGRESS state with PHONE_STARTED
    val note = createTestNote(
        id = 1, 
        state = "IN-PROGRESS",
        properties = mapOf("PHONE_STARTED" to "[2026-03-02 Sun 14:00]")
    )
    
    // When: Change to DONE
    repository.updateTodoState(note.id, "DONE")
    
    // Then: PHONE_ENDED property added
    val updatedNote = repository.getNote(note.id)
    assertNotNull(updatedNote.properties["PHONE_ENDED"])
}

@Test
fun `updateTodoState creates numbered property on restart`() {
    // Given: Task already has PHONE_STARTED and PHONE_ENDED
    val note = createTestNote(
        id = 1,
        state = "WAITING",
        properties = mapOf(
            "PHONE_STARTED" to "[2026-03-02 Sun 14:00]",
            "PHONE_ENDED" to "[2026-03-02 Sun 15:00]"
        )
    )
    
    // When: Change back to IN-PROGRESS
    repository.updateTodoState(note.id, "IN-PROGRESS")
    
    // Then: PHONE_STARTED_2 created
    val updatedNote = repository.getNote(note.id)
    assertNotNull(updatedNote.properties["PHONE_STARTED_2"])
}
```

**Estimated time:** 2 hours

---

#### Manual Testing

**Test Scenario 1: Basic flow**
1. Open agenda view
2. Find TODO task
3. Tap to cycle TODO → IN-PROGRESS
4. Verify:
   - Task shows "Active: 0m" (or "< 1m")
   - Open org file in text editor
   - Confirm `PHONE_STARTED: [timestamp]` in properties
5. Wait 5 minutes, verify UI shows "Active: 5m"
6. Tap to cycle IN-PROGRESS → DONE
7. Verify:
   - UI no longer shows duration
   - Org file has `PHONE_ENDED: [timestamp]`

**Test Scenario 2: Restart task**
1. Mark IN-PROGRESS task as WAITING
2. Verify `PHONE_ENDED` written
3. Mark WAITING task back to IN-PROGRESS
4. Verify:
   - `PHONE_STARTED_2` created (Phase 1 behavior)
   - UI shows duration from original `PHONE_STARTED` (limitation)

**Test Scenario 3: Sync with Emacs**
1. Complete Scenario 1 on phone
2. Wait for Syncthing sync (or manual copy)
3. Open file in Emacs
4. Verify properties visible
5. Write Elisp to convert properties to LOGBOOK
6. Verify conversion works

**Estimated time:** 1 hour

---

## Phase 2 Design (Future)

### Comma-Separated Session Format

**Single completed session:**
```org
:PROPERTIES:
:ID: abc123
:PHONE_SESSIONS: [2026-03-02 Sun 14:00]--[2026-03-02 Sun 17:00]
:END:
```

**Multiple sessions:**
```org
:PROPERTIES:
:ID: abc123
:PHONE_SESSIONS: [2026-03-02 Sun 14:00]--[2026-03-02 Sun 15:00], [2026-03-02 Sun 16:00]--[2026-03-02 Sun 17:00]
:END:
```

**Active session (no end time):**
```org
:PROPERTIES:
:ID: abc123
:PHONE_SESSIONS: [2026-03-02 Sun 14:00]--[2026-03-02 Sun 15:00], [2026-03-02 Sun 16:00]
:END:
```

### Migration Strategy

**Detect old format:**
```kotlin
val hasOldFormat = properties.containsKey("PHONE_STARTED") && 
                   !properties.containsKey("PHONE_SESSIONS")
```

**Convert to new format:**
```kotlin
if (hasOldFormat) {
    val sessions = mutableListOf<String>()
    
    // Collect all PHONE_STARTED* properties
    val starts = properties.entries
        .filter { it.key.startsWith("PHONE_STARTED") }
        .sortedBy { it.key }
    
    starts.forEachIndexed { index, (_, startTime) ->
        val endKey = if (index == 0) "PHONE_ENDED" else "PHONE_ENDED_${index + 1}"
        val endTime = properties[endKey]
        
        sessions.add(if (endTime != null) {
            "$startTime--$endTime"
        } else {
            startTime  // Active session
        })
    }
    
    // Write consolidated format
    properties["PHONE_SESSIONS"] = sessions.joinToString(", ")
    
    // Clean up old properties
    properties.keys.removeAll { it.startsWith("PHONE_STARTED") || it.startsWith("PHONE_ENDED") }
}
```

### Enhanced UI

**Display total time + current session:**
```
┌─────────────────────────────────────┐
│ IN-PROGRESS Write report            │
│ 🕒 Active: 45m (Total: 2h 15m)      │
│ SCHEDULED: Today 14:00              │
│ #work #urgent                       │
└─────────────────────────────────────┘
```

**Calculation:**
```kotlin
fun calculateTotalDuration(sessionsStr: String): String {
    val sessions = sessionsStr.split(", ")
    var totalMinutes = 0L
    var currentSessionStart: LocalDateTime? = null
    
    sessions.forEach { session ->
        val parts = session.split("--")
        val start = parseTimestamp(parts[0])
        
        if (parts.size == 2) {
            // Closed session
            val end = parseTimestamp(parts[1])
            totalMinutes += Duration.between(start, end).toMinutes()
        } else {
            // Active session
            currentSessionStart = start
        }
    }
    
    // Add current session duration if active
    currentSessionStart?.let { start ->
        totalMinutes += Duration.between(start, LocalDateTime.now()).toMinutes()
    }
    
    return formatDurationMinutes(totalMinutes)
}
```

---

## Emacs Integration (User Responsibility)

### Example Elisp Function

**Convert PHONE_* properties to LOGBOOK:**

```elisp
(defun org-process-phone-time-tracking ()
  "Convert PHONE_STARTED/PHONE_ENDED properties to LOGBOOK entries."
  (interactive)
  (org-map-entries
   (lambda ()
     (let ((started (org-entry-get nil "PHONE_STARTED"))
           (ended (org-entry-get nil "PHONE_ENDED")))
       (when (and started ended)
         ;; Parse timestamps
         (let* ((start-time (org-parse-time-string started))
                (end-time (org-parse-time-string ended))
                (duration (org-time-stamp-to-now started)))
           
           ;; Add CLOCK entry to LOGBOOK
           (org-clock-into-drawer)
           (insert (format "CLOCK: %s--%s\n" started ended))
           
           ;; Add state change entry
           (insert (format "- State \"DONE\" from \"IN-PROGRESS\" %s\n" ended))
           
           ;; Remove phone properties
           (org-entry-delete nil "PHONE_STARTED")
           (org-entry-delete nil "PHONE_ENDED")
           
           (message "Processed time tracking for: %s" (org-get-heading t t t t))))))
   nil 'file))
```

**Bind to key:**
```elisp
(define-key org-mode-map (kbd "C-c p") 'org-process-phone-time-tracking)
```

**Note:** This is a simplified example. User will customize based on their workflow.

---

## Risk Assessment

### Phase 1 Risks

| Risk | Likelihood | Impact | Mitigation |
|------|-----------|--------|------------|
| Property parsing broken | Low | High | Verify OrgParser in testing phase |
| Duration calculation wrong | Medium | Low | Unit tests for edge cases (DST, midnight) |
| UI performance (recomposition) | Low | Medium | Use remember, only update per minute |
| Sync conflicts overwrite properties | Low | High | Existing conflict detection handles this |
| Multiple restarts create many properties | High | Medium | Document as Phase 1 limitation, Phase 2 fixes |

### Phase 2 Risks

| Risk | Likelihood | Impact | Mitigation |
|------|-----------|--------|------------|
| Comma parsing fails on edge cases | Medium | High | Comprehensive unit tests |
| Migration loses data | Low | Critical | Backup properties before migration |
| Total time calculation incorrect | Medium | Medium | Cross-validate with manual calculation |
| Emacs can't parse comma format | Low | High | Test with Emacs before deploying |

---

## Success Criteria

### Phase 1

- [ ] TODO → IN-PROGRESS writes `PHONE_STARTED` property
- [ ] IN-PROGRESS → DONE writes `PHONE_ENDED` property
- [ ] UI shows "Active: Xh Ym" for IN-PROGRESS tasks
- [ ] Duration calculation accurate (within 1 minute)
- [ ] Properties survive app restart (database re-sync)
- [ ] Org files readable by Emacs
- [ ] Syncthing syncs files successfully
- [ ] User can manually process properties in Emacs

### Phase 2

- [ ] Migration from Phase 1 format works without data loss
- [ ] Multiple sessions tracked accurately
- [ ] UI shows total time + current session
- [ ] Session appending works correctly
- [ ] Emacs can parse comma-separated format
- [ ] No regression in existing agenda features

---

## Timeline

### Phase 1 (Immediate)

| Task | Duration | Dependencies |
|------|----------|--------------|
| 1. Modify AgendaRepository | 2-3 hours | None |
| 2. Add duration display UI | 1-2 hours | Task 3 |
| 3. Update AgendaItem.Note | 30 min | None |
| 4. Verify OrgParser/Writer | 1 hour | None |
| 5. Unit testing | 2 hours | Tasks 1-4 |
| 6. Manual testing | 1 hour | Tasks 1-5 |
| **Total** | **7-9.5 hours** | **~1-2 days** |

### Phase 2 (Future)

| Task | Duration | Dependencies |
|------|----------|--------------|
| 1. Design session list parser | 2 hours | Phase 1 complete |
| 2. Implement session appending | 3 hours | Task 1 |
| 3. Migration from Phase 1 | 2 hours | Task 2 |
| 4. Enhanced UI (total time) | 2 hours | Task 2 |
| 5. Testing & validation | 3 hours | Tasks 1-4 |
| 6. Emacs integration testing | 2 hours | Tasks 1-5 |
| **Total** | **14 hours** | **~2 days** |

---

## Open Questions

### For Phase 1

1. **Timer icon:** Use `Icons.Default.Timer` or different icon?
2. **Real-time updates:** Should duration update every minute or only on recomposition?
3. **Property naming:** Is `PHONE_STARTED`/`PHONE_ENDED` acceptable or prefer different names?
4. **Error handling:** What to show if timestamp parse fails?

### For Phase 2

1. **Comma escaping:** What if task title contains comma? (Unlikely in timestamp context)
2. **Session limit:** Cap at N sessions per task? (Prevents unbounded growth)
3. **Automatic cleanup:** Should app auto-convert to Phase 2 format, or wait for Emacs?

---

## Related Documentation

- **ADR 003:** Agenda View with Orgzly-Inspired Architecture
- **docs/REQUIREMENTS.md:** FR8 (Browse Notes), FR10 (Local Org Files)
- **Org-Mode Manual:** [Properties and Columns](https://orgmode.org/manual/Properties-and-Columns.html)
- **Org-Mode Manual:** [Clocking Work Time](https://orgmode.org/manual/Clocking-Work-Time.html)

---

## Implementation Checklist

### Phase 1 Tasks

- [ ] Read existing `AgendaRepository.updateTodoState()` implementation
- [ ] Write `determineTimeTrackingProperties()` helper
- [ ] Write `formatOrgTimestamp()` helper
- [ ] Refactor `updateHeadlineState()` to `updateHeadlineStateAndProperties()`
- [ ] Add time tracking logic to `updateTodoState()`
- [ ] Verify `OrgParser` reads properties correctly
- [ ] Verify `OrgWriter` writes properties correctly
- [ ] Add `properties` field to `AgendaItem.Note` (if missing)
- [ ] Pass properties in `buildAgendaList()` (AgendaRepository)
- [ ] Add `calculateDuration()` helper to AgendaScreen.kt
- [ ] Add `formatDuration()` helper to AgendaScreen.kt
- [ ] Add duration UI to `AgendaItemNote` composable
- [ ] Write unit tests for time tracking logic
- [ ] Manual testing: basic flow (TODO → IN-PROGRESS → DONE)
- [ ] Manual testing: restart scenario (WAITING → IN-PROGRESS)
- [ ] Manual testing: Emacs sync and property visibility
- [ ] Update CHANGELOG.md
- [ ] Update this document with implementation notes

### Phase 2 Tasks (Future)

- [ ] Design session list data structure
- [ ] Write session parser (comma-separated format)
- [ ] Write session serializer
- [ ] Implement migration from Phase 1 format
- [ ] Update `determineTimeTrackingProperties()` for sessions
- [ ] Add `calculateTotalDuration()` helper
- [ ] Update UI to show total + current time
- [ ] Write unit tests for session tracking
- [ ] Write migration tests
- [ ] Manual testing: multiple sessions
- [ ] Emacs integration: test parsing comma format
- [ ] Emacs integration: write conversion function
- [ ] Document Emacs workflow
- [ ] Update CHANGELOG.md

---

## Approval & Sign-off

**Reviewed by:** Ram Sharan Rimal  
**Approved:** 2026-03-02  
**Implementation Start:** 2026-03-02  

---

*This document will be updated with implementation notes and any design changes discovered during development.*
