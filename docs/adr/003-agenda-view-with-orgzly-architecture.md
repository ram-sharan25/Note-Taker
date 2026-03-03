# ADR 003: Agenda View with Orgzly-Inspired Architecture

## Status

Accepted

## Context

The note-taker app currently supports creating and viewing org-mode files locally via Storage Access Framework (SAF). Users want to:

1. **View today's agenda** - See TODO items scheduled or due today from their org files
2. **Update task states** - Cycle through TODO states (TODO → IN-PROGRESS → WAITING → DONE)
3. **Lock screen access** - View upcoming agenda items without unlocking phone
4. **Sync with Emacs** - Changes made in app should sync back to org files (via Syncthing)

### Current Implementation

The app has:
- ✅ **OrgParser** - Full org-mode parser that extracts headlines, TODO states, SCHEDULED/DEADLINE timestamps
- ✅ **OrgWriter** - Can write org files and update headlines while preserving formatting
- ✅ **LocalFileManager** - SAF-based file operations (read/write org files)
- ✅ **Room Database** - Used for note queue and submission history
- ❌ **No agenda view** - No way to filter and display scheduled items

### User Workflow

1. User manages org files in Emacs on desktop (work tasks, personal TODOs, projects)
2. Files sync to phone via Syncthing
3. User wants to view today's agenda on phone and mark items complete
4. Changes sync back to Emacs via Syncthing

### The Challenge

Naive implementation would parse org files every time the user opens the agenda screen:

```kotlin
// ❌ SLOW - Parses files on every screen open
fun loadAgenda() {
    val agendaFiles = listOf("inbox.org", "Brain/tasks.org", "Work/projects.org")
    val allHeadlines = agendaFiles.flatMap { filename ->
        val content = localFileManager.readFile(filename)
        orgParser.parse(content).getAllHeadlines()
    }
    val todayItems = allHeadlines.filter { hasScheduledOrDeadlineToday(it) }
    displayAgenda(todayItems)
}
```

