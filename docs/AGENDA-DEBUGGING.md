# Agenda Debugging Guide

This guide helps you debug issues with agenda item display.

## Overview

The agenda system syncs org-mode files into a Room database and displays **ALL items** from those files.

**Key Principle**: `agenda.org` (or other configured agenda files) is the **source of truth**. The app doesn't filter by date/state - it shows everything in the file. Filtering happens on the desktop (Emacs) when creating the agenda file.

## Architecture

```
agenda.org (source file)
    ↓ (parsed by OrgParser)
Room Database (notes, org_timestamps, note_planning)
    ↓ (queried by NoteDao)
AgendaRepository (builds agenda items)
    ↓ (flows to)
AgendaViewModel
    ↓ (rendered by)
AgendaScreen (UI)
```

## Common Issues

### Issue: Items Not Showing in Agenda

**Symptoms**: An item exists in `agenda.org` but doesn't appear in the app.

**Debugging Steps**:

1. **Verify the item is in agenda.org**:
   - Open `agenda.org` file and search for the item
   - Check that it's a top-level or nested headline (starts with `*`, `**`, etc.)
   - Verify it's not in a section that should be ignored (like README)

2. **Check if file is configured as agenda file**:
   - Open app Settings → Agenda Configuration
   - Verify `agenda.org` (or the file containing your item) is listed in "Agenda Files"
   - File path should match exactly (e.g., `phone_inbox/agenda.org`)

3. **Enable debug logging and check Logcat**:
   ```bash
   adb logcat -s AgendaRepository:D
   ```
   
   Look for:
   - `"=== getAgendaItems START ==="` - Query initiated
   - `"Query returned X results from database"` - Raw query results
   - Individual result logs showing: `todoState`, `title`, `timestamp`, `timeType`
   - `"Built X agenda items after processing"` - Final agenda items
   - Individual agenda item logs showing items passed to UI

4. **Verify file sync**:
   - Pull the refresh button in the app to force re-sync
   - Check logs for: `"=== clearAndResyncAll START ==="` and `"Successfully synced <filename>"`
   - Verify the file hash changed: `"File X hash mismatch"`

5. **Check database state** (Android Studio Database Inspector):
   - Open: `View > Tool Windows > App Inspection > Database Inspector`
   - Tables to inspect:
     - `notes` - All parsed headlines
     - `org_timestamps` - All parsed timestamps
     - `note_planning` - Links notes to their timestamps
     - `file_metadata` - File sync status and hash

## SQL Query Explanation

The query is intentionally **simple** - it returns ALL notes from configured agenda files:

```sql
SELECT n.*,
       COALESCE(ts_sched.timestamp, ts_dead.timestamp, 0) as ts_timestamp,
       COALESCE(ts_sched.repeaterType, ts_dead.repeaterType) as ts_repeaterType,
       COALESCE(ts_sched.repeaterValue, ts_dead.repeaterValue) as ts_repeaterValue,
       COALESCE(ts_sched.repeaterUnit, ts_dead.repeaterUnit) as ts_repeaterUnit,
       CASE 
           WHEN ts_sched.id IS NOT NULL THEN 'SCHEDULED'
           WHEN ts_dead.id IS NOT NULL THEN 'DEADLINE'
           ELSE 'NONE'
       END as ts_type
FROM notes n
LEFT JOIN note_planning np ON n.id = np.noteId
LEFT JOIN org_timestamps ts_sched ON np.scheduledTimestampId = ts_sched.id
LEFT JOIN org_timestamps ts_dead ON np.deadlineTimestampId = ts_dead.id
WHERE n.filename IN (:agendaFiles)
ORDER BY 
    CASE WHEN n.todoState IN ('IN-PROGRESS', 'HOLD', 'WAITING') THEN 0 ELSE 1 END,
    COALESCE(ts_sched.timestamp, ts_dead.timestamp, 9999999999999) ASC,
    n.title ASC
```

**Key points**:
- `LEFT JOIN` means items without timestamps are still included
- `WHERE n.filename IN (:agendaFiles)` - only returns notes from configured agenda files
- **No timestamp filtering** - returns all notes regardless of date
- Sorting: Stateful items (IN-PROGRESS/HOLD/WAITING) first, then by timestamp, then by title

