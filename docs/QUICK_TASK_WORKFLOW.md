# Quick Task Workflow — Implementation Documentation

## Goal

Enable instant capture and completion of "Quick Tasks" (single-action TODOs) in `quick.org`.  
Quick Tasks only ever live in the local org-mode file—no JSON or database acts as a source of truth for these tasks.

---

## Workflow Steps

### 1. Quick Task Capture UI
- **Entry point:** FloatingActionButton (FAB) on AgendaScreen—always visible.
- **FAB icon:** Use `Icons.Default.Add` or another action icon.
- **On FAB click:**  
  - Set `showQuickTaskDialog = true` (local state).
  - Display `QuickTaskDialog` modal.

#### QuickTaskDialog
- **Fields:**  
  - Title (required)
  - Description (optional)
  - Toggl Project (dropdown, required if Toggl enabled)
  - Enable Pomodoro (checkbox, optional)
- **Buttons:**  
  - "Start & Log" (enabled if Title and Project selected)
  - "Cancel"

- **On submit:**  
  - Calls `AgendaViewModel.saveQuickTask(title, description, projectName, pomodoroEnabled)` (suspend).

---

### 2. ViewModel — Quick Task Creation

#### AgendaViewModel
- **Method:** `saveQuickTask(title, description, projectName, pomodoroEnabled): Result<Unit>`
- **Logic:**
  1. Get current time for CREATED property.
  2. Find Toggl project ID (if selected).
  3. Start Toggl timer for the task (if enabled).
  4. Compose org headline (with properties/tags as needed).
  5. Write new headline to `quick.org` using backend (`LocalOrgStorageBackend`).
  6. If Pomodoro enabled, start Pomodoro timer.
  7. Return Result (success/failure) for snackbar/UI feedback.

- **Headline Format Example:**
  ```org
  * TODO <Title>
    :PROPERTIES:
    :CREATED: [2026-03-03 Tue 09:01]
    :TOGGL_PROJECT: <ProjectName>
    :TOGGL_ID: <TimerId>
    :POMODO_ENABLED: t
    :END:
    <Description>
  ```

---

### 3. Quick Task Completion

- **Marking as DONE:**  
  - UI calls `AgendaViewModel.updateTodoState(noteId, "DONE")`.
- **ViewModel logic:**
  - Checks if note’s `filename` == `PhoneInboxStructure.QUICK_FILE_PATH`.
  - If YES and newState is "DONE":
    - Extract CREATED property.
    - Get current time for ENDED property.
    - Update headline in quick.org by matching CREATED property and replacing:
      - `TODO` with `DONE`
      - Add ENDED property (`:ENDED:`)
      - Add CLOSED timestamp (`CLOSED: [2026-03-03 Tue 09:22]`)
      - Optionally update headline body (description, etc).

- **Error handling:**  
  - If org file write/update fails, show failure message in UI snackbar.
  - If CREATED property missing, show error in snackbar.
  - All file I/O gracefully handled—no crash.

- **org-mode state change format:**  
  ```org
  * DONE <Title>
    :PROPERTIES:
    :CREATED: [2026-03-03 Tue 09:01]
    :ENDED: [2026-03-03 Tue 09:22]
    :TOGGL_PROJECT: <ProjectName>
    :TOGGL_ID: <TimerId>
    :POMODO_ENABLED: t
    :END:
    <Description>
    CLOSED: [2026-03-03 Tue 09:22]
  ```

---

### 4. Edge Cases & Validations

- Multiple simultaneous Quick Tasks handled via unique CREATED property match.
- If quick.org file missing/corrupt, show "Quick Task org file missing" error in snackbar.
- All unhandled exceptions in file manipulation must translate to user-readable error in UI.

---

## File References

- **AgendaScreen**  
  - Scaffold with FAB (`Icons.Default.Add`) → QuickTaskDialog
  - Dialog triggers `viewModel.saveQuickTask`

- **QuickTaskDialog**  
  - Modal UI for quick entry (title, description, project, Pomodoro)

- **AgendaViewModel**  
  - `saveQuickTask()` for org file headline creation
  - `updateTodoState()` for marking quick tasks as DONE and updating org file

- **LocalOrgStorageBackend**  
  - `appendToQuickFile()`, `markQuickTaskDone()` for org file I/O

- **PhoneInboxStructure.QUICK_FILE_PATH**  
  - Path to `quick.org` used as the sole source of truth

---

## Upgrades/Expansion (Future)

- Consider validation UI for missing fields (title/project) in dialog.
- Add undo option for quick task completion (if workflow expansion requires).
- Upgrade backend to provide detailed error codes/messages for richer UI feedback.
- Integration with notification framework for Pomodoro/timer events.

---

## Testing Checklist

- [ ] FAB shows up on AgendaScreen
- [ ] FAB opens QuickTaskDialog
- [ ] Dialog validates required fields; disables submit when empty
- [ ] Successful Quick Task submission writes correct headline to org file
- [ ] Marking Quick Task as DONE updates org file state/timestamps
- [ ] All file errors → visible snackbars, no app crash
- [ ] Multiple quick tasks: unique CREATED property matches each
- [ ] Pomodoro/toggl options work if enabled

---

**Reference this file prior to implementation and testing for Quick Task support.**
