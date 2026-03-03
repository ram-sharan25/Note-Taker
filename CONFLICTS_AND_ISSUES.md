# Code Conflicts and Ambiguities Report
**Date:** 2026-03-01
**Version:** 0.8.0
**Severity Levels:** 🔴 CRITICAL | 🟠 HIGH | 🟡 MEDIUM | 🟢 LOW

---

## Executive Summary

Found **8 major conflict categories** affecting data consistency, user experience, and maintainability. The most critical issue is **two completely separate agenda implementations** that can cause data sync failures.

**Impact Assessment:**
- 🔴 **2 CRITICAL issues** — Can cause data loss or sync failures
- 🟠 **2 HIGH issues** — User features don't work as documented
- 🟡 **4 MEDIUM issues** — Maintenance and code quality problems

---

## 🔴 CRITICAL ISSUE #1: Dual Agenda Implementations

### The Problem
There are **TWO completely different implementations** of the agenda feature that conflict with each other:

| Component | AgendaViewModel | AgendaRepository |
|-----------|-----------------|------------------|
| **File** | `ui/viewmodels/AgendaViewModel.kt` | `data/repository/AgendaRepository.kt` |
| **Architecture** | File-based, parse-on-demand | Database-centric (Orgzly-inspired) |
| **Data Source** | Reads "phone_inbox/agenda.org" directly | Queries Room database |
| **Sync Strategy** | Manual `refresh()` | Background `OrgFileSyncWorker` |
| **buildAgendaItems** | Lines 103-181 (79 lines) | Lines 66-152 (87 lines) |
| **Recurring Tasks** | ❌ NO SUPPORT (shows wrong data) | ✅ Full support (++1d, .+1w, +1m) |
| **TODO State Update** | Line 191 `updateTodoState()` | Line 351 `updateTodoState()` |
| **State Storage** | In-memory only | Database + file |

### Code Evidence

**AgendaViewModel.kt (lines 86-95):**
```kotlin
fun refresh() {
    viewModelScope.launch {
        _isRefreshing.value = true
        try {
            val agendaFilePath = "phone_inbox/agenda.org"  // HARDCODED
            val contentResult = localFileManager.readFile(agendaFilePath)
            if (contentResult.isSuccess) {
                val content = contentResult.getOrThrow()
                val orgFile = orgParser.parse(content)  // PARSES EVERY TIME
                _agendaItems.value = buildAgendaItems(orgFile.getAllHeadlines())
            }
        } finally {
            _isRefreshing.value = false
        }
    }
}
```

**AgendaRepository.kt (lines 42-49):**
```kotlin
fun getAgendaItems(days: Int): Flow<List<AgendaItem>> {
    val today = LocalDate.now()
    val endTimestamp = today.plusDays(days.toLong()).atTime(LocalTime.MAX)
        .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()

    return noteDao.getAgendaItems(0, endTimestamp).map { results ->
        buildAgendaList(results, today, days)  // QUERIES DATABASE
    }
}
```

### The Conflict

1. **AgendaScreen.kt uses AgendaViewModel** → Bypasses entire database layer
2. **AgendaRepository methods exist but are NEVER CALLED** → Dead code
3. **Different recurring task handling:**
   - ViewModel: Treats repeaters as simple timestamps (WRONG)
   - Repository: Full expansion logic (CORRECT per Orgzly architecture)
4. **Sync desync:**
   - User updates TODO state via ViewModel → File updated, database stale
   - Background worker syncs → Overwrites file with stale database data
   - Result: **User loses their changes**

### Impact

🔴 **CRITICAL:**
- Data loss when user updates TODO states
- Recurring tasks show wrong dates in UI
- Background sync conflicts with UI updates
- User configuration (agenda files list) completely ignored
- Documentation claims "database-centric architecture" but UI uses file-based

### Root Cause

**M44 implementation added AgendaRepository (database-centric) but AgendaViewModel (file-based) was already there.** Both were kept, creating dual implementations.

### Resolution Options

**Option A: Use AgendaRepository (RECOMMENDED)**
- Delete AgendaViewModel's buildAgendaItems logic
- Wire AgendaScreen to AgendaRepository.getAgendaItems()
- Benefits: Recurring tasks work, scales to thousands of notes, matches documentation
- Effort: 2-3 hours