## Example: "Rest" Item Not Showing

**Item in agenda.org (line 54)**:
```org
** HOLD Rest
:PROPERTIES:
:SOURCE_ID: 56C3785A-5616-424F-8A3E-99F3C2474332
:ORIGIN_ID: [[id:56C3785A-5616-424F-8A3E-99F3C2474332]]
:CATEGORY: Personal
:SOURCE_TYPE: Area
:AREA: Personal
:ORIGIN_FILE: /Users/rrimal/Stillness/Brain/Areas/Personal.org
:LEVEL: 3
:END:
```

**Problem**: 
- Has TODO state: `HOLD` ✅
- Has SCHEDULED: ❌ No
- Has DEADLINE: ❌ No

**Why It Shows Now**:
- SQL query uses `LEFT JOIN` to include items without timestamps
- Query returns ALL notes from `agenda.org`, including "Rest"
- "Rest" appears in agenda because it's in the source file
- No filtering by date or state (agenda.org is pre-filtered on desktop)

## Debugging Checklist

When an item doesn't show:

- [ ] Verify item exists in `agenda.org` with correct syntax
- [ ] Check TODO state is valid (TODO, HOLD, WAITING, IN-PROGRESS, DONE)
- [ ] Check SCHEDULED/DEADLINE format: `SCHEDULED: <2026-03-02 Mon>`
- [ ] Force refresh in app (pull-to-refresh button)
- [ ] Check Logcat for `AgendaRepository` debug logs
- [ ] Verify SQL query matches the item's properties
- [ ] Check Room database with Database Inspector
- [ ] Verify file sync succeeded: `"Successfully synced <filename>"`
- [ ] Check for parsing errors: `"Error checking file"` or `"Failed to sync"`

## Testing the Fix

1. **Build and install the app**:
   ```bash
   ./gradlew assembleDebug
   ./gradlew installDebug
   ```

2. **Open the app** and navigate to Agenda screen

3. **Pull to refresh** to force re-sync from files

4. **Check Logcat** for debug output:
   ```bash
   adb logcat -s AgendaRepository:D | grep -E "(Rest|STATEFUL|getAgendaItems)"
   ```

5. **Expected output**:
   ```
   AgendaRepository: Query returned X results from database
   AgendaRepository: [Y] HOLD Rest - ts=0 (1970-01-01T00:00:00Z) type=STATEFUL
   AgendaRepository: Built X agenda items after processing
   AgendaRepository: [Z] Note: HOLD Rest - ts=0 timeType=SCHEDULED
   ```

6. **Verify in UI**: "Rest" item should appear in today's agenda section

## Architecture Principle

**Desktop (Emacs) does filtering → Mobile displays everything**

- **Emacs**: Creates `agenda.org` with only items you want (today's tasks, active projects, etc.)
- **Mobile App**: Displays ALL items from `agenda.org` without additional filtering
- **Syncthing**: Keeps files in sync between desktop and mobile

This design keeps the mobile app simple and makes Emacs the "command center" for agenda management.

## Future Enhancements

- [ ] Support for custom TODO keyword sequences (not just TODO | DONE)
- [ ] Filter by tags (`:@home:`, `:@work:`, etc.) - optional UI filter
- [ ] Filter by priority ([#A], [#B], [#C]) - optional UI filter
- [ ] Multiple agenda files support (separate sections per file)
- [ ] Configurable sort order (currently: stateful → timed → untimed)

## References

- ADR 003: Agenda View with Orgzly-Inspired Architecture (`docs/adr/003-agenda-view-with-orgzly-architecture.md`)
- Agenda README in source file (`agenda.org:6-28`)
- NoteDao queries (`app/src/main/kotlin/com/rrimal/notetaker/data/local/NoteDao.kt`)
- AgendaRepository logic (`app/src/main/kotlin/com/rrimal/notetaker/data/repository/AgendaRepository.kt`)

---

*Last Updated: 2026-03-02*
*Fix Version: 0.8.1 (Stateful items support)*
