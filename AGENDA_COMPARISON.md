# Agenda View Comparison: Emacs vs. Android App

## What Emacs `C-c a a` Shows

When you press `C-c a a` in Emacs org-mode, the daily/weekly agenda shows:

```
Week-agenda (W01):
Monday     1 January 2024
  tasks:      10:00...... Scheduled:  TODO [#A] Important meeting      :work:
  inbox:      .................. TODO Call dentist                     :personal:
  tasks:      Deadline:   TODO [#B] Submit report                      :work:urgent:
Tuesday    2 January 2024
  tasks:      Scheduled:  IN-PROGRESS Review code                      :dev:
  Brain/ideas: 14:30...... Scheduled:  TODO Brainstorm session          :creative:
```

### Key Elements:
1. **Date headers** - "Monday 1 January 2024"
2. **File name** - "tasks:", "inbox:", "Brain/ideas:"
3. **Time** - "10:00......" or "14:30......" (if timestamp has time)
4. **Type** - "Scheduled:" or "Deadline:"
5. **TODO state** - "TODO", "IN-PROGRESS", "DONE"
6. **Priority** - "[#A]", "[#B]", "[#C]"
7. **Title** - "Important meeting"
8. **Tags** - ":work:", ":personal:", ":work:urgent:"
9. **Overdue items** - Shown at top with warning colors

---

## What Android App Currently Shows

**✅ Already Implemented:**
- ✅ Date headers ("Mon, 1 Jan")
- ✅ Time ("10:00" if present)
- ✅ Type indicator ("S:" for Scheduled, "D:" for Deadline)
- ✅ TODO state badge (colored chip: TODO=red, DONE=green, IN-PROGRESS=yellow)
- ✅ Title
- ✅ Tags (":work:urgent:")
- ✅ Overdue section (shown at top with red background)

**❌ Missing:**
- ❌ **File name/source** - "tasks:", "Brain/ideas:" (we have it in database but not showing)
- ❌ **Priority** - "[#A]", "[#B]", "[#C]" (we have it in database but not showing)

---

## Database Fields Available

From `NoteEntity.kt` we already have these fields stored:
```kotlin
val filename: String,       // "Brain/tasks.org"  ← NOT SHOWN
val priority: String?,      // "A", "B", "C"       ← NOT SHOWN
val tags: String,           // "work:urgent"       ← SHOWN ✅
val todoState: String?,     // "TODO", "DONE"      ← SHOWN ✅
val title: String,          // headline text       ← SHOWN ✅
```

---

## What Needs to Be Added

### 1. Add Priority to AgendaItem.Note

**File:** `app/src/main/kotlin/com/rrimal/notetaker/ui/screens/agenda/AgendaItem.kt`
```kotlin
data class Note(
    // ... existing fields ...
    val priority: String?,           // ADD THIS
    val filename: String,            // ADD THIS
)
```

### 2. Include Priority in buildAgendaList

**File:** `AgendaRepository.kt` line 114
```kotlin
val item = AgendaItem.Note(
    // ... existing fields ...
    priority = result.note.priority,     // ADD THIS
    filename = result.note.filename,     // ADD THIS
)
```

### 3. Display Priority in UI

**File:** `AgendaScreen.kt` - Update `AgendaNoteItem` composable
```kotlin
Row(verticalAlignment = Alignment.CenterVertically) {
    // Show priority badge [#A], [#B], [#C]
    if (item.priority != null) {
        Text(
            text = "[#${item.priority}]",
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            color = when (item.priority) {
                "A" -> MaterialTheme.colorScheme.error
                "B" -> MaterialTheme.colorScheme.primary
                "C" -> MaterialTheme.colorScheme.secondary
                else -> MaterialTheme.colorScheme.onSurface
            }
        )
        Spacer(Modifier.width(4.dp))
    }

    // Show TODO state badge
    if (item.todoState != null) {
        // ... existing code ...
    }

    // Show title
    Text(item.title, ...)
}

// Show filename source below title
Row(modifier = Modifier.padding(top = 2.dp)) {
    Text(
        text = item.filename.removeSuffix(".org") + ":",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.outline,
        fontFamily = FontFamily.Monospace
    )
    Spacer(Modifier.width(8.dp))
    Text(
        text = "$timeLabel: ${item.formattedTime ?: ""}",
        // ... existing code ...
    )
}
```

---

## Expected Result After Changes

```
┌─────────────────────────────────────┐
│ Overdue                             │
├─────────────────────────────────────┤
│ [#A] TODO Important meeting         │
│ tasks: S: 10:00  :work:             │
├─────────────────────────────────────┤
│ Mon, 1 Jan                          │
├─────────────────────────────────────┤
│ TODO Call dentist                   │
│ inbox: S:  :personal:               │
│                                     │
│ [#B] TODO Submit report             │
│ tasks: D:  :work:urgent:            │
├─────────────────────────────────────┤
│ Tue, 2 Jan                          │
├─────────────────────────────────────┤
│ IN-PROGRESS Review code             │
│ tasks: S:  :dev:                    │
│                                     │
│ TODO Brainstorm session             │
│ Brain/ideas: S: 14:30  :creative:   │
└─────────────────────────────────────┘
```

This matches what you see in Emacs org-agenda!

---

## Configuration Files

The agenda pulls from files configured in Settings:

**Current default (from `AgendaConfigManager.kt`):**
```kotlin
const val DEFAULT_AGENDA_FILES = "inbox.org"
const val DEFAULT_TODO_KEYWORDS = "TODO IN-PROGRESS WAITING | DONE CANCELLED"
```

**To add more files:** Go to Settings → Agenda Configuration and add (one per line):
```
inbox.org
Brain/tasks.org
Brain/ideas.org
work/projects.org
```

These should be paths relative to your local folder root.
