# Testing Guide - Note Taker App

## Quick Start

### Run All Critical Tests (Conflict Validation)
```bash
./run-tests.sh critical
```

### Run All Tests
```bash
./run-tests.sh all
```

### Generate Coverage Report
```bash
./run-tests.sh coverage
```

---

## Test Categories

### 1. Critical Tests (MUST PASS before deployment)

These tests validate the conflict resolutions documented in `CONFLICTS_AND_ISSUES.md`:

#### **AgendaDataSourceConsistencyTest**
- Validates agenda uses AgendaRepository (database-centric), NOT AgendaViewModel (file-based)
- Tests recurring task expansion with all repeater types
- Verifies TODO state updates affect both database AND file

**Run:**
```bash
./gradlew testDebugUnitTest --tests '*AgendaDataSourceConsistencyTest'
```

**Why Critical:**
- Prevents data loss from dual implementations
- Ensures recurring tasks work correctly
- Validates sync consistency

---

#### **AgendaConfigurationTest**
- Validates user-configured agenda files are respected
- Ensures hardcoded paths are NOT used
- Tests multi-file agenda support

**Run:**
```bash
./gradlew testDebugUnitTest --tests '*AgendaConfigurationTest'
```

**Why Critical:**
- Settings UI would be non-functional without this
- User expects configuration to work
- Prevents confusion ("Why isn't my config working?")

---

#### **RecurringTaskExpansionTest**
- Tests all org-mode repeater types: +, ++, .+
- Tests all time units: h, d, w, m, y
- Validates jump-ahead logic for efficiency

**Run:**
```bash
./gradlew testDebugUnitTest --tests '*RecurringTaskExpansionTest'
```

**Why Critical:**
- Core agenda functionality
- Org-mode compatibility depends on this
- Performance optimization (jump-ahead) tested

---

#### **DatabaseMigrationTest**
- Tests all migrations: 1→2, 2→3, 3→4, and 1→4
- Validates no data loss during upgrades
- Tests fresh install creates all tables

**Run:**
```bash
./gradlew connectedAndroidTest --tests '*DatabaseMigrationTest'
```

**Why Critical:**
- Users upgrading from old versions lose data if migrations fail
- Fresh installs crash if schema is wrong
- Room auto-migration validation

---

### 2. Unit Tests (60% of test suite)

Test individual components in isolation with mocked dependencies.

#### **Business Logic Tests:**
```bash
# Org-mode parsing
./gradlew testDebugUnitTest --tests '*OrgParserTest'
./gradlew testDebugUnitTest --tests '*OrgTimestampParserTest'
./gradlew testDebugUnitTest --tests '*OrgWriterTest'

# Repository logic
./gradlew testDebugUnitTest --tests '*AgendaRepositoryTest'
./gradlew testDebugUnitTest --tests '*NoteRepositoryTest'

# Authentication
./gradlew testDebugUnitTest --tests '*AuthManagerTest'
./gradlew testDebugUnitTest --tests '*OAuthConfigTest'
```

#### **ViewModel Tests:**
```bash
# All ViewModels
./gradlew testDebugUnitTest --tests '*viewmodel*'

# Specific ViewModels
./gradlew testDebugUnitTest --tests '*NoteViewModelTest'
./gradlew testDebugUnitTest --tests '*AgendaViewModelTest'
./gradlew testDebugUnitTest --tests '*AuthViewModelTest'
```

---

### 3. Integration Tests (30% of test suite)

Test multiple components working together. **Requires connected device or emulator.**

#### **Database Tests:**
```bash
# All DAO tests
./gradlew connectedAndroidTest --tests '*Dao*'

# Specific DAOs
./gradlew connectedAndroidTest --tests '*NoteDaoTest'
./gradlew connectedAndroidTest --tests '*PendingNoteDaoTest'
```