**Option B: Use AgendaViewModel**
- Delete AgendaRepository's agenda query methods
- Keep only file sync + TODO state update in Repository
- Benefits: Simpler (no database), faster for small files
- Drawbacks: Recurring tasks broken, doesn't scale, contradicts ADR 003
- Effort: 1-2 hours

**Option C: Hybrid (NOT RECOMMENDED)**
- Keep both, use Repository for data, ViewModel for UI state only
- Complexity: High, confusing for maintainers

---

## 🔴 CRITICAL ISSUE #2: Hardcoded Paths Conflict with User Configuration

### The Problem

**Hardcoded path `"phone_inbox/agenda.org"` appears in 3 files:**

1. **AgendaViewModel.kt** (line 87, 194)
2. **InboxCaptureViewModel.kt** (line 26)
3. **LocalOrgStorageBackend.kt** (line 83)

**But user can configure agenda files in Settings via:**
- `AgendaConfigManager.kt` → stores user's file list in DataStore
- `SettingsScreen.kt` → UI to edit agenda files (multi-line text field)

### Code Evidence

**SettingsScreen.kt (lines 234-246):**
```kotlin
OutlinedTextField(
    value = agendaFilesText,
    onValueChange = { agendaFilesText = it },
    label = { Text("Agenda Files") },
    placeholder = { Text("inbox.org\nBrain/tasks.org\nWork/projects.org") },
    modifier = Modifier.fillMaxWidth().height(120.dp),
    maxLines = 5
)
Text(
    "One file per line, relative to root folder",
    style = MaterialTheme.typography.bodySmall
)
```

**AgendaConfigManager.kt (lines 41-43):**
```kotlin
val agendaFiles: Flow<List<String>> = context.agendaDataStore.data.map { prefs ->
    prefs[Keys.AGENDA_FILES]?.split("\n")?.filter { it.isNotBlank() } ?: emptyList()
}
```

**But AgendaViewModel.kt IGNORES this configuration:**
```kotlin
val agendaFilePath = "phone_inbox/agenda.org"  // HARDCODED, config ignored
```

### The Conflict

```
User configures in Settings:
  inbox.org
  Brain/tasks.org
  Brain/ideas.org

AgendaViewModel reads: phone_inbox/agenda.org (hardcoded)

Result: User's configured files NEVER appear in agenda!
```

### Impact

🔴 **CRITICAL:**
- Settings UI is **completely non-functional** for agenda files
- User thinks they can configure multiple files but agenda only shows one hardcoded file
- Misleading UX: "One file per line" but only first file matters (and only if named "phone_inbox/agenda.org")
- InboxCaptureViewModel writes to hardcoded path, ignoring user's inbox file config

### Resolution

**Fix (2 hours):**
1. Replace all hardcoded `"phone_inbox/agenda.org"` with `agendaConfigManager.agendaFiles.first()`
2. Handle empty config gracefully (default to "phone_inbox/agenda.org" or show error)
3. Update InboxCaptureViewModel to respect inbox file config
4. Test multi-file agenda (requires wiring AgendaRepository instead of ViewModel)

---

## 🟠 HIGH ISSUE #3: Duplicate Queue Schemas (Confusing Data Model)

### The Problem

**Three separate "pending notes" concepts in database:**

1. **PendingNoteEntity** + **PendingNoteDao** (table: `pending_notes`)
   - Purpose: Queue for GitHub-bound notes
   - Used by: NoteRepository, NoteUploadWorker
   - Schema: id, text, filename, createdAt, status

2. **SyncQueueEntity** + **SyncQueueDao** (table: `sync_queue`)
   - Purpose: Queue for local org files syncing to GitHub
   - Used by: GitHubSyncManager, GitHubSyncWorker
   - Schema: id, filename, content, createdAt, status

3. **SubmissionEntity** + **SubmissionDao** (table: `submissions`)
   - Purpose: History of submitted notes
   - Used by: NoteRepository
   - Schema: id, timestamp, preview, success

### Code Evidence

**Nearly identical DAO queries:**

```kotlin
// PendingNoteDao.kt (line 13)
@Query("SELECT * FROM pending_notes WHERE status IN ('pending', 'failed')")
suspend fun getAllPending(): List<PendingNoteEntity>

// SyncQueueDao.kt (line 22)
@Query("SELECT * FROM sync_queue WHERE status = 'pending'")
suspend fun getAllPending(): List<SyncQueueEntity>
```

