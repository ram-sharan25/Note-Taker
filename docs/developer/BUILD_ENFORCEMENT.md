# Build Enforcement - Test-Driven Quality Gates
**Status:** ✅ ACTIVE - Build will fail if tests don't pass

---

## Overview

The build system now enforces **test-driven quality**. No code can be built or deployed unless all tests pass.

### Quality Gates

```
Code Change
    ↓
Pre-Commit Hook (Local)
    ↓
Git Push
    ↓
GitHub Actions (CI)
    ↓
Critical Tests → Unit Tests → Build → Deploy
    ↓
✅ Success or ❌ Fail
```

---

## Enforcement Levels

### Level 1: Local Development (Pre-Commit Hook)

**When:** Every `git commit`
**What Runs:** Critical tests + hardcoded path check
**Duration:** 30-60 seconds

```bash
# Install hook
./install-hooks.sh

# Now every commit will:
git commit -m "Add feature"
  → 🔍 Check for hardcoded paths
  → 🧪 Run critical tests (4 tests)
  → ✅ Allow commit OR ❌ Block commit
```

**Bypass (NOT RECOMMENDED):**
```bash
git commit --no-verify  # Skips tests
```

---

### Level 2: Gradle Build (Local & CI)

**When:** `./gradlew assembleDebug` or `./gradlew bundleRelease`
**What Runs:** All relevant unit tests
**Configuration:** `app/build.gradle.kts`

```kotlin
// Debug builds require unit tests to pass
tasks.named("assembleDebug") {
    dependsOn("testDebugUnitTest")
    dependsOn("testCritical")
}

// Release builds require unit tests to pass
tasks.named("assembleRelease") {
    dependsOn("testReleaseUnitTest")
}

// AAB builds require unit tests to pass
tasks.named("bundleRelease") {
    dependsOn("testReleaseUnitTest")
}
```

**Result:**
```bash
./gradlew assembleDebug
  → Running testCritical...
  → Running testDebugUnitTest...
  → If tests pass: BUILD SUCCESSFUL ✅
  → If tests fail: BUILD FAILED ❌
```

---

### Level 3: GitHub Actions (PR Validation)

**When:** Pull request opened/updated
**What Runs:** Quick validation (critical tests + lint)
**Workflow:** `.github/workflows/pre-commit-tests.yml`

**Checks:**
1. ✅ Critical tests pass
2. ✅ No hardcoded paths in changed files
3. ✅ Lint check passes
4. ✅ Smart testing (only relevant tests)

**Auto-comments on PR:**
```
🧪 Quick Validation Results
✅ PASSED - Tests look good!

What was tested?
- ✓ Agenda uses Repository (not ViewModel)
- ✓ User config respected
- ✓ Recurring tasks work
- ✓ No hardcoded paths
```

---

### Level 4: GitHub Actions (Full Suite)

**When:** Push to `develop`, `staging`, or `master`
**What Runs:** Complete test suite + build verification
**Workflow:** `.github/workflows/test-on-push.yml`

**Pipeline:**
```
1. Critical Tests (5 min)
   └─ Must pass for pipeline to continue

2. Unit Tests (10 min)
   └─ Must pass for build to proceed
   └─ Generates coverage report

3. Build (Debug + Release)
   └─ Only runs if tests passed
   └─ Uploads artifacts

4. Integration Tests (staging/master only)
   └─ Runs on emulator (30 min)
   └─ Android API 34

5. Test Summary
   └─ Aggregates results
   └─ Fails workflow if any tests failed
```

---

## What Gets Tested

### Critical Tests (Must Always Pass)
```bash
./gradlew testCritical
```

| Test | What It Validates | Blocks |
|------|------------------|--------|
| AgendaDataSourceConsistencyTest | Single data source | All builds |
| AgendaConfigurationTest | Config respected | All builds |
| RecurringTaskExpansionTest | Repeaters work | All builds |
| DatabaseMigrationTest | Migrations work | Integration tests |

### Unit Tests (All Implementations)
```bash
./gradlew testDebugUnitTest
```

All business logic, ViewModels, Repositories, Parsers, etc.

### Integration Tests (staging/master only)
```bash
./gradlew connectedAndroidTest
```

Database operations, API calls, Workers, UI flows.

---

## Build Failure Scenarios

### Scenario 1: Critical Test Fails Locally

```bash
$ git commit -m "Add feature"

Running pre-commit checks...
🧪 Running critical tests...

AgendaConfigurationTest > sync_uses_configured_agenda_files_not_hardcoded_path FAILED
  Expected: "inbox.org"
  Actual: "phone_inbox/agenda.org"

❌ Critical tests failed!

================================================
  Commit blocked by test failures
================================================
```

**Action:** Fix the issue, run `./run-tests.sh critical`, then commit again.

---

### Scenario 2: Build Fails Due to Tests

```bash
$ ./gradlew assembleDebug

> Task :app:testCritical
AgendaDataSourceConsistencyTest > agenda_repository_returns_items_from_database_not_files FAILED

> Task :app:assembleDebug FAILED

BUILD FAILED in 45s
```

**Action:** Tests must pass before build succeeds.