#### **API Tests:**
```bash
./gradlew connectedAndroidTest --tests '*GitHubApiTest'
./gradlew connectedAndroidTest --tests '*OAuthFlowIntegrationTest'
```

#### **Worker Tests:**
```bash
./gradlew connectedAndroidTest --tests '*NoteUploadWorkerTest'
./gradlew connectedAndroidTest --tests '*OrgFileSyncWorkerTest'
```

---

### 4. UI Tests (10% of test suite)

Test complete user flows with Compose UI Testing. **Requires connected device or emulator.**

```bash
# All UI tests
./gradlew connectedAndroidTest --tests '*.ui.*'

# Specific flows
./gradlew connectedAndroidTest --tests '*AuthFlowTest'
./gradlew connectedAndroidTest --tests '*NoteSubmissionTest'
./gradlew connectedAndroidTest --tests '*AgendaFlowTest'
```

---

## Test Execution Workflows

### Pre-Commit (Local Development)
```bash
# 1. Run affected tests
./gradlew testDebugUnitTest --tests '*RecurringTaskExpansionTest'

# 2. If critical changes, run critical suite
./run-tests.sh critical

# 3. Fix any failures before committing
```

### Pre-PR (Pull Request)
```bash
# 1. Run all unit tests
./run-tests.sh unit

# 2. Run critical tests
./run-tests.sh critical

# 3. Generate coverage report
./run-tests.sh coverage

# 4. Verify coverage >= 75%
```

### Pre-Release (Staging → Production)
```bash
# 1. Run EVERYTHING
./run-tests.sh all

# 2. Verify all tests pass (100%)

# 3. Run on multiple devices/API levels

# 4. Manual smoke testing of critical flows
```

---

## Writing New Tests

### Test File Location
```
Unit Tests:        app/src/test/kotlin/com/rrimal/notetaker/unit/
Integration Tests: app/src/test/kotlin/com/rrimal/notetaker/integration/
UI Tests:          app/src/test/kotlin/com/rrimal/notetaker/ui/
```

### Test Template (Unit Test)
```kotlin
package com.rrimal.notetaker.unit.repository

import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals

class MyComponentTest {

    private lateinit var dependency: DependencyType
    private lateinit var component: ComponentUnderTest

    @Before
    fun setup() {
        dependency = mockk(relaxed = true)
        component = ComponentUnderTest(dependency)
    }

    @After
    fun tearDown() {
        clearAllMocks()
    }

    @Test
    fun `descriptive test name in backticks`() = runTest {
        // GIVEN: Setup test conditions
        every { dependency.someMethod() } returns "expected value"

        // WHEN: Execute the code under test
        val result = component.doSomething()

        // THEN: Verify the outcome
        assertEquals("expected value", result)
        verify(exactly = 1) { dependency.someMethod() }
    }
}
```

### Test Template (Integration Test)
```kotlin
package com.rrimal.notetaker.integration.database

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
class MyDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var dao: MyDao

    @Before
    fun setup() {
        // Create in-memory database
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().context,
            AppDatabase::class.java
        ).allowMainThreadQueries().build()

        dao = database.myDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun testInsertAndRetrieve() {
        // Test database operations
    }
}
```

---

## Test Quality Guidelines

### Good Test Characteristics

✅ **GOOD:**
```kotlin
@Test
fun `agenda items are filtered by configured status`() = runTest {
    // Clear setup, action, assertion
    // Descriptive name explains what's being tested
    // Single responsibility
}
```

❌ **BAD:**
```kotlin
@Test
fun test1() {
    // Unclear what this tests
    // No structure
    // Magic numbers
}
```

### Test Structure (AAA Pattern)
```kotlin
@Test
fun `test name`() {
    // GIVEN: Arrange - Setup test conditions
    val input = "test"
    every { mock.method() } returns "output"

    // WHEN: Act - Execute code under test
    val result = component.doSomething(input)

    // THEN: Assert - Verify outcomes
    assertEquals("expected", result)
    verify { mock.method() }
}
```

