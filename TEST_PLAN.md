# Test Plan - Note Taker App
**Version:** 0.8.0
**Date:** 2026-03-01

---

## Testing Strategy

### Test Pyramid
```
         /\
        /UI\          10% - UI/E2E Tests (Compose Tests)
       /----\
      /Integ.\        30% - Integration Tests (Database, API, Workers)
     /--------\
    /Unit Tests\      60% - Unit Tests (ViewModels, Repositories, Parsers)
   /------------\
```

### Coverage Goals
- **Unit Tests:** 80% coverage for business logic
- **Integration Tests:** All database operations, all API calls, all WorkManager jobs
- **UI Tests:** Critical user flows only (auth, note submission, agenda view)

---

## Test Categories

### 1. Unit Tests (60%)

**Core Business Logic:**
- ✅ `OrgParserTest` - Parse org files, extract headlines, properties
- ✅ `OrgTimestampParserTest` - Parse timestamps, repeaters, date extraction
- ✅ `OrgWriterTest` - Write org files, preserve formatting
- ✅ `AgendaRepositoryTest` - Agenda list building, recurring expansion, TODO state updates
- ✅ `AuthManagerTest` - Token storage, OAuth state management
- ✅ `OAuthConfigTest` - PKCE generation, state validation
- ✅ `NoteRepositoryTest` - Note submission, queue management, conflict handling

**ViewModels:**
- ✅ `NoteViewModelTest` - Note input state, submission flow, voice/keyboard switching
- ✅ `AgendaViewModelTest` - Agenda item loading, filtering, state updates
- ✅ `AuthViewModelTest` - OAuth flow, PAT validation, repo discovery
- ✅ `SettingsViewModelTest` - Config management, disconnect flow, sync triggers
- ✅ `BrowseViewModelTest` - Directory navigation, file loading
- ✅ `InboxCaptureViewModelTest` - TODO capture, validation

**Utilities:**
- ✅ `RecurringTaskExpansionTest` - All repeater types (+, ++, .+), all units (h,d,w,m,y)
- ✅ `FileHashingTest` - SHA-256 calculation, change detection
- ✅ `LocalFileManagerTest` - File operations, path handling

### 2. Integration Tests (30%)

**Database:**
- ✅ `DatabaseMigrationTest` - All migrations (1→2, 2→3, 3→4)
- ✅ `NoteDaoTest` - CRUD operations, complex queries, foreign keys
- ✅ `PendingNoteDaoTest` - Queue operations, status updates
- ✅ `AgendaDaoTest` - Agenda queries with joins, timestamp filtering

**API:**
- ✅ `GitHubApiTest` - Mock server tests for all endpoints
- ✅ `OAuthFlowIntegrationTest` - Full OAuth flow with mock GitHub

**Workers:**
- ✅ `NoteUploadWorkerTest` - Retry logic, status updates, conflict handling
- ✅ `OrgFileSyncWorkerTest` - File sync, hash checking, database updates

**File System:**
- ✅ `LocalFileManagerIntegrationTest` - SAF operations with temp files

### 3. UI Tests (10%)

**Critical Flows:**
- ✅ `AuthFlowTest` - OAuth sign-in, PAT setup, disconnect
- ✅ `NoteSubmissionTest` - Voice input, keyboard input, submit, queue
- ✅ `AgendaFlowTest` - Load agenda, filter, TODO state update
- ✅ `InboxCaptureTest` - Title/description input, submit
- ✅ `BrowseFlowTest` - Navigate folders, view files

---

## Test Files Structure

