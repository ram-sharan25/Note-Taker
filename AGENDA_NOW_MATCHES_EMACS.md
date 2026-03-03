# ✅ Android Agenda Now Matches Emacs `C-c a a`

## What Changed

I added **Priority** and **Filename** to the agenda display, so it now shows the same information as Emacs org-mode agenda.

---

## Before (Missing Priority and File Source)

```
┌─────────────────────────────────────┐
│ Mon, 1 Jan                          │
├─────────────────────────────────────┤
│ TODO Important meeting              │  ← Missing [#A] priority
│ S: 10:00  :work:                    │  ← Missing "tasks:" filename
│                                     │
│ TODO Submit report                  │  ← Missing [#B] priority
│ D:  :work:urgent:                   │  ← Missing "tasks:" filename
└─────────────────────────────────────┘
```

---

## After (Complete Org-Mode Agenda View) ✅

```
┌─────────────────────────────────────┐
│ Overdue                             │
├─────────────────────────────────────┤
│ [#A] TODO Important meeting         │  ← Priority shown!
│ tasks: S: 10:00  :work:             │  ← Filename shown!
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

---

## Complete Feature List (Matches Emacs)

| Feature | Emacs `C-c a a` | Android App | Status |
|---------|----------------|-------------|--------|
| Date headers | ✅ "Monday 1 January" | ✅ "Mon, 1 Jan" | ✅ |
| **Filename source** | ✅ "tasks:" | ✅ "tasks:" | ✅ **NEW** |
| Time-of-day | ✅ "10:00......" | ✅ "10:00" | ✅ |
| Type indicator | ✅ "Scheduled:" | ✅ "S:" / "D:" | ✅ |
| TODO state | ✅ "TODO" / "DONE" | ✅ Colored badge | ✅ |
| **Priority** | ✅ "[#A]" / "[#B]" | ✅ "[#A]" / "[#B]" | ✅ **NEW** |
| Title | ✅ "Meeting title" | ✅ "Meeting title" | ✅ |
| Tags | ✅ ":work:urgent:" | ✅ ":work:urgent:" | ✅ |
| Overdue section | ✅ Warning color | ✅ Red background | ✅ |
| Click to change state | ❌ (uses `t` key) | ✅ Tap TODO badge | ✅ |

---

## Example Org File That Shows In Agenda

**File:** `Brain/tasks.org`

```org
* TODO [#A] Important meeting
SCHEDULED: <2024-01-01 Mon 10:00>
:PROPERTIES:
:ID: abc-123
:END:
- Prepare slides
- Review budget
```

**Shows in Agenda as:**
```
[#A] TODO Important meeting
Brain/tasks: S: 10:00
```

---

## How to Configure Agenda Files

1. Open app → **Settings**
2. Scroll to **Agenda Configuration**
3. Add your org files (one per line):

```
inbox.org
Brain/tasks.org
Brain/ideas.org
work/projects.org
```

4. Optionally customize TODO keywords:
```
TODO IN-PROGRESS WAITING | DONE CANCELLED
```

5. Tap **Save Agenda Configuration**
6. Go to **Agenda screen** (from note input top bar)

---

## What Gets Shown in Agenda

The agenda shows entries with:
- `SCHEDULED: <date>` timestamps
- `DEADLINE: <date>` timestamps

From the configured files in Settings → Agenda Configuration.

**Does NOT show:**
- Regular timestamps (inactive `[2024-01-01]` or active `<2024-01-01>` without SCHEDULED/DEADLINE keywords)
- Headlines without any timestamps
- Files not listed in Agenda Configuration

---

## Color Coding (Better than Emacs!)

**Priority colors:**
- [#A] → **Red** (high priority)
- [#B] → **Blue** (medium priority)
- [#C] → **Gray** (low priority)

**TODO state badges:**
- TODO → **Red badge**
- IN-PROGRESS → **Yellow badge**
- WAITING → **Yellow badge**
- DONE → **Green badge**
- CANCELLED → **Gray badge**

**Time indicators:**
- Overdue items → **Red text**
- Normal items → **Gray text**
- Scheduled (S:) vs Deadline (D:) prefixes

---

## Interactive Features (Better than Emacs!)

1. **Tap TODO badge** → Cycles state (TODO → IN-PROGRESS → DONE → TODO)
2. **Pull to refresh** → Re-syncs from files
3. **Filter button** → Show only specific TODO states
4. **Time period toggle** → 1 day / 3 days / 7 days / 14 days
5. **Automatic sync** → Every 15 minutes in background

---

## Files Changed

1. `AgendaItem.kt` - Added `priority` and `filename` fields
2. `AgendaRepository.kt` - Include priority and filename when building items
3. `AgendaScreen.kt` - Display priority badge and filename in UI

**Changes compiled successfully!** Ready to install and test.

---

## Install and Test

```bash
./gradlew installDebug
```

Then:
1. Open app → Settings → Agenda Configuration
2. Add your org files (e.g., `inbox.org`, `Brain/tasks.org`)
3. Go to Agenda screen
4. See your scheduled/deadline items with priority and file source!

---

## Example org files to test with

**inbox.org:**
```org
* TODO [#A] Call dentist
SCHEDULED: <2024-03-01 Fri>
:PROPERTIES:
:ID: dentist-123
:END:

* DONE Buy groceries
SCHEDULED: <2024-02-28 Thu>
:PROPERTIES:
:ID: groceries-456
:END:
```

**Brain/tasks.org:**
```org
* TODO [#B] Write blog post
DEADLINE: <2024-03-05 Tue>
:PROPERTIES:
:ID: blog-789
:END:
- Research topic
- Write draft

* IN-PROGRESS [#A] Code review
SCHEDULED: <2024-03-01 Fri 14:30>
:PROPERTIES:
:ID: review-abc
:END:
```

Both will show in your agenda with file sources and priorities!