### Naming Convention
```kotlin
// ✅ Use backticks for descriptive names
`agenda respects user configured files`

// ✅ State what's being tested and expected outcome
`recurring tasks expand correctly with database timestamps`

// ❌ Don't use generic names
testMethod1()
testAgenda()
```

---

## Debugging Failed Tests

### 1. Read the Error Message
```bash
Expected: "inbox.org"
Actual:   "phone_inbox/agenda.org"
```
→ Test caught hardcoded path being used instead of config

### 2. Check Test Output
```bash
./gradlew testDebugUnitTest --tests '*TestName' --info
```
→ Shows detailed logs

### 3. Run Single Test in Debug Mode
In Android Studio:
1. Right-click test method
2. Select "Debug 'test name'"
3. Set breakpoints in test and production code

### 4. Check Mock Behavior
```kotlin
// Print all mock interactions
verify { mockObject.method() }
confirmVerified(mockObject)  // Fails if unexpected calls
```

---

## Coverage Goals

### Overall Target: 75%

```
Unit Tests:        80% coverage (business logic)
Integration Tests: 60% coverage (database, API)
UI Tests:          Critical flows only
```

### Generate Report
```bash
./run-tests.sh coverage
# Opens: app/build/reports/coverage/test/debug/index.html
```

### Interpretation
- **Green (>80%):** Well tested
- **Yellow (60-80%):** Acceptable
- **Red (<60%):** Needs more tests

**Focus coverage on:**
- Critical business logic (AgendaRepository, NoteRepository)
- Data transformations (OrgParser, OrgWriter)
- Complex algorithms (recurring expansion, conflict detection)

**Lower priority coverage:**
- UI composables (test via UI tests instead)
- Simple data classes
- Generated code (Hilt modules, Room DAOs)

---

## Continuous Integration

### GitHub Actions Workflow

Create `.github/workflows/test.yml`:
```yaml
name: Test Suite

on: [push, pull_request]

jobs:
  unit-tests:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - uses: actions/setup-java@v3
        with:
          java-version: '17'
      - name: Run Unit Tests
        run: ./gradlew testDebugUnitTest --stacktrace
      - name: Upload Test Results
        uses: actions/upload-artifact@v3
        with:
          name: test-results
          path: app/build/test-results/

  critical-tests:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - uses: actions/setup-java@v3
        with:
          java-version: '17'
      - name: Run Critical Tests
        run: |
          ./gradlew testDebugUnitTest --tests '*AgendaDataSourceConsistencyTest'
          ./gradlew testDebugUnitTest --tests '*AgendaConfigurationTest'
          ./gradlew testDebugUnitTest --tests '*RecurringTaskExpansionTest'
```

---

## Troubleshooting

### "No tests found"
**Cause:** Test files not in correct location
**Fix:** Move to `app/src/test/kotlin/` or `app/src/androidTest/kotlin/`

### "Unresolved reference: mockk"
**Cause:** Missing test dependency
**Fix:** See `TEST_DEPENDENCIES.md`, add mockk dependency

### "Room database locked"
**Cause:** Database not closed after test
**Fix:** Add `@After fun tearDown() { database.close() }`

### "Could not find any tests for AndroidJUnit4"
**Cause:** Missing androidTest dependency
**Fix:** Add `androidTestImplementation(libs.androidx.junit)`

---

## Next Steps

1. ✅ Review TEST_PLAN.md for complete test strategy
2. ✅ Add dependencies from TEST_DEPENDENCIES.md
3. ✅ Run critical tests: `./run-tests.sh critical`
4. ✅ Fix any failures
5. ✅ Run full suite: `./run-tests.sh all`
6. ✅ Achieve 75% coverage
7. ✅ Add tests to CI/CD pipeline

---

**Last Updated:** 2026-03-01
**Test Suite Version:** 0.8.0