```
app/src/test/kotlin/com/rrimal/notetaker/
├── unit/
│   ├── orgmode/
│   │   ├── OrgParserTest.kt
│   │   ├── OrgTimestampParserTest.kt
│   │   ├── OrgWriterTest.kt
│   │   └── RecurringTaskExpansionTest.kt
│   ├── repository/
│   │   ├── AgendaRepositoryTest.kt
│   │   ├── NoteRepositoryTest.kt
│   │   └── FileHashingTest.kt
│   ├── auth/
│   │   ├── AuthManagerTest.kt
│   │   ├── OAuthConfigTest.kt
│   │   └── OAuthTokenExchangerTest.kt
│   ├── viewmodel/
│   │   ├── NoteViewModelTest.kt
│   │   ├── AgendaViewModelTest.kt
│   │   ├── AuthViewModelTest.kt
│   │   ├── SettingsViewModelTest.kt
│   │   ├── BrowseViewModelTest.kt
│   │   └── InboxCaptureViewModelTest.kt
│   └── util/
│       └── LocalFileManagerTest.kt
│
├── integration/
│   ├── database/
│   │   ├── DatabaseMigrationTest.kt
│   │   ├── NoteDaoTest.kt
│   │   ├── PendingNoteDaoTest.kt
│   │   └── AgendaDaoTest.kt
│   ├── api/
│   │   ├── GitHubApiTest.kt
│   │   └── OAuthFlowIntegrationTest.kt
│   ├── worker/
│   │   ├── NoteUploadWorkerTest.kt
│   │   └── OrgFileSyncWorkerTest.kt
│   └── filesystem/
│       └── LocalFileManagerIntegrationTest.kt
│
└── ui/
    ├── AuthFlowTest.kt
    ├── NoteSubmissionTest.kt
    ├── AgendaFlowTest.kt
    ├── InboxCaptureTest.kt
    └── BrowseFlowTest.kt

app/src/androidTest/kotlin/com/rrimal/notetaker/
└── EndToEndTest.kt  # Full app flow test
```

---

## Test Data

### Sample Org Files

**agenda.org** (for testing):
```org
#+TITLE: Test Agenda
#+TODO: TODO IN-PROGRESS WAITING | DONE CANCELLED

* TODO Daily standup
SCHEDULED: <2026-03-01 Sat 09:00 ++1d>
:PROPERTIES:
:ID: 550e8400-e29b-41d4-a716-446655440000
:END:

* IN-PROGRESS Quarterly report
DEADLINE: <2026-03-15 Sat>
:PROPERTIES:
:ID: 550e8400-e29b-41d4-a716-446655440001
:END:

* TODO Weekly review
SCHEDULED: <2026-03-01 Sat .+1w>
:PROPERTIES:
:ID: 550e8400-e29b-41d4-a716-446655440002
:END:

* DONE Completed task
CLOSED: [2026-02-28 Fri 14:30]
:PROPERTIES:
:ID: 550e8400-e29b-41d4-a716-446655440003
:END:
```

**inbox.org** (for testing):
```org
#+TITLE: Inbox
#+STARTUP: overview
#+FILETAGS: :inbox:
#+PROPERTY: CREATED_FORMAT [%Y-%m-%d %a %H:%M]

* TODO Test inbox entry
:PROPERTIES:
:CREATED: [2026-03-01 Sat 10:00]
:END:
- First bullet
- Second bullet
```

---

## Conflict Resolution Tests

### Critical Test: Dual Agenda Implementation

**Test Name:** `AgendaDataSourceConsistencyTest`
**Purpose:** Ensure agenda data comes from ONE source only

```kotlin
@Test
fun `agenda screen uses AgendaRepository not AgendaViewModel`() {
    // GIVEN: AgendaScreen is displayed
    // WHEN: Agenda items load
    // THEN: Data comes from AgendaRepository.getAgendaItems()
    // AND: AgendaViewModel.buildAgendaItems() is NOT called
}

@Test
fun `recurring tasks expand correctly in agenda`() {
    // GIVEN: Org file with "++1d" repeater
    // WHEN: Agenda loads for next 7 days
    // THEN: 7 instances of task appear (one per day)
}

@Test
fun `todo state update persists to both database and file`() {
    // GIVEN: Agenda item with TODO state
    // WHEN: User changes to DONE
    // THEN: Database updated AND org file updated
    // AND: Next sync doesn't overwrite change
}
```

### Critical Test: Hardcoded Paths

**Test Name:** `AgendaConfigurationTest`
**Purpose:** Ensure user configuration is respected