**Both have same status field:**
- "pending", "failed", "auth_failed"

### The Conflict

- **Unclear separation:** Why two queues for similar operations?
- **Duplicate logic:** Both manage retries, both track status
- **Confusing naming:** "PendingNote" vs "SyncQueue" doesn't clarify difference
- **Future pain:** Changes to queue logic need to update both DAOs

### Impact

🟠 **HIGH:**
- Maintenance burden (duplicate code)
- Confusion for new developers ("Which queue should I use?")
- Potential bugs if one queue logic diverges from the other

### Resolution

**Unify into single abstraction (4-6 hours):**
1. Create `QueuedItemEntity` with polymorphic type field
2. Single `QueuedItemDao` with type-filtered queries
3. Migrate both tables to unified schema
4. Update NoteRepository and GitHubSyncManager to use unified API

---

## 🟠 HIGH ISSUE #4: Conflicting File Write Methods

### The Problem

**LocalFileManager.kt has two methods that overlap:**

```kotlin
// Method 1: writeFile (line 140)
suspend fun writeFile(
    filename: String,
    content: String,
    relativePath: String = ""
): Result<Unit>

// Method 2: updateFile (line 316)
suspend fun updateFile(
    documentId: String,
    content: String
): Result<Unit>
```

**Usage confusion:**
- **AgendaViewModel** (line 207): Uses `updateFile(agendaFilePath, newContent)`
- **AgendaRepository** (line 379): Uses `writeFile(note.filename, newContent)`
- **InboxCaptureViewModel**: Uses `file.createFile()` OR `updateFile()`
- **LocalOrgStorageBackend**: Uses `writeFile()`

### The Conflict

- Same operation (update file content), different methods
- No clear guidance on which to use when
- `writeFile()` searches by filename, `updateFile()` takes path/ID
- Both return `Result<Unit>` with similar error handling

### Impact

🟠 **HIGH:**
- Code review confusion ("Why not use writeFile here?")
- Maintenance burden (need to understand both methods)
- Future refactoring harder (which method to keep?)

### Resolution

**Consolidate (2-3 hours):**
1. Deprecate `writeFile()`, migrate all usage to `updateFile()`
2. Or rename to clarify: `createOrUpdateFile()` vs `updateExistingFile()`
3. Document when to use each

---

## 🟡 MEDIUM ISSUE #5: Database Migration Bundling

### The Problem

**MIGRATION_3_4 creates 5 tables in one migration:**

```kotlin
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Creates:
        // 1. notes
        // 2. org_timestamps
        // 3. note_planning
        // 4. file_metadata
        // 5. todo_keywords_config
    }
}
```

### The Issue

- **Mixed concerns:** Agenda schema + file tracking + config bundled together
- **Future migrations harder:** Can't adjust one table without touching entire migration
- **Not granular:** All-or-nothing approach (no partial rollback)

### Impact

🟡 **MEDIUM:**
- Future schema changes require careful version management
- Harder to debug if one table creation fails
- Best practice: One concern per migration

### Resolution

**For future migrations (low priority):**
- Split into separate migrations for each logical group
- Current migration works fine, no need to change now

---

## 🟡 MEDIUM ISSUE #6: Unused Repository Methods (Dead Code)

### The Problem

**AgendaRepository.kt has methods that look complete but aren't called:**

1. **`getAgendaItems(days: Int)`** (line 42)
   - Returns Flow<List<AgendaItem>>
   - Fetches from database
   - **NOT USED** by AgendaScreen (uses AgendaViewModel instead)

2. **`getAgendaItemsFiltered(days, statusFilter)`** (line 51)
   - Filters by TODO states
   - **NOT USED** (ViewModel applies filter via combine())

### Code Evidence

**AgendaScreen.kt uses AgendaViewModel:**
```kotlin
val agendaItems by viewModel.agendaItems.collectAsState()
```

**NOT using AgendaRepository.getAgendaItems():**
```kotlin
// This method exists but is NEVER called:
fun getAgendaItems(days: Int): Flow<List<AgendaItem>> { ... }
```

### Impact