---

### Scenario 3: PR Check Fails

```
GitHub Actions: ❌ Pre-Commit Tests failed

Critical Tests: ❌ FAILED
  - AgendaConfigurationTest failed

Action Required:
  1. Run ./run-tests.sh critical locally
  2. Fix failures
  3. Push fixes
```

**Action:** PR cannot be merged until checks pass.

---

### Scenario 4: CI Pipeline Fails

```
GitHub Actions: test-on-push

✅ Critical Tests: PASSED
❌ Unit Tests: FAILED (2 failures)
⏸️  Build: SKIPPED (tests failed)
⏸️  Integration Tests: SKIPPED (tests failed)

Build cannot proceed.
```

**Action:** Fix failing unit tests before build proceeds.

---

## Overriding Enforcement (Emergency Only)

### Skip Pre-Commit Hook
```bash
# NOT RECOMMENDED - bypasses all local checks
git commit --no-verify
```

### Disable Test Dependencies (Temporary)
```kotlin
// In app/build.gradle.kts - comment out these lines:
// tasks.named("assembleDebug") {
//     dependsOn("testCritical")
// }
```

**⚠️ WARNING:** This defeats test-driven quality. Only use for emergency hotfixes.

---

## Coverage Enforcement (Optional)

Currently disabled. To enable minimum coverage threshold:

```kotlin
// In app/build.gradle.kts, uncomment:
tasks.register("checkCoverage") {
    dependsOn("testDebugUnitTestCoverage")
    doLast {
        val coverage = // parse coverage report
        if (coverage < 0.75) {
            throw GradleException("❌ Coverage below 75%")
        }
    }
}

// Then add dependency:
tasks.named("assembleDebug") {
    dependsOn("checkCoverage")
}
```

---

## Testing the Enforcement

### Test Pre-Commit Hook
```bash
# Install hook
./install-hooks.sh

# Make empty commit to test hook
git commit --allow-empty -m "Test commit"
  → Should run tests
  → Should succeed if tests pass
```

### Test Build Enforcement
```bash
# Clean build
./gradlew clean

# Try to build (will run tests first)
./gradlew assembleDebug
  → Should run testCritical
  → Should run testDebugUnitTest
  → Should build only if tests pass
```

### Test CI Pipeline
```bash
# Push to develop branch
git push origin develop
  → GitHub Actions runs
  → Critical tests run
  → Unit tests run
  → Build runs if tests pass
```

---

## Troubleshooting

### "Tests take too long in pre-commit hook"

**Solution 1:** Run fewer tests locally
```bash
# Edit .githooks/pre-commit
# Change: ./gradlew testCritical
# To: ./gradlew testDebugUnitTest --tests '*AgendaConfigurationTest'
```

**Solution 2:** Skip hook occasionally
```bash
SKIP_TESTS=true git commit -m "WIP"
```

### "Build fails but tests pass locally"

**Cause:** CI has different dependencies or config

**Solution:**
```bash
# Clean and rebuild like CI does
./gradlew clean
./gradlew testDebugUnitTest --stacktrace
./gradlew assembleDebug --stacktrace
```

### "Want to disable enforcement temporarily"

**Edit:** `app/build.gradle.kts`
```kotlin
// Comment out these lines:
// tasks.named("assembleDebug") {
//     dependsOn("testCritical")
// }
```

**Re-enable:** Uncomment the lines, sync Gradle

---

## Monitoring Test Health

### View Test Results Locally
```bash
# Run with report
./run-tests.sh unit

# Open report
open app/build/reports/tests/testDebugUnitTest/index.html
```

### View CI Test Results
1. Go to GitHub Actions tab
2. Click on workflow run
3. View "Test Summary" job
4. Download artifacts for detailed reports

### Coverage Report
```bash
# Generate coverage
./run-tests.sh coverage

# Open report
open app/build/reports/jacoco/testDebugUnitTestCoverage/html/index.html
```

---

## Benefits of Enforcement

✅ **Prevents bad code from being committed**
- Tests catch bugs before code review
- Reviewers focus on design, not bugs

✅ **Maintains consistent quality**
- Every commit passes tests
- No "broken builds" on develop branch

✅ **Fast feedback**
- Local: 30-60 seconds (pre-commit)
- CI: 5-10 minutes (PR check)
- Faster than manual testing

✅ **Confidence in deployments**
- If tests pass, code works
- Staging/production deploys are safe

✅ **Documentation through tests**
- Tests show how features should work
- New developers understand expectations

---

## Summary

**3 Enforcement Levels:**
1. 🔒 **Pre-Commit Hook** - Blocks bad commits (local)
2. 🔒 **Gradle Build** - Tests before building (local + CI)
3. 🔒 **GitHub Actions** - Full pipeline (CI/CD)

**Result:**
> **Only code that passes tests can be built or deployed.**

This ensures the highest quality codebase where conflicts are caught immediately, features work as expected, and users never see broken functionality.

---

**Status:** ✅ ACTIVE
**Last Updated:** 2026-03-01
**Configuration:** See `app/build.gradle.kts`, `.github/workflows/`, `.githooks/`
