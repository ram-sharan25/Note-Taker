# ADR 005: Agenda Architecture Alternatives

## Status

Proposed (Replaces ADR 003 database-centric approach)

## Context

### The Problem

ADR 003 proposed an Orgzly-inspired database-centric architecture where the Android app would:
- Parse all org files into a Room database
- Store normalized timestamps, headlines, TODO states
- Expand recurring tasks in-memory
- Provide fast SQL queries for agenda generation

**Why This Is Problematic:**

1. **Logic Duplication** - Org-mode agenda logic is complex (recurring tasks, TODO workflows, custom keywords, priorities, habits). Maintaining this in both Emacs and Android means duplicate work.

2. **Maintenance Burden** - Every org-mode spec change or user configuration change (TODO keywords, agenda views) requires updating both Emacs and Android code.

3. **Scale Issues** - User has "lots and lots of files." Syncing hundreds of org files on every app open is slow and battery-intensive.

4. **Complexity** - ~1000+ lines of code for database schema, DAOs, sync workers, recurring expansion, conflict resolution.

5. **Feature Parity Impossible** - Emacs org-mode has decades of features (custom agenda commands, block agendas, effort estimates, clocking). Building all of this in Android is unrealistic.

### User Workflow

1. Manages org files in Emacs (desktop)
2. Files sync to phone via Syncthing
3. Wants to **view** agenda on phone (quick glance)
4. Optionally: mark items complete on phone
5. Changes sync back to Emacs

**Key Insight:** User primarily needs **read access** with basic editing, not full Emacs feature parity.

---

## Options Considered

### Option A: Single Agenda Org File (RECOMMENDED)

**Concept:** Emacs aggregates scheduled/deadline items from all org files into a single `mobile-agenda.org` file. Android reads this one file, filters by date/status, allows editing TODO states.

**Architecture:**
```
Emacs (many files) → mobile-agenda.org → Syncthing → Android (filter/display/edit)
```

**How it works:**
- Emacs elisp function reads all agenda files, extracts scheduled/deadline items
- Writes to `mobile-agenda.org` in standard org format
- Runs on file save (hook) or periodically (timer/cron)
- Android uses existing OrgParser to read file
- Filters by date/status in-memory (fast for 100-200 items)
- Edits TODO states using existing OrgWriter
- Changes sync back via Syncthing

**Pros:**
- ✅ Zero logic duplication (Emacs handles complexity)
- ✅ Scales to unlimited source files (Emacs aggregates once)
- ✅ Editable (TODO state changes write back to file)
- ✅ Reuses existing code (OrgParser, OrgWriter)
- ✅ No database needed (file is small, parsing is fast)
- ✅ Standard org format (valid org-mode file)
- ✅ ~300 lines vs ~1000 lines for database approach

**Cons:**
- ❌ Requires Emacs running periodically (or cron job)
- ❌ Can become stale if desktop offline for days
- ❌ No recurring task expansion (unless Emacs pre-expands)

---

### Option B: Org-Protocol Bridge (Emacs Server)

**Concept:** Emacs runs as daemon with HTTP endpoint. Android queries via API for agenda data.

**Architecture:**
```
Android → HTTP → Emacs daemon (always running) → Live agenda data
```

**Pros:**
- ✅ Real-time accuracy (always queries live state)
- ✅ Full feature parity (Emacs handles everything)
- ✅ Can edit on phone (POST requests)

**Cons:**
- ❌ Requires Emacs always running
- ❌ Requires network/VPN when away from home
- ❌ No offline mode
- ❌ Complex setup (port forwarding, security)

**Decision:** Rejected - defeats purpose of mobile app for offline quick glances.

---

### Option C: Simplified Database (Reduced Scope)

**Concept:** Keep database approach but drastically reduce features.

**Supported:** SCHEDULED/DEADLINE only, basic TODO/DONE states, no recurring tasks, no custom keywords

**Not Supported:** Recurring tasks, custom TODO workflows, priorities, tags, habits