🟡 **MEDIUM:**
- Dead code in production
- Confusion: "Why does this method exist?"
- Wasted implementation effort

### Resolution

**Clean up (1 hour):**
1. If keeping AgendaViewModel approach: Delete unused Repository methods
2. If switching to Repository: Wire up getAgendaItems() in AgendaScreen

---

## 🟡 MEDIUM ISSUE #7: Documentation vs Reality Mismatch

### The Problem

**CLAUDE.md claims:**
> "ADR 003: Agenda View with Orgzly-Inspired Architecture — Status: Documented, not yet implemented"

**Reality:**
- ✅ AgendaRepository EXISTS (database-centric, Orgzly-inspired)
- ✅ OrgFileSyncWorker EXISTS
- ⚠️ BUT AgendaScreen uses AgendaViewModel (file-based, NOT Orgzly-inspired)

### Impact

🟡 **MEDIUM:**
- Onboarding confusion ("Wait, is agenda implemented or not?")
- Architecture documentation doesn't match actual UI implementation
- ADR 003 describes Repository but UI bypasses it

### Resolution

**Update docs (30 minutes):**
1. Mark ADR 003 as "Partially Implemented (Repository exists, UI uses different approach)"
2. Or update to "Complete" if switching UI to use Repository
3. Document the dual-implementation situation

---

## 🟡 MEDIUM ISSUE #8: Duplicate File Parsing Logic

### The Problem

**OrgParser.parse() called in multiple places:**

1. **AgendaViewModel.kt** (line 92): Parses on every refresh
2. **AgendaRepository.kt** (line 264): Parses for DB sync
3. **LocalOrgStorageBackend.kt**: Parses for submission
4. **BrowseScreen**: Parses for viewing

### The Issue

- No caching between layers
- Same file parsed multiple times
- Different error handling in each place
- Performance cost for large files

### Impact

🟡 **MEDIUM:**
- Performance: Repeated parsing of same file
- Consistency: Different error handling could cause divergent behavior
- Battery: Unnecessary CPU usage

### Resolution

**Add caching layer (4-6 hours):**
1. Create `ParsedOrgFileCache` with LRU eviction
2. Key by (filename, lastModified timestamp)
3. Wrap all parse() calls with cache lookup
4. Clear cache on file write

---

## RESOLUTION ROADMAP

### Phase 1: Critical Fixes (URGENT - 4-6 hours)

1. **Resolve dual agenda** (2-3 hours)
   - Decision: Use AgendaRepository (database-centric)
   - Wire AgendaScreen to Repository.getAgendaItems()
   - Delete ViewModel's buildAgendaItems
   - Test recurring tasks work

2. **Fix hardcoded paths** (1-2 hours)
   - Replace hardcoded "phone_inbox/agenda.org" with config
   - Test multi-file agenda
   - Verify inbox capture respects config

3. **Update documentation** (30 min)
   - Mark conflicts in CLAUDE.md
   - Update ADR 003 status

### Phase 2: High-Priority Cleanup (1 week)

4. **Consolidate queue schemas** (4-6 hours)
   - Design unified QueuedItemEntity
   - Write migration
   - Update all usage sites

5. **Consolidate file write methods** (2-3 hours)
   - Pick one method, deprecate other
   - Update all call sites
   - Document usage guidelines

### Phase 3: Medium-Priority Improvements (2-3 days)

6. **Remove dead code** (1 hour)
   - Delete unused Repository methods
   - Or wire them up properly

7. **Add file parsing cache** (4-6 hours)
   - Implement ParsedOrgFileCache
   - Wrap all parse() calls
   - Performance testing

---

## TESTING CHECKLIST

After fixes, verify:

- [ ] Agenda shows items from ALL configured files (not just hardcoded path)
- [ ] Recurring tasks (`++1d`, `.+1w`, `+1m`) display correct future dates
- [ ] TODO state updates persist across app restart
- [ ] Background sync doesn't overwrite user's TODO state changes
- [ ] Inbox capture writes to user-configured inbox file
- [ ] Settings → Agenda Files input actually affects agenda display
- [ ] Multi-file agenda works (3+ files configured)
- [ ] No database version conflicts on fresh install or upgrade

---

**Generated:** 2026-03-01
**Agent:** Claude Sonnet 4.5
**Codebase Version:** 0.8.0 (M44)
