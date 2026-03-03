# Time Tracking Properties (PHONE_STARTED / PHONE_ENDED)

## Behavior

The app automatically adds time tracking properties to org-mode headlines when TODO states change. These properties track **multiple work sessions** using numbered properties.

### PHONE_STARTED

**Trigger**: When a task state changes **TO** `IN-PROGRESS` (from any other state)

**Properties Added**:
- First session: `PHONE_STARTED`
- Second session: `PHONE_STARTED_2`
- Third session: `PHONE_STARTED_3`
- And so on...

**Examples**:
- `TODO` → `IN-PROGRESS` ✅ Adds `PHONE_STARTED`
- `DONE` → `IN-PROGRESS` ✅ Adds `PHONE_STARTED_2` (if `PHONE_STARTED` exists)
- `HOLD` → `IN-PROGRESS` ✅ Adds `PHONE_STARTED_3` (if `PHONE_STARTED_2` exists)
- `TODO` → `DOING` ❌ No property added
- `TODO` → `STARTED` ❌ No property added

### PHONE_ENDED

**Trigger**: When a task state changes **FROM** `IN-PROGRESS` (to any other state)

**Properties Added**:
- First session: `PHONE_ENDED`
- Second session: `PHONE_ENDED_2`
- Third session: `PHONE_ENDED_3`
- And so on...

**Examples**:
- `IN-PROGRESS` → `DONE` ✅ Adds `PHONE_ENDED`
- `IN-PROGRESS` → `TODO` ✅ Adds `PHONE_ENDED_2` (if `PHONE_ENDED` exists)
- `IN-PROGRESS` → `HOLD` ✅ Adds `PHONE_ENDED_3` (if `PHONE_ENDED_2` exists)
- `DOING` → `DONE` ❌ No property added

---

## Multiple Work Sessions Tracking

The numbered properties allow you to track complete history of all work sessions:

### Example: Multiple Work Sessions

```org
* TODO Understand DNS
```

**Session 1**:
1. Change to `IN-PROGRESS`:
   ```org
   * IN-PROGRESS Understand DNS
   :PROPERTIES:
   :PHONE_STARTED: [2026-03-02 Mon 09:00]
   :END:
   ```

2. Change to `TODO` (take a break):
   ```org
   * TODO Understand DNS
   :PROPERTIES:
   :PHONE_STARTED: [2026-03-02 Mon 09:00]
   :PHONE_ENDED: [2026-03-02 Mon 09:30]
   :END:
   ```

**Session 2** (Resume work):
3. Change to `IN-PROGRESS` again:
   ```org
   * IN-PROGRESS Understand DNS
   :PROPERTIES:
   :PHONE_STARTED: [2026-03-02 Mon 09:00]
   :PHONE_ENDED: [2026-03-02 Mon 09:30]
   :PHONE_STARTED_2: [2026-03-02 Mon 14:00]
   :END:
   ```

4. Change to `DONE`:
   ```org
   * DONE Understand DNS
   :PROPERTIES:
   :PHONE_STARTED: [2026-03-02 Mon 09:00]
   :PHONE_ENDED: [2026-03-02 Mon 09:30]
   :PHONE_STARTED_2: [2026-03-02 Mon 14:00]
   :PHONE_ENDED_2: [2026-03-02 Mon 14:30]
   :END:
   ```

**Session 3** (Restart after completion):
5. Change to `IN-PROGRESS` again:
   ```org
   * IN-PROGRESS Understand DNS
   :PROPERTIES:
   :PHONE_STARTED: [2026-03-02 Mon 09:00]
   :PHONE_ENDED: [2026-03-02 Mon 09:30]
   :PHONE_STARTED_2: [2026-03-02 Mon 14:00]
   :PHONE_ENDED_2: [2026-03-02 Mon 14:30]
   :PHONE_STARTED_3: [2026-03-02 Mon 16:00]
   :END:
   ```

6. Change to `HOLD`:
   ```org
   * HOLD Understand DNS
   :PROPERTIES:
   :PHONE_STARTED: [2026-03-02 Mon 09:00]
   :PHONE_ENDED: [2026-03-02 Mon 09:30]
   :PHONE_STARTED_2: [2026-03-02 Mon 14:00]
   :PHONE_ENDED_2: [2026-03-02 Mon 14:30]
   :PHONE_STARTED_3: [2026-03-02 Mon 16:00]
   :PHONE_ENDED_3: [2026-03-02 Mon 16:15]
   :END:
   ```

**Result**: Complete history of all work sessions preserved!

---

## Time Calculation

You can calculate total time worked by pairing `PHONE_STARTED` with `PHONE_ENDED`:

| Start | End | Duration |
|-------|-----|----------|
| `PHONE_STARTED` | `PHONE_ENDED` | Session 1 |
| `PHONE_STARTED_2` | `PHONE_ENDED_2` | Session 2 |
| `PHONE_STARTED_3` | `PHONE_ENDED_3` | Session 3 |