**Pros:**
- ✅ Simpler than full database (~200 lines vs ~1000)
- ✅ Covers 80% use case

**Cons:**
- ❌ Still duplicates logic (even simple parsing is duplication)
- ❌ Feature gap frustrates power users
- ❌ Breaks on custom configs

**Decision:** Maybe - good fallback if Option A doesn't work.

---

### Option D: No Agenda (File Browser Only)

**Concept:** Don't build agenda. Just browse org files with "show TODOs" filter.

**Decision:** Rejected - doesn't solve user's need for time-based agenda view.

---

### Option E: Hybrid (Emacs Export + Fallback)

**Concept:** Primary mode uses Emacs export (Option A). If file is stale (>24h), fall back to simplified on-device parsing (Option C).

**Pros:**
- ✅ Best of both worlds (full features when online, basic features offline)
- ✅ Graceful degradation

**Cons:**
- ❌ Two code paths to maintain
- ❌ Inconsistent UX

**Decision:** Good backup plan for offline resilience.

---

### Option F: Web-Based Viewer (PWA)

**Concept:** Emacs exports to HTML, Android displays in WebView.

**Decision:** Rejected - WebView overhead, defeats purpose of native app.

---

### Option G: Termux SSH Trigger

**Concept:** Android app triggers Termux script that SSHs to desktop and runs Emacs export.

**Pros:**
- ✅ On-demand refresh
- ✅ Complements Option A

**Cons:**
- ❌ Complex setup (SSH keys, Termux)
- ❌ Only for power users

**Decision:** Advanced feature, not primary approach.

---

## Comparison Matrix

| Option | Complexity | Maintenance | Scales to Many Files | Offline | Editing | Filters |
|--------|-----------|-------------|---------------------|---------|---------|---------|
| **A: Single Org File** | ⭐⭐ Low-Med | ⭐⭐⭐ None | ✅ Yes | ⚠️ Stale | ✅ TODO states | ✅ Date/Status |
| B: Org-Protocol | ⭐⭐⭐ High | ⭐⭐⭐ None | ✅ Yes | ❌ No | ✅ Full | ✅ Server |
| C: Simplified DB | ⭐⭐ Medium | ⭐⭐ Low | ⚠️ Slow | ✅ Yes | ⭐⭐ Basic | ⭐⭐ Basic |
| E: Hybrid A+C | ⭐⭐ Medium | ⭐⭐ Low | ✅ Yes | ✅ Degraded | ⭐⭐ Basic | ⭐⭐ Basic |
| ADR 003: Full DB | ⭐⭐⭐ High | ⭐ High | ⚠️ Sync lag | ✅ Yes | ✅ Full | ✅ SQL |

**Legend:** ⭐⭐⭐ = Best, ⭐⭐ = Good, ⭐ = Limited, ✅ = Supported, ❌ = Not supported, ⚠️ = Partial

---

## Decision

### **Adopt Option A: Single Agenda Org File**

**Rationale:**

1. **Addresses Core Problem** - User has "lots and lots of files" but only needs to view/edit agenda items. Emacs does the heavy lifting (reading many files), Android just handles one small file.

2. **Zero Logic Duplication** - All complex org-mode logic stays in Emacs. When user changes TODO keywords or adds custom agenda views in Emacs, it automatically works on Android.

3. **Minimal Code** - ~300 lines vs ~1000 lines for database approach. Reuses existing OrgParser and OrgWriter.

4. **Maintenance-Free** - Org-mode spec changes don't affect Android code. Emacs handles it.

5. **Editable** - Can change TODO states on phone with simple file write-back (two-way sync via Syncthing).

6. **Flexible Filtering** - Date-wise and status-wise filters without SQL complexity. Fast in-memory filtering for 100-200 items.

7. **Standard Format** - `mobile-agenda.org` is a valid org-mode file (can open/edit in Emacs if needed).

**Implementation Approach:**