```kotlin
@Test
fun `agenda respects user configured files`() {
    // GIVEN: User configures ["inbox.org", "Brain/tasks.org"]
    // WHEN: Agenda loads
    // THEN: Items from BOTH files appear
    // AND: Hardcoded "phone_inbox/agenda.org" is NOT used
}

@Test
fun `inbox capture writes to configured inbox file`() {
    // GIVEN: User configures inbox as "Brain/inbox.org"
    // WHEN: User captures TODO
    // THEN: Entry written to "Brain/inbox.org"
    // AND: NOT to hardcoded path
}
```

---

## Test Execution Commands

### Run All Tests
```bash
./gradlew test                          # Unit tests
./gradlew connectedAndroidTest          # Integration + UI tests
./gradlew testDebugUnitTest             # Debug build unit tests
./gradlew testReleaseUnitTest           # Release build unit tests
```

### Run Specific Test Suites
```bash
# Unit tests only
./gradlew testDebugUnitTest --tests "*.unit.*"

# Integration tests only
./gradlew testDebugUnitTest --tests "*.integration.*"

# Conflict resolution tests
./gradlew testDebugUnitTest --tests "*AgendaDataSourceConsistencyTest"
./gradlew testDebugUnitTest --tests "*AgendaConfigurationTest"

# Database migration tests
./gradlew testDebugUnitTest --tests "*DatabaseMigrationTest"

# ViewModel tests
./gradlew testDebugUnitTest --tests "*.viewmodel.*"
```

### Coverage Report
```bash
./gradlew testDebugUnitTestCoverage     # Generate coverage report
# Report: app/build/reports/coverage/test/debug/index.html
```

### CI/CD Integration
```bash
# In .github/workflows/test.yml
- name: Run Unit Tests
  run: ./gradlew testDebugUnitTest --stacktrace

- name: Run Integration Tests
  run: ./gradlew connectedAndroidTest --stacktrace

- name: Upload Test Results
  uses: actions/upload-artifact@v3
  with:
    name: test-results
    path: app/build/test-results/
```

---

## Test Quality Gates

### Before Merge (PR Checks)
- ✅ All unit tests pass
- ✅ No new test failures
- ✅ Coverage doesn't decrease
- ✅ Conflict resolution tests pass

### Before Release
- ✅ All unit tests pass (100%)
- ✅ All integration tests pass (100%)
- ✅ Critical UI flows pass (100%)
- ✅ No flaky tests
- ✅ Coverage >= 75%

---

## Priority Test Implementation

### Phase 1: Critical (TODAY - 4-6 hours)
1. ✅ `AgendaDataSourceConsistencyTest` - Validates agenda uses Repository
2. ✅ `AgendaConfigurationTest` - Validates config respected
3. ✅ `RecurringTaskExpansionTest` - Validates all repeater types
4. ✅ `DatabaseMigrationTest` - Validates all migrations work

### Phase 2: High Priority (This Week)
5. ✅ `NoteRepositoryTest` - Submission, queue, conflicts
6. ✅ `AgendaRepositoryTest` - Agenda building, TODO state updates
7. ✅ `OrgParserTest` - File parsing correctness
8. ✅ `OrgTimestampParserTest` - Timestamp extraction

### Phase 3: Medium Priority (Next Week)
9. ✅ All ViewModel tests
10. ✅ All DAO tests
11. ✅ Worker tests
12. ✅ UI flow tests

---

## Test Fixtures & Mocks

### Mock Data
```kotlin
object TestData {
    val sampleOrgFile = """
        * TODO Test task
        SCHEDULED: <2026-03-01 Sat 09:00 ++1d>
    """.trimIndent()

    val sampleNote = NoteEntity(
        id = 1,
        filename = "test.org",
        headlineId = "test-id",
        level = 1,
        title = "Test task",
        todoState = "TODO",
        priority = "A",
        tags = "work:urgent",
        body = "Test body",
        parentId = null,
        position = 0,
        lastModified = System.currentTimeMillis()
    )
}
```

### Mock Repositories
```kotlin
class FakeAgendaRepository : AgendaRepository {
    private val items = mutableListOf<AgendaItem>()

    override fun getAgendaItems(days: Int) = flow { emit(items) }

    fun addTestItem(item: AgendaItem) { items.add(item) }
}
```

---

**Next Step:** Implement test files in priority order (Phase 1 first)