Example:
- Session 1: 09:00 → 09:30 = 30 minutes
- Session 2: 14:00 → 14:30 = 30 minutes  
- Session 3: 16:00 → 16:15 = 15 minutes
- **Total**: 1 hour 15 minutes

---

## Org-Mode File Sync

Properties are written to the org file via the sync mechanism:

1. State change in Android app
2. Properties updated in Room database
3. On next sync (manual refresh or auto-sync), changes written to org file
4. Emacs/other org-mode tools see the updated properties

**Note**: If you edit the same headline in Emacs while the Android app is running, the next sync from the app will overwrite Emacs changes. Always sync/refresh before editing in multiple places.

---

## Integration with Toggl Track

`PHONE_STARTED` and `PHONE_ENDED` work alongside Toggl integration:

| Event | PHONE_STARTED_N | PHONE_ENDED_N | Toggl Timer |
|-------|-----------------|---------------|-------------|
| → `IN-PROGRESS` | ✅ Added | - | ▶️ Started |
| `IN-PROGRESS` → | - | ✅ Added | ⏹️ Stopped |

Both tracking mechanisms follow the same state transition rules.

**Difference**:
- **Phone properties**: Stored in org file, permanent record
- **Toggl**: Cloud-based, can sync back to Toggl web/desktop, includes project/billing info

---

## Future Enhancements

### Planned for v1.0.0

**LOGBOOK Integration**: Convert numbered properties to org-mode `CLOCK` entries:

```org
* DONE Understand DNS
:LOGBOOK:
CLOCK: [2026-03-02 Mon 09:00]--[2026-03-02 Mon 09:30] =>  0:30
CLOCK: [2026-03-02 Mon 14:00]--[2026-03-02 Mon 14:30] =>  0:30
CLOCK: [2026-03-02 Mon 16:00]--[2026-03-02 Mon 16:15] =>  0:15
:END:
```

This is the standard org-mode format for time tracking and integrates with `org-clock` commands in Emacs.

### Optional Enhancements

- **Custom property names**: Configure `PHONE_STARTED`/`PHONE_ENDED` → `WORK_STARTED`/`WORK_ENDED`
- **Auto-calculate total time**: Add `TOTAL_PHONE_TIME: 1:15` property
- **Property cleanup**: Archive old sessions after N days

---

## Troubleshooting

### Properties not appearing in org file

**Check**:
1. Did you manually refresh the agenda? (Sync happens on refresh)
2. Is the org file writable? (Check file permissions)
3. Are you using local storage mode? (GitHub mode doesn't write to local files)

**Debug**:
```bash
adb logcat -d | grep "determineTimeTrackingProperties"
```

### Missing PHONE_ENDED for a session

**Cause**: Task is still `IN-PROGRESS` (session not finished)

**Solution**: Change task state FROM `IN-PROGRESS` to add the corresponding `PHONE_ENDED`

### Numbers skipped (e.g., PHONE_STARTED, PHONE_STARTED_3)

**Cause**: Manual editing or deletion of properties in org file

**Solution**: This is normal. The app finds the next available number and uses it. Numbers don't have to be sequential.

### Too many numbered properties

**Cause**: Many work sessions on the same task

**Solution**: Consider this a feature! It shows complete work history. In the future, these will be converted to `CLOCK` entries in `:LOGBOOK:` drawer.

---

## Emacs Integration

You can process these properties in Emacs with org-mode:

```elisp
;; Calculate total time from PHONE_STARTED/PHONE_ENDED properties
(defun my/calculate-phone-time ()
  "Calculate total time from PHONE_STARTED and PHONE_ENDED properties."
  (interactive)
  (let ((props (org-entry-properties))
        (total 0)
        (counter 1))
    ;; Process PHONE_STARTED and PHONE_ENDED
    (when (and (assoc "PHONE_STARTED" props)
               (assoc "PHONE_ENDED" props))
      (let ((start (org-parse-time-string (cdr (assoc "PHONE_STARTED" props))))
            (end (org-parse-time-string (cdr (assoc "PHONE_ENDED" props)))))
        (setq total (+ total (- (org-time-string-to-seconds end)
                                (org-time-string-to-seconds start))))))
    ;; Process numbered properties
    (while (and (assoc (format "PHONE_STARTED_%d" counter) props)
                (assoc (format "PHONE_ENDED_%d" counter) props))
      (let ((start (org-parse-time-string (cdr (assoc (format "PHONE_STARTED_%d" counter) props))))
            (end (org-parse-time-string (cdr (assoc (format "PHONE_ENDED_%d" counter) props)))))
        (setq total (+ total (- (org-time-string-to-seconds end)
                                (org-time-string-to-seconds start)))))
      (setq counter (1+ counter)))
    (message "Total phone time: %s" (org-duration-from-minutes (/ total 60)))))
```

---

*Last Updated: 2026-03-02*  
*Version: 0.8.0*