- **Emacs:** Write elisp function to aggregate scheduled/deadline items from all agenda files into `mobile-agenda.org`. Trigger on file save (hook) or periodically (timer/systemd).
- **Android:** Read `mobile-agenda.org` with existing OrgParser, filter by date/status, display in LazyColumn, edit TODO states with OrgWriter.
- **Sync:** Syncthing handles two-way sync (Emacs writes → Android reads, Android edits → Emacs picks up changes).

### **Fallback: Option E (Hybrid)**

If user often away from desktop for >24 hours, add fallback mode:
- Try Emacs export first (full features)
- If file stale, use simplified on-device parsing (basic features)
- Show banner: "Using simplified agenda. Connect to desktop for full features."

---

## Consequences

### Positive

✅ **Minimal Code** - 70% reduction in Android code vs database approach
✅ **Zero Maintenance** - Org-mode changes don't affect Android
✅ **Scales Infinitely** - Emacs processes unlimited files, outputs small result file
✅ **Editable** - Two-way sync for TODO state changes
✅ **Fast Development** - 3-4 weeks vs 5+ weeks for database
✅ **Reuses Existing Code** - OrgParser and OrgWriter already implemented
✅ **No Database** - No schema migrations, sync workers, or Room complexity

### Negative

❌ **Requires Emacs Running** - Need desktop on periodically (mitigate with cron/systemd)
❌ **Stale Data** - If desktop offline for days, agenda outdated (show file age, add refresh button)
❌ **Emacs Dependency** - Must maintain elisp export function (but simpler than Android database)
❌ **No Recurring Expansion** - Unless Emacs pre-expands repeaters (future enhancement)

### Mitigation Strategies

**Stale Data:**
- Show file timestamp: "Last updated: 2 hours ago"
- Warning banner if >1 hour old
- Pull-to-refresh to re-read file
- Optional: Termux SSH trigger for on-demand Emacs export

**Emacs Offline:**
- Systemd timer (runs even when Emacs closed)
- Or cron job on desktop
- Or Emacs daemon + after-save hook

**Recurring Tasks:**
- Phase 1: Don't expand (show base instance only)
- Phase 2: Emacs pre-expands next 30 days of instances

**File Conflicts:**
- Check lastModified before writing TODO state change
- If changed externally, show conflict warning
- Options: "Reload" or "Force Save"

---

## Implementation Plan

**Phase 1: Read-Only (Week 1)**
- Emacs: elisp function to export `mobile-agenda.org`
- Android: Parse with OrgParser, display in LazyColumn
- Show file age

**Phase 2: Filtering (Week 2)**
- Extract available dates/statuses
- Build filter UI (date chips, status chips)
- Apply filters in-memory

**Phase 3: Editing (Week 3)**
- Make TODO state chips clickable
- Implement state cycling
- Write back with OrgWriter
- Conflict detection

**Phase 4: Polish (Week 4)**
- Pull-to-refresh
- Swipe-to-mark-done
- Search by title
- Overdue items section
- Optional: Termux SSH trigger

---

## References

### Similar Approaches
- [Beorg (iOS)](https://beorgapp.com/) - Uses iCloud sync of org files, similar philosophy
- [Organice](https://github.com/200ok-ch/organice) - Web-based, reads org files directly

### Org-Mode Documentation
- [Org Agenda](https://orgmode.org/manual/Agenda-Views.html)
- [Timestamps](https://orgmode.org/manual/Timestamps.html)

---

## Decision Date

2026-03-01

## Decision Makers

Ram Sharan Rimal (Product Owner)

## Related ADRs

- ADR 003: Database-Centric Agenda (superseded by this ADR)
- ADR 001: PAT Over OAuth - Establishes preference for simplicity
- ADR 002: Nepali Language Support - Phased approach precedent

---

**Next Steps:**

1. Review and approve this ADR
2. Create implementation guide (`docs/agenda-implementation.md`) with code examples
3. Implement Phase 1 (Emacs export + Android parser)
4. Test with user's actual org files
5. Iterate based on feedback
