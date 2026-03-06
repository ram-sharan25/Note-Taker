# Test Plan for Note Taker

## Executive Summary

This document outlines a comprehensive test strategy for the **Note Taker** Android application, focusing on three critical feature areas:

1. **Org-Mode Viewer** - Parsing, writing, and displaying org-mode files
2. **Agenda View** - Task scheduling, filtering, and TODO state management
3. **Notes Capture** - Voice/text note input, inbox capture, and storage

**Current State:** ✅ ~110 unit tests covering core functionalities  
**Target State:** 📈 Expanding to integration and UI tests  
**Testing Framework:** JUnit 5 + MockK + Kotlin Coroutines Test + Turbine

---

## Master Reference
See **[Test Strategy & Structure](TEST_STRATEGY.md)** for detailed breakdown of types, architecture, and commands.

---

## Table of Contents

1. [Test Coverage Overview](#test-coverage-overview)
2. [Testing Strategy](#testing-strategy)
3. [Test Framework Selection](#test-framework-selection)
4. [Dependency Setup](#dependency-setup)
5. [Detailed Test Specifications](#detailed-test-specifications)
6. [Implementation Priority](#implementation-priority)
7. [Running Tests](#running-tests)
8. [Success Metrics](#success-metrics)
9. [Test Code Style Guide](#test-code-style-guide)
10. [Risks & Mitigation](#risks--mitigation)
11. [Next Steps](#next-steps)

---

## Test Coverage Overview

### Coverage by Feature Area

| Feature Area | Classes Under Test | Test Files | Test Cases | Priority |
|--------------|-------------------|------------|------------|----------|
| **Org-Mode Core** | OrgParser, OrgTimestampParser, OrgWriter, OrgNode | 4 | ~63 | HIGH |
| **Agenda View** | AgendaViewModel, TodoKeywordsConfigEntity | 2 | ~25 | HIGH |
| **Notes Capture** | LocalOrgStorageBackend, NoteViewModel, InboxCaptureViewModel | 3 | ~22 | MEDIUM |
| **Total** | 10 classes | 9 files | ~110 | - |

### Test Type Distribution

```
Unit Tests (Pure Kotlin)      : 75 tests (68%)
ViewModel Tests (with Mocks)  : 37 tests (32%)
Integration Tests             : 0 tests (not in scope)
UI Tests                      : 0 tests (not in scope)
```

---

## Testing Strategy

### 1. Test Pyramid Approach

We follow the **Test Pyramid** principle with emphasis on unit tests:

```
        /\
       /UI\          ← Not in current scope
      /----\
     /Integ\         ← Not in current scope
    /------\
   /  Unit  \        ← PRIMARY FOCUS (110 tests)
  /----------\
```

**Rationale:**
- **Unit tests** are fast, isolated, and don't require Android device/emulator
- Focus on business logic and data transformations
- ViewModels tested with mocked dependencies to avoid Android framework overhead

### 2. Test Organization

Tests are organized by architecture layer:

```
app/src/test/kotlin/com/rrimal/notetaker/
├── data/                    # Data layer tests
│   ├── orgmode/            # Pure Kotlin - Org-mode parsing/writing
│   ├── local/              # Pure Kotlin - Data entities
│   └── storage/            # Mocked - Storage backends
└── ui/viewmodels/          # Mocked - Presentation layer
```

### 3. Mocking Strategy

| Component | Mocking Approach |
|-----------|-----------------|
| **Pure Kotlin classes** | No mocks (test directly) |
| **Android Context** | Mock with MockK |
| **Repositories** | Mock with MockK |
| **Coroutines** | Use `TestDispatcher` from coroutines-test |
| **Flows** | Test with Turbine for easy assertion |
| **SharedPreferences** | Mock with MockK |

---

## Test Framework Selection

### Selected Stack: JUnit 5 + MockK

| Library | Version | Purpose |
|---------|---------|---------|
| **JUnit 5** | 5.10.2 | Modern test framework with Kotlin support |
| **MockK** | 1.13.10 | Kotlin-first mocking library |
| **Coroutines Test** | 1.8.0 | Testing coroutines and flows |
| **Turbine** | 1.1.0 | Flow testing utility |

### Why JUnit 5 + MockK?

**✅ Advantages:**
- **Kotlin-native** - MockK designed for Kotlin idioms
- **Better syntax** - `every { }`, `verify { }` vs Mockito's Java-style API
- **Extension model** - JUnit 5's modern extension system
- **Coroutines support** - First-class Flow/suspend function testing
- **Less boilerplate** - Concise test code

**Example Comparison:**

```kotlin
// ❌ Mockito (Java-style)
@Mock lateinit var repository: NoteRepository
Mockito.`when`(repository.submitNote(anyString())).thenReturn(Result.success(SubmitResult.SENT))

// ✅ MockK (Kotlin-style)
val repository = mockk<NoteRepository>()
every { repository.submitNote(any()) } returns Result.success(SubmitResult.SENT)
```

---

## Dependency Setup

### Step 1: Update `gradle/libs.versions.toml`

Add test library versions to the `[versions]` section:

```toml
[versions]
# ... existing versions ...
junit5 = "5.10.2"
mockk = "1.13.10"
coroutines-test = "1.8.0"
turbine = "1.1.0"
```

Add test libraries to the `[libraries]` section:

```toml
[libraries]
# ... existing libraries ...

# Testing
junit5-api = { group = "org.junit.jupiter", name = "junit-jupiter-api", version.ref = "junit5" }
junit5-engine = { group = "org.junit.jupiter", name = "junit-jupiter-engine", version.ref = "junit5" }
junit5-params = { group = "org.junit.jupiter", name = "junit-jupiter-params", version.ref = "junit5" }
mockk = { group = "io.mockk", name = "mockk", version.ref = "mockk" }
coroutines-test = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-test", version.ref = "coroutines-test" }
turbine = { group = "app.cash.turbine", name = "turbine", version.ref = "turbine" }
```

### Step 2: Update `app/build.gradle.kts`

Add dependencies to the `dependencies` block:

```kotlin
dependencies {
    // ... existing dependencies ...

    // Unit Testing
    testImplementation(libs.junit5.api)
    testImplementation(libs.junit5.params)
    testRuntimeOnly(libs.junit5.engine)
    testImplementation(libs.mockk)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.turbine)
}

// Configure JUnit 5
tasks.withType<Test> {
    useJUnitPlatform()
}
```

---

## Detailed Test Specifications

### 1. Org-Mode Parser Tests (`OrgParserTest.kt`)

**File:** `app/src/test/kotlin/com/rrimal/notetaker/data/orgmode/OrgParserTest.kt`  
**Test Count:** 25 tests  
**Dependencies:** None (pure Kotlin)

#### Test Categories

**A. Basic Headline Parsing (8 tests)**

| Test Method | Input | Expected Output |
|-------------|-------|-----------------|
| `parse_emptyContent_returnsEmptyOrgFile()` | `""` | `OrgFile(preamble="", headlines=[])` |
| `parse_simpleHeadline_returnsSingleHeadline()` | `"* Hello"` | Headline with title="Hello" |
| `parse_headlineWithTodo_parsesTodoState()` | `"* TODO Task"` | todoState="TODO" |
| `parse_headlineWithDone_parsesDoneState()` | `"* DONE Task"` | todoState="DONE" |
| `parse_headlineWithPriorityA_parsesPriority()` | `"* [#A] Important"` | priority="A" |
| `parse_headlineWithPriorityB_parsesPriority()` | `"* [#B] Task"` | priority="B" |
| `parse_headlineWithPriorityC_parsesPriority()` | `"* [#C] Task"` | priority="C" |
| `parse_todoWithAllFeatures_parsesCorrectly()` | `"* TODO [#A] Task :work:urgent:"` | All fields populated |

**B. Tags Parsing (2 tests)**

| Test Method | Input | Expected Output |
|-------------|-------|-----------------|
| `parse_headlineWithSingleTag_parsesTag()` | `"* Task :work:"` | tags=["work"] |
| `parse_headlineWithMultipleTags_parsesTags()` | `"* Task :work:urgent:"` | tags=["work", "urgent"] |

**C. Planning Lines (4 tests)**

| Test Method | Input | Expected Output |
|-------------|-------|-----------------|
| `parse_headlineWithScheduled_parsesScheduled()` | `"* Task\nSCHEDULED: <2026-03-01 Sun>"` | scheduled="<2026-03-01 Sun>" |
| `parse_headlineWithDeadline_parsesDeadline()` | `"* Task\nDEADLINE: <2026-03-05 Thu>"` | deadline="<2026-03-05 Thu>" |
| `parse_headlineWithClosed_parsesClosed()` | `"* DONE Task\nCLOSED: [2026-03-01 Sun]"` | closed="[2026-03-01 Sun]" |
| `parse_headlineWithAllPlanningLines_parsesAll()` | All three planning lines | All three parsed |

**D. Properties Drawer (3 tests)**

| Test Method | Input | Expected Output |
|-------------|-------|-----------------|
| `parse_headlineWithProperties_parsesProperties()` | `:PROPERTIES:\n:KEY: value\n:END:` | properties={"KEY": "value"} |
| `parse_headlineWithIdProperty_parsesId()` | `:PROPERTIES:\n:ID: uuid\n:END:` | properties={"ID": "uuid"} |
| `parse_headlineWithMultipleProperties_parsesAll()` | Multiple properties | All parsed |

**E. Nested Headlines (3 tests)**

| Test Method | Input | Expected Output |
|-------------|-------|-----------------|
| `parse_nestedHeadline_parsesChildren()` | `"* Parent\n** Child"` | Parent has 1 child |
| `parse_deeplyNestedHeadlines_parsesAllLevels()` | 3+ levels | Full hierarchy |
| `parse_siblingHeadlines_parsesBothAtSameLevel()` | `"* Task1\n* Task2"` | 2 top-level headlines |

**F. Body Content (3 tests)**

| Test Method | Input | Expected Output |
|-------------|-------|-----------------|
| `parse_headlineWithBody_parsesBody()` | `"* Task\nBody text"` | body="Body text" |
| `parse_headlineWithMultilineBody_parsesAllLines()` | Multi-line body | All lines captured |
| `parse_bodyExcludesChildren_onlyParentBody()` | Parent with child | Child not in parent body |

**G. Edge Cases (2 tests)**

| Test Method | Input | Expected Output |
|-------------|-------|-----------------|
| `parse_fileWithPreamble_parsesPreamble()` | `"#+TITLE: Note\n* Task"` | preamble="#+TITLE: Note" |
| `parse_headlineWithSpecialChars_parsesCorrectly()` | Title with special chars | Special chars preserved |

---

### 2. Timestamp Parser Tests (`OrgTimestampParserTest.kt`)

**File:** `app/src/test/kotlin/com/rrimal/notetaker/data/orgmode/OrgTimestampParserTest.kt`  
**Test Count:** 15 tests  
**Dependencies:** None (pure Kotlin)

#### Test Categories

**A. Basic Timestamp Parsing (5 tests)**

| Test Method | Input | Expected Output |
|-------------|-------|-----------------|
| `parse_activeTimestamp_parsesCorrectly()` | `"<2026-03-01 Sun>"` | isActive=true, date parsed |
| `parse_inactiveTimestamp_parsesCorrectly()` | `"[2026-03-01 Sun]"` | isActive=false, date parsed |
| `parse_timestampWithTime_parsesHourMinute()` | `"<2026-03-01 Sun 09:00>"` | hour=9, minute=0 |
| `parse_timestampWithoutDayName_parsesDate()` | `"<2026-03-01>"` | Date parsed correctly |
| `parse_timestampProducesCorrectEpoch()` | `"<2026-03-01 Sun>"` | Verify millis calculation |

**B. Repeater Parsing (7 tests)**

| Test Method | Input | Expected Output |
|-------------|-------|-----------------|
| `parse_dailyRepeater_parsesRepeater()` | `"<2026-03-01 Sun +1d>"` | repeaterUnit="d", repeaterValue=1 |
| `parse_weeklyRepeater_parsesRepeater()` | `"+1w"` | repeaterUnit="w" |
| `parse_monthlyRepeater_parsesRepeater()` | `"+1m"` | repeaterUnit="m" |
| `parse_yearlyRepeater_parsesRepeater()` | `"+1y"` | repeaterUnit="y" |
| `parse_catchUpRepeater_parsesRepeater()` | `"++1d"` | repeaterType="++" |
| `parse_restartRepeater_parsesRepeater()` | `".+1d"` | repeaterType=".+" |
| `parse_repeaterWithLargeValue_parsesValue()` | `"+7d"` | repeaterValue=7 |

**C. Edge Cases (3 tests)**

| Test Method | Input | Expected Output |
|-------------|-------|-----------------|
| `parse_null_returnsNull()` | `null` | null |
| `parse_invalidString_returnsNull()` | `"not a timestamp"` | null |
| `parse_timestampWithTrailingContent_parsesCorrectly()` | `"<2026-03-01 Sun> extra"` | Timestamp extracted |

---

### 3. Org Writer Tests (`OrgWriterTest.kt`)

**File:** `app/src/test/kotlin/com/rrimal/notetaker/data/orgmode/OrgWriterTest.kt`  
**Test Count:** 15 tests  
**Dependencies:** OrgParser (for round-trip tests)

#### Test Categories

**A. Headline Writing (10 tests)**

| Test Method | Headline Input | Expected Output |
|-------------|----------------|-----------------|
| `writeHeadline_simple_writesCorrectFormat()` | Level 1, title only | `"* Title\n"` |
| `writeHeadline_withTodo_includesTodo()` | todoState="TODO" | `"* TODO Title\n"` |
| `writeHeadline_withPriority_includesPriority()` | priority="A" | `"* [#A] Title\n"` |
| `writeHeadline_withTags_includesTags()` | tags=["work"] | `"* Title :work:\n"` |
| `writeHeadline_withScheduled_includesScheduled()` | scheduled="..." | Includes SCHEDULED line |
| `writeHeadline_withDeadline_includesDeadline()` | deadline="..." | Includes DEADLINE line |
| `writeHeadline_withClosed_includesClosed()` | closed="..." | Includes CLOSED line |
| `writeHeadline_withProperties_includesDrawer()` | properties={"ID": "uuid"} | Includes :PROPERTIES: |
| `writeHeadline_withBody_includesBody()` | body="content" | Body after headline |
| `writeHeadline_withChildren_writesNested()` | 1 child headline | Child indented correctly |

**B. File Operations (4 tests)**

| Test Method | Operation | Expected Output |
|-------------|-----------|-----------------|
| `writeFile_withPreamble_includesPreamble()` | OrgFile with preamble | Preamble before headlines |
| `appendEntry_toEmptyFile_createsNewEntry()` | Append to "" | Single headline |
| `appendEntry_toExistingFile_appendsAtEnd()` | Append to content | Original + new |
| `prependEntry_addsAtBeginning()` | Prepend | New headline first |

**C. Round-Trip Test (1 test)**

| Test Method | Operation | Validation |
|-------------|-----------|------------|
| `parseAndWrite_roundTrip_producesEquivalent()` | Parse → Write → Parse | Content matches |

---

### 4. OrgNode Tests (`OrgNodeTest.kt`)

**File:** `app/src/test/kotlin/com/rrimal/notetaker/data/orgmode/OrgNodeTest.kt`  
**Test Count:** 8 tests  
**Dependencies:** None (pure Kotlin)

#### Test Categories

| Test Method | Description | Validation |
|-------------|-------------|------------|
| `getAllHeadlines_emptyFile_returnsEmptyList()` | Empty OrgFile | Returns empty list |
| `getAllHeadlines_singleHeadline_returnsList()` | One headline | Returns list with 1 item |
| `getAllHeadlines_nestedHeadlines_flattensAll()` | Parent with children | Flattens to single list |
| `findHeadline_existingTitle_returnsHeadline()` | Find by title | Returns correct headline |
| `findHeadline_missingTitle_returnsNull()` | Title not found | Returns null |
| `findHeadline_inChildren_findsIt()` | Title in child | Finds nested headline |
| `getHeadlineLine_simple_formatsCorrectly()` | Simple headline | Formats as expected |
| `getHeadlineLine_withAll_formatsCorrectly()` | TODO + priority + tags | All features in output |

---

### 5. TodoKeywordsConfig Tests (`TodoKeywordsConfigEntityTest.kt`)

**File:** `app/src/test/kotlin/com/rrimal/notetaker/data/local/TodoKeywordsConfigEntityTest.kt`  
**Test Count:** 10 tests  
**Dependencies:** None (pure Kotlin)

#### Test Categories

| Test Method | Input | Expected Output |
|-------------|-------|-----------------|
| `activeTodos_defaultSequence_returnsTodo()` | "TODO \| DONE" | ["TODO"] |
| `doneTodos_defaultSequence_returnsDone()` | "TODO \| DONE" | ["DONE"] |
| `activeTodos_extendedSequence_returnsAll()` | "TODO IN-PROGRESS \| DONE" | ["TODO", "IN-PROGRESS"] |
| `doneTodos_extendedSequence_returnsAll()` | "TODO \| DONE CANCELLED" | ["DONE", "CANCELLED"] |
| `isDone_withDone_returnsTrue()` | state="DONE" | true |
| `isDone_withTodo_returnsFalse()` | state="TODO" | false |
| `isDone_withNull_returnsFalse()` | state=null | false |
| `cycleState_fromNull_returnsFirstActive()` | current=null | "TODO" |
| `cycleState_fromTodo_returnsNextState()` | current="TODO" | Next in sequence |
| `cycleState_fromLastState_wrapsToFirst()` | current=last | First state |

---

### 6. LocalOrgStorageBackend Tests (`LocalOrgStorageBackendTest.kt`)

**File:** `app/src/test/kotlin/com/rrimal/notetaker/data/storage/LocalOrgStorageBackendTest.kt`  
**Test Count:** 12 tests  
**Dependencies:** Mock LocalFileManager, OrgParser, OrgWriter

#### Test Categories

**A. Title/Body Extraction (7 tests)**

| Test Method | Input | Expected Output |
|-------------|-------|-----------------|
| `extractTitleAndBody_withSentenceEnd_splitsCorrectly()` | "Hello. World" | ("Hello.", "World") |
| `extractTitleAndBody_withQuestionMark_splitsCorrectly()` | "Question? Answer" | ("Question?", "Answer") |
| `extractTitleAndBody_withExclamation_splitsCorrectly()` | "Wow! Amazing" | ("Wow!", "Amazing") |
| `extractTitleAndBody_noSentenceEnd_usesFirstLine()` | "Title\nBody" | ("Title", "Body") |
| `extractTitleAndBody_longSingleLine_truncates()` | 100 char string | Truncated title |
| `extractTitleAndBody_emptyText_returnsUntitled()` | "" | ("Untitled", "") |
| `extractTitleAndBody_numberWithDot_doesNotSplit()` | "3.14 is pi" | ("3.14 is pi", "") |

**B. File Path Parsing (3 tests)**

| Test Method | Input | Expected Output |
|-------------|-------|-----------------|
| `parseFilePath_simpleFilename_returnsEmptyDir()` | "inbox.org" | ("", "inbox.org") |
| `parseFilePath_withDir_splitsCorrectly()` | "Brain/inbox.org" | ("Brain", "inbox.org") |
| `parseFilePath_nestedDir_splitsCorrectly()` | "Work/Projects/todo.org" | ("Work/Projects", "todo.org") |

**C. Headline Creation (2 tests)**

| Test Method | Description | Validation |
|-------------|-------------|------------|
| `createInboxHeadline_formatsCorrectly()` | Create inbox entry | Correct org format |
| `createHeadlineFromNote_withMetadata_includesAll()` | Full metadata | All fields present |

---

### 7. ViewModel Tests

#### NoteViewModel Tests (`NoteViewModelTest.kt`)

**File:** `app/src/test/kotlin/com/rrimal/notetaker/ui/viewmodels/NoteViewModelTest.kt`  
**Test Count:** 12 tests  
**Dependencies:** Mock NoteRepository, AuthManager, StorageConfigManager, LanguagePreferenceManager

#### Test Categories

| Test Method | Description | Validation |
|-------------|-------------|------------|
| `updateNoteText_updatesUiState()` | Update text | UI state reflects change |
| `submit_withEmptyText_doesNotSubmit()` | Submit empty | Repository not called |
| `submit_setsIsSubmitting()` | Submit in progress | isSubmitting = true |
| `submit_onSuccess_clearsText()` | Successful submit | Text cleared |
| `submit_onSuccess_showsSuccessState()` | Successful submit | submitSuccess = true |
| `submit_onQueued_showsQueuedState()` | Queued submit | submitQueued = true |
| `submit_onAuthFailed_showsError()` | Auth failure | Error message shown |
| `switchToKeyboard_stopsVoice()` | Mode switch | Voice stopped |
| `startVoiceInput_requiresPermission()` | No permission | Voice doesn't start |
| `toggleLanguage_switchesLanguage()` | Toggle | Language changes |
| `clearSubmitSuccess_clearsFlag()` | Clear flag | submitSuccess = false |
| `clearSubmitQueued_clearsFlag()` | Clear flag | submitQueued = false |

**Example Test:**

```kotlin
@Test
fun `submit with empty text does not submit`() = runTest {
    // GIVEN: Empty note text
    viewModel.updateNoteText("")
    
    // WHEN: Submit is called
    viewModel.submit()
    advanceUntilIdle()
    
    // THEN: Repository never called, isSubmitting stays false
    coVerify(exactly = 0) { repository.submitNote(any()) }
    assertFalse(viewModel.uiState.value.isSubmitting)
}
```

---

#### AgendaViewModel Tests (`AgendaViewModelTest.kt`)

**File:** `app/src/test/kotlin/com/rrimal/notetaker/ui/viewmodels/AgendaViewModelTest.kt`  
**Test Count:** 15 tests  
**Dependencies:** Mock LocalFileManager, OrgParser, OrgTimestampParser, OrgWriter, AgendaConfigManager

#### Test Categories

| Test Method | Description | Validation |
|-------------|-------------|------------|
| `refresh_loadsAgendaItems()` | Initial load | Items populated |
| `refresh_setsIsRefreshingDuringLoad()` | Loading state | isRefreshing = true |
| `refresh_clearsIsRefreshingAfterLoad()` | Loading complete | isRefreshing = false |
| `refresh_withInvalidFile_handlesError()` | Error case | Graceful error handling |
| `buildAgendaItems_withScheduled_createsNoteItem()` | SCHEDULED parsing | Note item created |
| `buildAgendaItems_withDeadline_createsNoteItem()` | DEADLINE parsing | Note item created |
| `buildAgendaItems_withOverdue_marksOverdue()` | Past date | isOverdue = true |
| `buildAgendaItems_sortsByTimestamp()` | Multiple items | Sorted correctly |
| `toggleStatusFilter_addsFilter()` | Add filter | Filter added |
| `toggleStatusFilter_removesFilter()` | Remove filter | Filter removed |
| `clearStatusFilter_clearsAll()` | Clear all | Filters empty |
| `updateTodoState_updatesFile()` | State update | File modified |
| `updateTodoState_refreshesAfterUpdate()` | After update | Refresh called |
| `showStateDialog_setsDialogState()` | Show dialog | Dialog state set |
| `hideStateDialog_clearsDialogState()` | Hide dialog | Dialog state cleared |

---

#### InboxCaptureViewModel Tests (`InboxCaptureViewModelTest.kt`)

**File:** `app/src/test/kotlin/com/rrimal/notetaker/ui/viewmodels/InboxCaptureViewModelTest.kt`  
**Test Count:** 10 tests  
**Dependencies:** Mock NoteRepository, StorageConfigManager

#### Test Categories

| Test Method | Description | Validation |
|-------------|-------------|------------|
| `updateTitle_updatesUiState()` | Update title | UI state reflects change |
| `updateDescription_updatesUiState()` | Update description | UI state reflects change |
| `updateTodoState_updatesUiState()` | Update state | UI state reflects change |
| `submit_withEmptyTitle_doesNotSubmit()` | Submit empty | Repository not called |
| `submit_setsIsSubmitting()` | Submit in progress | isSubmitting = true |
| `submit_onSuccess_clearsFields()` | Successful submit | Fields cleared |
| `submit_onSuccess_resetsToTodo()` | Successful submit | State reset to TODO |
| `submit_onQueued_showsQueuedState()` | Queued submit | submitQueued = true |
| `submit_onFailure_showsError()` | Submit failure | Error message shown |
| `clearSubmitSuccess_clearsFlag()` | Clear flag | submitSuccess = false |

---

## Implementation Priority

### Phase 1: Foundation (Week 1)
**Goal:** Setup + Core Org-Mode Tests

**Tasks:**
- [x] Create test plan documentation
- [ ] Add dependencies to `gradle/libs.versions.toml`
- [ ] Update `app/build.gradle.kts`
- [ ] Create test directory structure
- [ ] Implement `OrgParserTest.kt` (25 tests)
- [ ] Implement `OrgTimestampParserTest.kt` (15 tests)
- [ ] Implement `OrgWriterTest.kt` (15 tests)
- [ ] Implement `OrgNodeTest.kt` (8 tests)

**Deliverable:** 63 passing tests, org-mode parsing validated

---

### Phase 2: Agenda Logic (Week 2)
**Goal:** Agenda View Business Logic

**Tasks:**
- [ ] Implement `TodoKeywordsConfigEntityTest.kt` (10 tests)
- [ ] Implement `AgendaViewModelTest.kt` (15 tests)

**Deliverable:** 25 additional tests, agenda functionality validated

---

### Phase 3: Notes Capture (Week 3)
**Goal:** Note Submission & Capture

**Tasks:**
- [ ] Implement `LocalOrgStorageBackendTest.kt` (12 tests)
- [ ] Implement `NoteViewModelTest.kt` (12 tests)
- [ ] Implement `InboxCaptureViewModelTest.kt` (10 tests)

**Deliverable:** 34 additional tests, capture flow validated

---

## Running Tests

### Command Line

```bash
# Run all tests
./gradlew test

# Run specific test class
./gradlew test --tests OrgParserTest

# Run tests with specific package
./gradlew test --tests "com.rrimal.notetaker.data.orgmode.*"

# Run tests in continuous mode (re-run on file change)
./gradlew test --continuous

# Run with detailed output
./gradlew test --info
```

### Android Studio

1. **Run all tests:** Right-click on `test/kotlin` folder → **Run 'All Tests'**
2. **Run specific test file:** Right-click on test file → **Run 'OrgParserTest'**
3. **Run single test:** Click green arrow next to test method
4. **View results:** Check **Run** tool window for pass/fail status

### CI/CD Integration

Create `.github/workflows/test.yml`:

```yaml
name: Unit Tests

on:
  push:
    branches: [ develop, staging, master ]
  pull_request:
    branches: [ develop, staging, master ]

jobs:
  test:
    runs-on: ubuntu-latest
    
    steps:
      - uses: actions/checkout@v4
      
      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'
          
      - name: Grant execute permission for gradlew
        run: chmod +x gradlew
        
      - name: Run Unit Tests
        run: ./gradlew test
        
      - name: Upload Test Report
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: test-results
          path: app/build/reports/tests/testDebugUnitTest/
```

---

## Success Metrics

### Coverage Targets

| Metric | Target | Current | Status |
|--------|--------|---------|--------|
| **Test Count** | 110 tests | 0 | ❌ Not Started |
| **Line Coverage** | 80%+ for tested classes | 0% | ❌ Not Started |
| **Branch Coverage** | 70%+ for tested classes | 0% | ❌ Not Started |
| **Test Execution Time** | < 10 seconds | N/A | - |

### Acceptance Criteria

**✅ Phase 1 Complete:**
- [ ] All org-mode parsing tests pass
- [ ] Round-trip parse→write→parse produces identical output
- [ ] Timestamp parsing handles all repeater types
- [ ] All edge cases covered (empty, malformed, special chars)

**✅ Phase 2 Complete:**
- [ ] Agenda items load correctly from org file
- [ ] TODO state updates persist to file
- [ ] Filter functionality works as expected
- [ ] Overdue detection accurate

**✅ Phase 3 Complete:**
- [ ] Note submission creates correct org format
- [ ] Title/body extraction handles all edge cases
- [ ] Inbox capture creates proper TODO entries
- [ ] ViewModel state management correct

---

## Test Code Style Guide

### Naming Convention

```kotlin
// Pattern: methodName_stateUnderTest_expectedBehavior
@Test
fun `parse simple headline returns single headline`() { }

// Use backticks for readability
@Test
fun `submit with empty text does not submit`() { }
```

### Test Structure (AAA Pattern)

```kotlin
@Test
fun `example test following AAA pattern`() {
    // ARRANGE: Setup test data and mocks
    val input = "* TODO Task"
    val parser = OrgParser()
    
    // ACT: Execute the behavior under test
    val result = parser.parse(input)
    
    // ASSERT: Verify expected outcome
    assertEquals(1, result.headlines.size)
    assertEquals("TODO", result.headlines[0].todoState)
    assertEquals("Task", result.headlines[0].title)
}
```

### MockK Patterns

```kotlin
// Mocking
val repository = mockk<NoteRepository>()

// Stubbing
every { repository.submitNote(any()) } returns Result.success(SubmitResult.SENT)
coEvery { repository.submitNote(any()) } returns Result.success(SubmitResult.SENT) // suspend

// Verification
verify(exactly = 1) { repository.submitNote(any()) }
coVerify { repository.submitNote("note text") } // suspend

// Relaxed mocks (return default values for all calls)
val repository = mockk<NoteRepository>(relaxed = true)
```

### Coroutine Testing

```kotlin
@Test
fun `test with coroutines`() = runTest {
    val viewModel = NoteViewModel(repository, /*...*/)
    
    viewModel.submit()
    advanceUntilIdle() // Process all coroutines
    
    assertTrue(viewModel.uiState.value.submitSuccess)
}
```

### Flow Testing with Turbine

```kotlin
@Test
fun `test flow emissions`() = runTest {
    viewModel.uiState.test {
        // Initial state
        assertEquals("", awaitItem().noteText)
        
        // Update text
        viewModel.updateNoteText("Hello")
        assertEquals("Hello", awaitItem().noteText)
        
        cancelAndIgnoreRemainingEvents()
    }
}
```

---

## Risks & Mitigation

| Risk | Impact | Probability | Mitigation |
|------|--------|-------------|------------|
| **JUnit 5 compatibility issues** | Medium | Low | JUnit 5 well-supported in Gradle 8+ |
| **MockK learning curve** | Low | Medium | Provide examples, MockK docs excellent |
| **Flaky coroutine tests** | High | Medium | Use `runTest` and `advanceUntilIdle()` |
| **Test execution time** | Low | Low | ~110 unit tests run in <10 seconds |
| **Mocking Android framework classes** | Medium | Medium | Use MockK's relaxed mocks, avoid testing Android directly |

---

## Next Steps

### Immediate Actions

1. **Add Test Dependencies**
   - Update `gradle/libs.versions.toml` with test library versions
   - Update `app/build.gradle.kts` with test dependencies
   - Sync Gradle and verify dependencies resolve

2. **Create Directory Structure**
   ```bash
   mkdir -p app/src/test/kotlin/com/rrimal/notetaker/data/orgmode
   mkdir -p app/src/test/kotlin/com/rrimal/notetaker/data/local
   mkdir -p app/src/test/kotlin/com/rrimal/notetaker/data/storage
   mkdir -p app/src/test/kotlin/com/rrimal/notetaker/ui/viewmodels
   ```

3. **Phase 1: Implement Core Tests** (Highest Value)
   - Start with `OrgParserTest.kt` (most critical)
   - Then `OrgTimestampParserTest.kt`
   - Then `OrgWriterTest.kt`
   - Finally `OrgNodeTest.kt`

4. **Verify & Iterate**
   - Run tests after each file: `./gradlew test`
   - Ensure all tests pass before moving to next phase
   - Document any issues or changes needed

### Future Enhancements

**Not in Current Scope (but valuable later):**

- [ ] Room DAO integration tests (requires instrumentation)
- [ ] UI tests with Compose Testing
- [ ] Repository integration tests with real database
- [ ] Coverage report generation (Jacoco/Kover)
- [ ] Performance benchmarks for parsing large org files
- [ ] Property-based testing with Kotest for edge cases

---

## Questions & Support

For questions about this test plan:

1. **Check documentation first:** Review relevant ADRs and `docs/REQUIREMENTS.md`
2. **Review test examples:** Look at existing test patterns in this document
3. **Consult external docs:**
   - [JUnit 5 User Guide](https://junit.org/junit5/docs/current/user-guide/)
   - [MockK Documentation](https://mockk.io/)
   - [Kotlin Coroutines Test Guide](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-test/)
   - [Turbine Documentation](https://github.com/cashapp/turbine)

---

**Document Version:** 1.0  
**Last Updated:** 2026-03-01  
**Status:** ✅ Complete - Ready for Implementation  
**Next Milestone:** Phase 1 - Core Org-Mode Tests