**Problems:**
- Slow for large org files (thousands of headlines)
- No recurring task support (e.g., `SCHEDULED: <2026-03-01 ++1d>`)
- Complex date parsing logic needed
- Inefficient querying (can't filter by date at file level)

### Research: Orgzly Android

Orgzly is the mature, widely-used Android app for org-mode (10k+ stars on GitHub, 100k+ downloads on Play Store). Analysis of their architecture (see `docs/research/orgzly-architecture.md`) reveals they use a **database-centric approach**:

1. **Parse once, query many** - Org files parsed into normalized Room database
2. **Pre-computed timestamps** - SCHEDULED/DEADLINE stored as epoch milliseconds
3. **In-memory recurring expansion** - Repeaters expanded only for visible date range
4. **Query-based filtering** - Fast SQL queries instead of full file parsing

This architecture scales to **thousands of notes** with instant agenda loading.

## Decision

Adopt **Orgzly's database-centric architecture** for agenda implementation with these components:

1. **Normalized Database Schema** - Store parsed org headlines with timestamps in Room
2. **File Sync Worker** - Parse org files → insert into database on file changes
3. **Agenda Repository** - Query database + expand recurring tasks in-memory
4. **Configurable TODO Keywords** - User-defined TODO state progression (like Emacs)
5. **Lock Screen Notification** - Persistent notification showing next 3-5 agenda items

### Core Architecture

```
┌──────────────────┐
│  Org Files       │  (Synced via Syncthing)
│  - inbox.org     │
│  - tasks.org     │
└────────┬─────────┘
         │
         │ File change detected
         ↓
┌────────────────────────────────┐
│  OrgFileSyncWorker             │
│  - Parse with OrgParser        │
│  - Extract headlines           │
│  - Insert into Room DB         │
└────────┬───────────────────────┘
         ↓
┌────────────────────────────────┐
│  Room Database (Normalized)    │
│  ┌──────────────────────────┐  │
│  │ notes                    │  │
│  │ - id, filename, title    │  │
│  │ - todoState, body        │  │
│  └──────────────────────────┘  │
│  ┌──────────────────────────┐  │
│  │ org_timestamps           │  │
│  │ - year, month, day       │  │
│  │ - hour, minute           │  │
│  │ - timestamp (epoch ms)   │  │
│  │ - repeaterType, value    │  │
│  └──────────────────────────┘  │
│  ┌──────────────────────────┐  │
│  │ note_planning            │  │
│  │ - noteId                 │  │
│  │ - scheduledTimestampId   │  │
│  │ - deadlineTimestampId    │  │
│  └──────────────────────────┘  │
└────────┬───────────────────────┘
         │
         │ Fast SQL queries
         ↓
┌────────────────────────────────┐
│  AgendaRepository              │
│  - Query notes by date range   │
│  - Expand recurring tasks      │
│  - Group into day buckets      │
└────────┬───────────────────────┘
         ↓
┌────────────────────────────────┐
│  AgendaScreen                  │
│  - RecyclerView with sticky    │
│    day headers                 │
│  - Tap to cycle TODO states    │
│  - Swipe actions               │
└────────────────────────────────┘
```

### Database Schema

**Notes Table:**
```kotlin
@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val filename: String,              // "Brain/tasks.org"
    val headlineId: String,            // UUID for stable reference
    val level: Int,                    // Headline level (1 = *, 2 = **)
    val title: String,
    val todoState: String?,            // "TODO", "IN-PROGRESS", "DONE"
    val priority: String?,             // "A", "B", "C"
    val tags: String,                  // "work:urgent" (colon-separated)
    val body: String,
    val parentId: Long?,               // For nested headlines
    val position: Int,                 // Original position in file
    val lastModified: Long             // For sync tracking
)
```

**Timestamps Table:**
```kotlin
@Entity(tableName = "org_timestamps")
data class OrgTimestampEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val string: String,                // "<2026-03-01 Fri 09:00 ++1d>"
    val isActive: Boolean,             // < > (active) vs [ ] (inactive)
    val year: Int,
    val month: Int,
    val day: Int,
    val hour: Int?,
    val minute: Int?,
    val timestamp: Long,               // Epoch milliseconds (for fast queries)

    // Repeater: ++1d (catch-up), .+1w (restart), +1m (cumulative)
    val repeaterType: String?,         // "++", ".+", "+"
    val repeaterValue: Int?,           // 1, 2, 3, etc.
    val repeaterUnit: String?          // "h", "d", "w", "m", "y"
)
```

**Note Planning Table:**
```kotlin
@Entity(
    tableName = "note_planning",
    foreignKeys = [
        ForeignKey(entity = NoteEntity::class, parentColumns = ["id"], childColumns = ["noteId"], onDelete = CASCADE),
        ForeignKey(entity = OrgTimestampEntity::class, parentColumns = ["id"], childColumns = ["scheduledTimestampId"]),
        ForeignKey(entity = OrgTimestampEntity::class, parentColumns = ["id"], childColumns = ["deadlineTimestampId"])
    ]
)
data class NotePlanningEntity(
    @PrimaryKey val noteId: Long,
    val scheduledTimestampId: Long?,
    val deadlineTimestampId: Long?,
    val closedTimestampId: Long?
)
```

### Agenda Item Types (Sealed Class)

Following Orgzly's pattern:

```kotlin
sealed class AgendaItem(open val id: Long) {
    /**
     * Header for overdue items section
     */
    data class Overdue(override val id: Long) : AgendaItem(id)

    /**
     * Date divider (sticky header)
     */
    data class Day(
        override val id: Long,
        val date: LocalDate,
        val formattedDate: String  // "Mon, 25 Sep"
    ) : AgendaItem(id)

    /**
     * Individual agenda entry
     */
    data class Note(
        override val id: Long,
        val noteId: Long,
        val title: String,
        val todoState: String?,
        val timeType: TimeType,        // SCHEDULED or DEADLINE
        val timestamp: Long,
        val formattedTime: String?,    // "09:00" or null
        val isOverdue: Boolean = false,
        val tags: List<String> = emptyList()
    ) : AgendaItem(id)
}

enum class TimeType { SCHEDULED, DEADLINE }
```

### Recurring Task Expansion

Based on Orgzly's `AgendaUtils.expandOrgDateTime()`:

```kotlin
/**
 * Expand recurring timestamp for date range
 * Example: <2026-03-01 Sat 09:00 ++1d> with range [2026-03-01 to 2026-03-07]
 * Returns: [2026-03-01 09:00, 2026-03-02 09:00, 2026-03-03 09:00, ...]
 */
fun expandRecurringTimestamp(
    timestamp: OrgTimestampEntity,
    startDate: LocalDate,
    endDate: LocalDate
): List<LocalDateTime> {
    if (timestamp.repeaterType == null) {
        // Non-recurring: return single instance if in range
        val date = LocalDate.of(timestamp.year, timestamp.month, timestamp.day)
        return if (date in startDate..endDate) {
            listOf(date.atTime(timestamp.hour ?: 0, timestamp.minute ?: 0))
        } else {
            emptyList()
        }
    }

    val result = mutableListOf<LocalDateTime>()
    var current = LocalDate.of(timestamp.year, timestamp.month, timestamp.day)

    // Expand instances within range
    while (current <= endDate) {
        if (current >= startDate) {
            result.add(current.atTime(timestamp.hour ?: 0, timestamp.minute ?: 0))
        }

        // Advance by repeater interval
        current = when (timestamp.repeaterUnit) {
            "h" -> current  // Hour repeaters handled separately
            "d" -> current.plusDays(timestamp.repeaterValue!!.toLong())
            "w" -> current.plusWeeks(timestamp.repeaterValue!!.toLong())
            "m" -> current.plusMonths(timestamp.repeaterValue!!.toLong())
            "y" -> current.plusYears(timestamp.repeaterValue!!.toLong())
            else -> break
        }
    }

    return result
}
```

**Repeater Types** (Emacs org-mode standard):
- `++` (catch-up) - Shift from original date (e.g., daily habit)
- `.+` (restart) - Shift from completion date
- `+` (cumulative) - Don't skip missed instances

### Day Bucket Architecture

```kotlin
fun buildAgendaList(
    notes: List<NoteWithPlanning>,
    agendaDays: Int
): List<AgendaItem> {
    val now = LocalDate.now()
    val overdueItems = mutableListOf<AgendaItem.Note>()

    // Create buckets for each day in range
    val dayBuckets = (0 until agendaDays).associate { offset ->
        now.plusDays(offset.toLong()) to mutableListOf<AgendaItem.Note>()
    }

    notes.forEach { note ->
        // Expand SCHEDULED timestamp
        note.scheduledTimestamp?.let { ts ->
            expandRecurringTimestamp(ts, now, now.plusDays(agendaDays.toLong()))
                .forEach { dateTime ->
                    val date = dateTime.toLocalDate()
                    val item = AgendaItem.Note(
                        id = generateId(),
                        noteId = note.id,
                        title = note.title,
                        todoState = note.todoState,
                        timeType = TimeType.SCHEDULED,
                        timestamp = dateTime.toEpochSecond(),
                        formattedTime = dateTime.format("HH:mm"),
                        isOverdue = date < now
                    )

                    if (date < now) {
                        overdueItems.add(item)
                    } else {
                        dayBuckets[date]?.add(item)
                    }
                }
        }

        // Same for DEADLINE
        note.deadlineTimestamp?.let { /* similar logic */ }
    }

    // Flatten to list with headers
    return buildList {
        if (overdueItems.isNotEmpty()) {
            add(AgendaItem.Overdue(0))
            addAll(overdueItems.sortedBy { it.timestamp })
        }

        dayBuckets.forEach { (date, items) ->
            if (items.isNotEmpty()) {
                add(AgendaItem.Day(date.toEpochDay(), date, formatDate(date)))
                addAll(items.sortedBy { it.timestamp })
            }
        }
    }
}
```

### TODO State Management

Configurable TODO keywords like Emacs:

```kotlin
data class TodoKeywordsConfig(
    val sequence: String  // "TODO IN-PROGRESS WAITING | DONE CANCELLED"
) {
    private val parts = sequence.split("|")
    val activeTodos: List<String> = parts[0].trim().split(" ").filter { it.isNotBlank() }
    val doneTodos: List<String> = parts.getOrNull(1)?.trim()?.split(" ")?.filter { it.isNotBlank() } ?: emptyList()

    fun isDone(state: String?): Boolean = state != null && doneTodos.contains(state)

    fun cycleState(current: String?): String {
        val allStates = activeTodos + doneTodos
        val currentIndex = allStates.indexOf(current)

        return if (currentIndex == -1) {
            activeTodos.first()  // No state → first TODO
        } else {
            allStates[(currentIndex + 1) % allStates.size]  // Cycle through
        }
    }
}
```

**State Cycling Example:**
```
TODO → IN-PROGRESS → WAITING → DONE → CANCELLED → TODO (loops)
```

### File Sync Strategy

**When to sync:**
1. On app open (check file lastModified timestamps)
2. On manual refresh (swipe-to-refresh in agenda)
3. Periodic background sync (WorkManager, every 15 minutes when app active)

**Sync logic:**
```kotlin
suspend fun syncFileToDatabase(filename: String) {
    val file = localFileManager.getFileMetadata(filename)
    val lastSynced = database.fileMetadataDao().getLastSynced(filename)

    if (file.lastModified > lastSynced) {
        // File changed, re-parse
        val content = localFileManager.readFile(filename).getOrThrow()
        val orgFile = orgParser.parse(content)

        database.withTransaction {
            // Delete old entries for this file
            database.noteDao().deleteByFilename(filename)

            // Insert new entries
            orgFile.getAllHeadlines().forEach { headline ->
                val noteId = insertNote(headline, filename)
                headline.scheduled?.let { insertTimestamp(it, noteId, "scheduled") }
                headline.deadline?.let { insertTimestamp(it, noteId, "deadline") }
            }

            // Update sync timestamp
            database.fileMetadataDao().updateLastSynced(filename, file.lastModified)
        }
    }
}
```

**Write-back to org file:**
```kotlin
suspend fun updateTodoState(noteId: Long, newState: String) {
    // 1. Update database
    database.noteDao().updateState(noteId, newState)

    // 2. Read org file
    val note = database.noteDao().getById(noteId)
    val content = localFileManager.readFile(note.filename).getOrThrow()
    val orgFile = orgParser.parse(content)

    // 3. Find and update headline
    val updatedFile = orgFile.copy(
        headlines = updateHeadlineState(orgFile.headlines, note.headlineId, newState)
    )

    // 4. Write back to file
    val newContent = orgWriter.writeFile(updatedFile)
    localFileManager.updateFile(note.filename, newContent)
}

private fun updateHeadlineState(
    headlines: List<OrgNode.Headline>,
    headlineId: String,
    newState: String
): List<OrgNode.Headline> {
    return headlines.map { headline ->
        if (headline.id == headlineId) {
            headline.copy(todoState = newState)
        } else if (headline.children.isNotEmpty()) {
            headline.copy(children = updateHeadlineState(headline.children, headlineId, newState))
        } else {
            headline
        }
    }
}
```

### UI Patterns

**RecyclerView with Sticky Day Headers:**
```kotlin
class AgendaAdapter : ListAdapter<AgendaItem, RecyclerView.ViewHolder>(), StickyHeaders {
    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is AgendaItem.Overdue -> TYPE_OVERDUE
            is AgendaItem.Day -> TYPE_DAY
            is AgendaItem.Note -> TYPE_NOTE
        }
    }

    override fun isStickyHeader(position: Int): Boolean {
        return getItemViewType(position) in listOf(TYPE_OVERDUE, TYPE_DAY)
    }
}
```

**Swipe Actions:**
```kotlin
// Swipe right → Mark DONE
// Swipe left → Show context menu
attachItemTouchHelper(recyclerView, adapter) { viewHolder, direction ->
    when (direction) {
        ItemTouchHelper.RIGHT -> viewModel.markDone(viewHolder.noteId)
        ItemTouchHelper.LEFT -> showContextMenu(viewHolder)
    }
}
```

**Lock Screen Notification:**
```kotlin
// Persistent notification with next 3-5 items
NotificationCompat.Builder(context, AGENDA_CHANNEL_ID)
    .setSmallIcon(R.drawable.ic_agenda)
    .setContentTitle("Today's Agenda")
    .setStyle(NotificationCompat.InboxStyle()
        .addLine("09:00 TODO Team standup")
        .addLine("14:00 DEADLINE Finish quarterly report")
        .addLine("16:00 TODO Grocery shopping")
    )
    .setOngoing(true)  // Persistent
    .addAction(R.drawable.ic_check, "Mark Done", markDonePendingIntent)
    .build()
```

## Alternatives Considered

### Option A: Parse Org Files on Every Agenda Load

```kotlin
fun loadAgenda() {
    val files = listOf("inbox.org", "tasks.org")
    val headlines = files.flatMap { filename ->
        val content = localFileManager.readFile(filename)
        orgParser.parse(content).getAllHeadlines()
    }
    val today = headlines.filter { hasScheduledOrDeadlineToday(it) }
    displayAgenda(today)
}
```

**Pros:**
- Simple implementation (~100 lines of code)
- No database migration needed
- Always shows latest file content (no sync lag)

**Cons:**
- **Slow for large files** - Parsing 1000 headlines takes 200-500ms on each screen open
- **No recurring task support** - Complex to expand repeaters in-memory every time
- **Battery drain** - Repeated file I/O and parsing
- **No offline caching** - Can't show agenda if file access fails

**Decision:** Rejected. Does not scale to power users with thousands of notes.

---

### Option B: Lightweight Cache with File Hash

Store parsed headlines in memory, invalidate when file hash changes:

```kotlin
data class FileCache(
    val hash: String,
    val headlines: List<OrgNode.Headline>,
    val timestamp: Long
)

val cache = mutableMapOf<String, FileCache>()

fun loadAgendaCached(filename: String): List<OrgNode.Headline> {
    val currentHash = localFileManager.getFileHash(filename)
    val cached = cache[filename]

    return if (cached?.hash == currentHash) {
        cached.headlines  // Use cached
    } else {
        // Re-parse
        val content = localFileManager.readFile(filename)
        val headlines = orgParser.parse(content).getAllHeadlines()
        cache[filename] = FileCache(currentHash, headlines, System.currentTimeMillis())
        headlines
    }
}
```

**Pros:**
- Faster than full re-parse on every load
- Simple to implement
- No database schema needed

**Cons:**
- **Memory intensive** - Caching thousands of headlines in RAM
- **Lost on app restart** - Must re-parse on cold start
- **No querying** - Still filters in-memory (can't leverage SQL indexes)
- **Recurring tasks still hard** - No place to store expanded instances

**Decision:** Rejected. Marginal improvement over Option A, doesn't solve core problems.

---

### Option C: Database Without Normalized Timestamps

Store notes in database but keep timestamps as strings:

```kotlin
@Entity
data class NoteEntity(
    val id: Long,
    val title: String,
    val todoState: String?,
    val scheduledString: String?,  // "<2026-03-01 Sat ++1d>"
    val deadlineString: String?
)
```

**Pros:**
- Simpler schema than full normalization
- Faster to implement
- Still allows basic querying

**Cons:**
- **Can't query by date** - Need to parse timestamp string in SQL (slow) or in-memory (defeats purpose)
- **Recurring expansion still manual** - No pre-parsed repeater values
- **Poor index performance** - Can't create index on date range

**Decision:** Rejected. Loses main benefit of database approach (fast date queries).

---

### Option D: Use SQLite FTS (Full-Text Search) for Org Files

Store entire org file content in FTS table, use text search for timestamps:

```kotlin
@Entity
@Fts4
data class OrgFileContentEntity(
    @PrimaryKey val filename: String,
    val content: String
)

// Query for scheduled items today
SELECT * FROM org_file_content WHERE content MATCH 'SCHEDULED:<2026-03-01'
```

**Pros:**
- Very fast text search
- Simple schema
- Good for keyword search across files

**Cons:**
- **Not designed for structured data** - Org headlines are structured, not just text
- **Can't query by date range** - FTS doesn't understand dates
- **No recurring support** - Can't expand repeaters
- **Overkill** - Don't need full-text search, need structured queries

**Decision:** Rejected. Wrong tool for the job.

---

### Option E: Hybrid - Database for Metadata, Parse for Display

Store minimal metadata in database (title, filename, scheduledAt timestamp), parse file when displaying:

```kotlin
@Entity
data class NoteMetadataEntity(
    val id: Long,
    val filename: String,
    val title: String,
    val scheduledAt: Long?,  // Epoch timestamp
    val deadlineAt: Long?
)

// Query for today's items (fast)
val todayMetadata = db.noteDao().getScheduledBetween(todayStart, todayEnd)

// Parse file to get full headline details (slower, but only for displayed items)
val fullHeadlines = todayMetadata.map { meta ->
    val content = localFileManager.readFile(meta.filename)
    val orgFile = orgParser.parse(content)
    orgFile.findHeadline(meta.title)
}
```

**Pros:**
- Fast queries (indexed timestamps)
- Smaller database (no body text, no properties)
- Always shows latest file content for displayed items

**Cons:**
- **Still parses files** - Just fewer of them (defeats caching purpose)
- **Recurring tasks hard** - Where to store expanded instances?
- **Sync complexity** - Must keep metadata in sync with files
- **Partial benefit** - Doesn't fully leverage database

**Decision:** Rejected. Complexity of both approaches without full benefits of either.

---

## Consequences

### Positive

**Performance:**
- ⚡ **Instant agenda loading** - SQL query with indexed timestamps takes <10ms vs 200-500ms file parsing
- ⚡ **Scalable** - Handles thousands of notes without performance degradation
- ⚡ **Battery efficient** - No repeated file parsing, single parse on file change
- ⚡ **Smooth scrolling** - RecyclerView with pre-computed data

**Functionality:**
- ✅ **Recurring tasks** - Full support for `++1d`, `.+1w`, `+1m` repeaters (Emacs-compatible)
- ✅ **Fast date queries** - "Show next 7 days" is instant SQL query
- ✅ **Flexible filtering** - Can add saved searches (e.g., "Overdue", "This week", "High priority")
- ✅ **Offline support** - Agenda cached in database, works without file access

**User Experience:**
- 👍 **Lock screen agenda** - Notification can query database without waking app
- 👍 **Background sync** - WorkManager can sync files in background
- 👍 **Instant TODO state updates** - Update database immediately, write file async
- 👍 **Sticky day headers** - Clear visual grouping (like Orgzly)

**Maintainability:**
- 📚 **Proven architecture** - Based on Orgzly (100k+ users, mature codebase)
- 📚 **Testable** - Database queries easy to unit test
- 📚 **Debuggable** - Can inspect database with Database Inspector

### Negative

**Complexity:**
- ⚠️ **Larger codebase** - ~1000 lines of code (entities, DAOs, sync logic)
- ⚠️ **Database migration** - Must maintain schema versions, write migrations
- ⚠️ **Sync logic** - Must detect file changes and re-sync
- ⚠️ **Two sources of truth** - Database and org files must stay in sync

**Storage:**
- 💾 **Database size** - ~1KB per note, ~1MB for 1000 notes (acceptable)
- 💾 **Duplicate data** - Org file content exists in both file and database

**Sync Challenges:**
- 🔄 **Sync lag** - Database may be stale if file changed externally (Syncthing, Emacs edit)
- 🔄 **Conflict resolution** - If user edits in app and Emacs simultaneously, must handle conflicts
- 🔄 **Headline IDs** - Need stable IDs to match database records to file headlines (use UUID in properties drawer)

**Edge Cases:**
- 🐛 **Malformed org files** - Parser errors must not crash sync
- 🐛 **Large files** - Very large org files (>10MB) may cause sync delays
- 🐛 **Nested headlines** - Deep nesting (>10 levels) adds complexity

**Migration Cost:**
- 🕐 **Development time** - Estimated 2-3 weeks for full implementation + testing
- 🕐 **Testing burden** - Must test sync logic, recurring expansion, state updates

### Mitigation Strategies

**Sync Lag:**
- Show "Syncing..." indicator when files modified
- Add manual "Refresh" button
- Background sync every 15 minutes when app active

**Conflict Resolution:**
- File modification always wins (app reads file, updates database)
- Before writing TODO state change, check file lastModified timestamp
- If file changed externally, re-parse and retry state update

**Headline IDs:**
- Generate UUID for each headline on first sync
- Store in `:PROPERTIES:` drawer as `:ID:` (Emacs org-mode standard)
- Use ID to match database records to file headlines across edits

**Large Files:**
- Parse in background thread with coroutines
- Show progress indicator for files >5000 lines
- Consider splitting very large files (user responsibility)

**Database Size:**
- Automatically vacuum database monthly (SQLite VACUUM)
- Prune old CLOSED items (configurable, default: keep 1 year)

## Technical Details

### Room Database Entities

**Full Schema:**
```kotlin
@Database(
    entities = [
        NoteEntity::class,
        OrgTimestampEntity::class,
        NotePlanningEntity::class,
        FileMetadataEntity::class,
        TodoKeywordsConfigEntity::class
    ],
    version = 1
)
abstract class OrgDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao
    abstract fun timestampDao(): OrgTimestampDao
    abstract fun planningDao(): NotePlanningDao
    abstract fun fileMetadataDao(): FileMetadataDao
    abstract fun todoKeywordsDao(): TodoKeywordsDao
}
```

**File Metadata (for sync tracking):**
```kotlin
@Entity(tableName = "file_metadata")
data class FileMetadataEntity(
    @PrimaryKey val filename: String,
    val lastSynced: Long,          // Epoch millis when last synced
    val lastModified: Long,        // File lastModified timestamp
    val hash: String               // SHA-256 hash for integrity check
)
```

**TODO Keywords Config:**
```kotlin
@Entity(tableName = "todo_keywords_config")
data class TodoKeywordsConfigEntity(
    @PrimaryKey val id: Int = 0,   // Singleton (only one config)
    val sequence: String            // "TODO IN-PROGRESS WAITING | DONE CANCELLED"
)
```

### DAO Queries

**Agenda Queries:**
```kotlin
@Dao
interface NoteDao {
    @Query("""
        SELECT n.*, ts.timestamp, ts.repeaterType, ts.repeaterValue, ts.repeaterUnit
        FROM notes n
        INNER JOIN note_planning np ON n.id = np.noteId
        LEFT JOIN org_timestamps ts ON np.scheduledTimestampId = ts.id OR np.deadlineTimestampId = ts.id
        WHERE ts.timestamp >= :startTimestamp AND ts.timestamp <= :endTimestamp
        ORDER BY ts.timestamp ASC
    """)
    fun getAgendaItems(startTimestamp: Long, endTimestamp: Long): Flow<List<NoteWithPlanning>>

    @Query("UPDATE notes SET todoState = :newState WHERE id = :noteId")
    suspend fun updateState(noteId: Long, newState: String)

    @Query("DELETE FROM notes WHERE filename = :filename")
    suspend fun deleteByFilename(filename: String)
}
```

### Settings UI

**Agenda Configuration in SettingsScreen:**
```kotlin
Card {
    Column {
        Text("Agenda Configuration", style = MaterialTheme.typography.titleMedium)

        // Agenda files list
        OutlinedTextField(
            value = agendaFiles,
            onValueChange = { agendaFiles = it },
            label = { Text("Agenda files") },
            placeholder = { Text("inbox.org\nBrain/tasks.org\nWork/projects.org") },
            modifier = Modifier.fillMaxWidth().height(120.dp),
            maxLines = 5
        )
        Text(
            "One file per line, relative to root folder",
            style = MaterialTheme.typography.bodySmall
        )

        Spacer(height = 16.dp)

        // TODO keywords
        OutlinedTextField(
            value = todoKeywords,
            onValueChange = { todoKeywords = it },
            label = { Text("TODO keywords") },
            placeholder = { Text("TODO IN-PROGRESS WAITING | DONE CANCELLED") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            "Space-separated. Use | to mark done states",
            style = MaterialTheme.typography.bodySmall
        )

        Spacer(height = 16.dp)

        // Agenda days range
        Text("Agenda range: $agendaDays days")
        Slider(
            value = agendaDays.toFloat(),
            onValueChange = { agendaDays = it.toInt() },
            valueRange = 1f..30f,
            steps = 29
        )

        Button(
            onClick = { viewModel.saveAgendaConfig(agendaFiles, todoKeywords, agendaDays) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Save Agenda Configuration")
        }
    }
}
```

### Initial Sync Flow

**On first app open after enabling agenda:**
```kotlin
suspend fun performInitialSync() {
    val agendaFiles = settings.getAgendaFiles()  // ["inbox.org", "Brain/tasks.org"]

    agendaFiles.forEach { filename ->
        try {
            syncFileToDatabase(filename)
        } catch (e: Exception) {
            Log.e("AgendaSync", "Failed to sync $filename", e)
            // Continue with other files
        }
    }
}
```

**Periodic sync (WorkManager):**
```kotlin
class AgendaSyncWorker @Inject constructor(
    context: Context,
    params: WorkerParameters,
    private val agendaRepository: AgendaRepository
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        return try {
            agendaRepository.syncAllFiles()
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}

// Schedule periodic sync
WorkManager.getInstance(context).enqueueUniquePeriodicWork(
    "agenda_sync",
    ExistingPeriodicWorkPolicy.KEEP,
    PeriodicWorkRequestBuilder<AgendaSyncWorker>(15, TimeUnit.MINUTES).build()
)
```

## Migration Path

### Phase 1: Database Schema & Sync (Week 1-2)

**Tasks:**
1. Create Room entities (NoteEntity, OrgTimestampEntity, NotePlanningEntity)
2. Write DAOs with queries
3. Implement OrgFileSyncWorker (parse → insert into DB)
4. Add file change detection (compare lastModified timestamps)
5. Implement initial sync on app open

**Testing:**
- Unit tests for sync logic
- Integration tests with sample org files
- Test with 100+ headlines

**Deliverable:** Database populated from org files, sync working

---

### Phase 2: Agenda Repository & UI (Week 3)

**Tasks:**
1. Implement AgendaRepository (query DB, expand recurring tasks)
2. Create AgendaItem sealed class
3. Implement day bucket architecture
4. Build RecyclerView adapter with sticky headers
5. Add swipe-to-refresh

**Testing:**
- Unit tests for recurring expansion
- UI tests for RecyclerView
- Test with various repeater types (++1d, .+1w, +1m)

**Deliverable:** Working agenda screen showing scheduled items

---

### Phase 3: TODO State Updates (Week 4)

**Tasks:**
1. Implement TODO state cycling (tap to cycle)
2. Write-back to org file (update DB → write file)
3. Add swipe actions (swipe right = mark done)
4. Implement conflict resolution (check lastModified before write)

**Testing:**
- Test state cycling with various TODO keyword configs
- Test write-back preserves file formatting
- Test conflict resolution (concurrent edits)

**Deliverable:** Full CRUD operations on agenda items

---

### Phase 4: Lock Screen & Polish (Week 5)

**Tasks:**
1. Implement persistent notification with next 3-5 items
2. Add "Mark Done" action button in notification
3. Update notification on state changes
4. Add Settings UI for agenda configuration
5. Polish UI (animations, loading states, error handling)

**Testing:**
- Test notification updates
- Test notification actions
- Test on various Android versions (API 29-36)

**Deliverable:** Production-ready agenda feature

---

### Phase 5: Advanced Features (Future)

**Potential additions:**
- Saved searches ("Overdue", "This week", "High priority")
- Agenda widget (home screen)
- Bulk operations (mark multiple done)
- Calendar view (month view with dots for scheduled days)
- Repeater customization UI (instead of text input)

## Open Questions

### 1. How to generate stable headline IDs?

**Options:**
- **UUID in properties drawer** (Orgzly's approach)
  ```org
  * TODO Buy groceries
    :PROPERTIES:
    :ID: 550e8400-e29b-41d4-a716-446655440000
    :END:
  ```
  Pros: Emacs org-mode standard, stable across edits
  Cons: Adds properties drawer to every headline

- **Hash of title + position** (computed)
  ```kotlin
  val id = sha256("$title-$position-$filename")
  ```
  Pros: No file modification
  Cons: Breaks if headline moves or title changes

**Decision:** Use UUID in properties drawer (`:ID:`). Emacs-compatible, stable.

---

### 2. How to handle recurring tasks completed before next instance?

Example: Daily habit `++1d`, completed today. Should it show tomorrow's instance immediately?

**Orgzly's Approach:**
- Keep showing current instance until next instance date
- Use `.+` repeater if you want "restart from today" behavior

**Decision:** Follow Orgzly (Emacs standard). Different repeater types handle this:
- `++` (catch-up): Show next scheduled instance from original date
- `.+` (restart): Show next instance from completion date
- `+` (cumulative): Show all missed instances

---

### 3. How to handle very large org files (>10,000 headlines)?

**Options:**
- Paginate sync (sync 1000 headlines at a time)
- Show progress indicator
- Warn user if file > threshold

**Decision:** Start simple (sync entire file), add pagination if users report issues. Most org files are <1000 headlines.

---

### 4. Should we support sub-hour repeaters (`++30m`, `++2h`)?

**Orgzly's Approach:** Yes, they expand hour repeaters.

**Decision:** Yes, support hour repeaters. Useful for medication reminders, pomodoro timers.

---

### 5. How to handle deleted headlines (present in DB but not in file)?

**Options:**
- Delete from DB on next sync (clean)
- Mark as deleted but keep (preserve history)

**Decision:** Delete from DB. User's org file is source of truth.

---

### 6. Should agenda show all TODO items or only scheduled/deadline items?

**Options:**
- **Only scheduled/deadline** (Orgzly default)
- **All TODO items** (with filter toggle)

**Decision:** Only scheduled/deadline by default. Add "All TODOs" filter in Phase 5.

---

## References

### Orgzly Research
- [Orgzly Android Repository](https://github.com/orgzly/orgzly-android) - Main codebase
- Detailed analysis in `docs/research/orgzly-architecture.md` (internal doc)

### Org-Mode Specifications
- [Org Mode Manual - Timestamps](https://orgmode.org/manual/Timestamps.html) - SCHEDULED, DEADLINE, repeaters
- [Org Mode Manual - TODO Keywords](https://orgmode.org/manual/TODO-Extensions.html) - Configurable workflows
- [Org Element Parser](https://orgmode.org/worg/dev/org-element-api.html) - Official parser spec

### Android Architecture
- [Room Database Documentation](https://developer.android.com/training/data-storage/room) - Jetpack Room
- [WorkManager Guide](https://developer.android.com/topic/libraries/architecture/workmanager) - Background sync
- [Storage Access Framework](https://developer.android.com/guide/topics/providers/document-provider) - File access

### Similar Apps
- [Orgzly](https://github.com/orgzly/orgzly-android) - Feature-complete org-mode app
- [Orgro](https://github.com/amake/orgro) - Read-only org viewer
- [Organice](https://github.com/200ok-ch/organice) - Web-based org editor

### Libraries
- [org-java](https://github.com/orgzly/org-java) - Java org-mode parser (used by Orgzly)
- [StickyHeadersRecyclerView](https://github.com/timehop/sticky-headers-recyclerview) - Sticky headers UI

## Decision Date

2026-03-01

## Decision Makers

Ram Sharan Rimal (Product Owner)

## Related ADRs

- ADR 001: PAT Over OAuth for Authentication - Establishes preference for simplicity
- ADR 002: Nepali Language Support - Phased approach precedent (start simple, iterate)
- Future ADR: Lock Screen Widget Implementation (Android 15+ Glance API)

---

**Implementation Status:** ✅ Phase 1, 2 & 3 Complete (Database, Sync, Expansion, UI, State Management)

**Additional:** Pomodoro Timer (FR17) was built on top of the Phase 3 TODO state infrastructure. The `StateSelectionDialog` — originally defined in `AgendaScreen.kt` — was extracted to its own file (`ui/screens/agenda/StateSelectionDialog.kt`) so it can be shared between `AgendaScreen` and `MainScreen` (for the Pomodoro timer overlay). The dialog gained a Pomodoro prompt flow: tapping IN-PROGRESS keeps the dialog open and offers "Start Pomodoro" / "Done" options.

**Next Steps:** Phase 4 (Lock screen persistent notification)

### Final Implementation Notes (Phase 1-3)

The implementation followed the Orgzly-inspired architecture with a few optimizations:

- **Database:** Added `NoteEntity`, `OrgTimestampEntity`, `NotePlanningEntity`, `FileMetadataEntity`, and `TodoKeywordsConfigEntity`.
- **Sync:** `AgendaRepository` handles syncing configured `.org` files. It uses SHA-256 hashing (`FileMetadataEntity`) to skip re-parsing unchanged files.
- **Expansion:** `expandRecurringTimestamp` uses `java.time` and includes "jump-ahead" logic for efficient expansion of old recurring tasks.
- **Refresh Functionality:** Added `clearAndResyncAll()` method for manual refresh button:
  - Clears all agenda database data (notes, timestamps, file metadata)
  - Forces full re-parse and re-sync from org files
  - Guarantees fresh data after external edits (Emacs, Syncthing, etc.)
  - Shows spinner in UI during refresh operation
  - Located at `AgendaScreen.kt:84` (refresh icon button in top bar)
- **UI:** `AgendaScreen` uses a `LazyColumn` with `stickyHeader` (for day buckets) and `AgendaItem` sealed class for different row types.
- **Task Item Display (`AgendaNoteItem`):**
  - Shows TODO state chip + title only
  - Tags are not displayed in agenda view
  - Filename and time type labels (`S:`, `D:`) are not shown
  - If the task has a scheduled time: green chip in the **bottom-right corner** of the card
  - If the task has a deadline time: red chip in the **bottom-right corner** of the card
  - No chip shown if task has no scheduled/deadline time
- **Navigation:** Integrated into `AppNavGraph` and added a Calendar icon to `TopicBar`.
- **Configuration:** New section in `SettingsScreen` allows users to list agenda files, set the range (1-30 days), and define TODO keywords.
- **Worker:** `OrgFileSyncWorker` provides background sync capabilities.
