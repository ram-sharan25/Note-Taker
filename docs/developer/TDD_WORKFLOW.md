# TDD Workflow - Staged Rollout Plan
**Current Stage:** 🔴 RED (Tests exist, code fails)
**Goal:** 🟢 GREEN (Tests pass, enforcement active)

---

## The Ideal TDD Cycle

```
┌─────────────────────────────────────────┐
│   1. 🔴 RED - Write failing test        │
│         ↓                               │
│   2. 🟢 GREEN - Make test pass          │
│         ↓                               │
│   3. ♻️  REFACTOR - Improve code        │
│         ↓                               │
│   4. ✅ COMMIT - Save working state     │
└─────────────────────────────────────────┘
```

**Current Reality:** You're at step 1 (RED) - Tests exist but code hasn't been fixed yet.

**You should NOT commit until:** Tests pass (GREEN)

---

## 4-Stage Rollout Plan

### Stage 0: Setup (DONE ✅)
**Status:** Tests written, infrastructure ready
**What exists:**
- ✅ 4 critical tests implemented
- ✅ Test runner scripts ready
- ✅ Documentation complete
- ✅ Enforcement configured (but NOT enabled)

**What to do:** Nothing yet - just awareness

---

### Stage 1: Fix Code (IN PROGRESS 🔴)
**Goal:** Make all tests pass (RED → GREEN)
**Duration:** 4-6 hours of focused work

**Steps:**

1. **Run tests to see current failures**
   ```bash
   ./run-tests.sh critical
   ```

2. **Fix each failure systematically**

   **Fix A: Wire AgendaScreen to Repository**
   ```kotlin
   // In AgendaScreen.kt or wherever agenda data is loaded
   // CHANGE FROM:
   val agendaItems by viewModel.agendaItems.collectAsState()

   // CHANGE TO:
   val agendaItems by repository.getAgendaItems(days).collectAsState()
   ```

   **Fix B: Replace hardcoded paths**
   ```bash
   # Find all occurrences
   grep -r "phone_inbox/agenda.org" app/src/main/kotlin/

   # Replace each with:
   agendaConfigManager.agendaFiles.first()
   ```

   **Fix C: Verify recurring expansion**
   ```bash
   # Should already work - just verify
   ./gradlew testDebugUnitTest --tests '*RecurringTaskExpansionTest'
   ```

   **Fix D: Test migrations**
   ```bash
   # Requires connected device
   ./gradlew connectedAndroidTest --tests '*DatabaseMigrationTest'
   ```

3. **Verify all tests pass**
   ```bash
   ./run-tests.sh critical
   ```

   **Expected output:**
   ```
   ✅ Agenda Data Source Consistency PASSED
   ✅ Agenda Configuration PASSED
   ✅ Recurring Task Expansion PASSED
   ✅ Database Migrations PASSED
   ```

**DON'T COMMIT YET** - Move to Stage 2 first

---

### Stage 2: Verify & Tag (READY TO COMMIT 🟢)
**Goal:** Create a clean commit with passing tests
**Duration:** 30 minutes

**Steps:**

1. **Final verification**
   ```bash
   # Clean build
   ./gradlew clean

   # Run all tests
   ./run-tests.sh all

   # Verify build works
   ./gradlew assembleDebug
   ```

2. **Review changes**
   ```bash
   git status
   git diff
   ```

3. **Stage your fixes (NOT the test infrastructure)**
   ```bash
   # Add only the bug fixes
   git add app/src/main/kotlin/com/rrimal/notetaker/ui/screens/AgendaScreen.kt
   git add app/src/main/kotlin/com/rrimal/notetaker/ui/viewmodels/AgendaViewModel.kt
   # ... etc (only production code fixes)

   # DO NOT add test files yet
   ```

4. **Commit the fixes**
   ```bash
   git commit -m "Fix agenda conflicts: use Repository, respect user config

   - Wire AgendaScreen to use AgendaRepository.getAgendaItems()
   - Replace hardcoded 'phone_inbox/agenda.org' with agendaConfigManager
   - Verified recurring task expansion works correctly
   - All critical tests now pass

   Resolves conflicts documented in CONFLICTS_AND_ISSUES.md
   Tests validate: AgendaDataSourceConsistency, AgendaConfiguration"
   ```

5. **Create a tag for this milestone**
   ```bash
   git tag -a v0.8.1-tests-passing -m "Milestone: All critical tests pass

   Code fixes:
   - Agenda uses single data source (Repository)
   - User configuration respected
   - Recurring tasks work correctly
   - Database migrations validated

   This commit represents GREEN state in TDD cycle.
   Test infrastructure added in next commit."

   git push origin v0.8.1-tests-passing
   ```

**NOW you have:** Clean working state, tagged, tests passing

---

### Stage 3: Add Test Infrastructure (SEPARATE COMMIT 📦)
**Goal:** Commit test infrastructure separately
**Duration:** 15 minutes

**Steps:**

1. **Stage test files**
   ```bash
   git add app/src/test/
   git add app/src/androidTest/
   git add TEST_PLAN.md
   git add TESTING_GUIDE.md
   git add TESTING_SUMMARY.md
   git add TEST_DEPENDENCIES.md
   git add run-tests.sh
   git add CONFLICTS_AND_ISSUES.md
   ```

2. **Commit test infrastructure**
   ```bash
   git commit -m "Add comprehensive test infrastructure

   Test files (4 critical tests):
   - AgendaDataSourceConsistencyTest (validates single data source)
   - AgendaConfigurationTest (validates config respected)
   - RecurringTaskExpansionTest (validates all repeater types)
   - DatabaseMigrationTest (validates migrations work)

   Documentation:
   - TEST_PLAN.md (test strategy)
   - TESTING_GUIDE.md (how to write/run tests)
   - TESTING_SUMMARY.md (quick reference)
   - CONFLICTS_AND_ISSUES.md (issues catalog)

   Scripts:
   - run-tests.sh (test runner with 5 modes)

   Coverage: 4 tests implemented, 29 planned (see TEST_PLAN.md)
   All tests pass: ./run-tests.sh critical"
   ```

3. **Create tag**
   ```bash
   git tag -a v0.8.1-test-suite -m "Test infrastructure complete"
   git push origin v0.8.1-test-suite
   ```

---

### Stage 4: Enable Enforcement (OPTIONAL COMMIT 🔒)
**Goal:** Turn on automatic quality gates
**Duration:** 10 minutes

**Steps:**

1. **Stage enforcement files**
   ```bash
   git add app/build.gradle.kts  # Test dependencies in builds
   git add .github/workflows/
   git add .githooks/
   git add install-hooks.sh
   git add BUILD_ENFORCEMENT.md
   ```

2. **Commit enforcement**
   ```bash
   git commit -m "Enable test-driven build enforcement

   Enforcement layers:
   1. Pre-commit hook - Runs critical tests before commit
   2. Gradle builds - Tests required before assembleDebug/bundleRelease
   3. GitHub Actions - Full CI/CD pipeline with test validation

   Configuration:
   - app/build.gradle.kts: Test dependencies for builds
   - .github/workflows/test-on-push.yml: Full CI pipeline
   - .github/workflows/pre-commit-tests.yml: PR validation
   - .githooks/pre-commit: Local commit validation

   Install: ./install-hooks.sh

   See BUILD_ENFORCEMENT.md for details."
   ```

3. **Create tag**
   ```bash
   git tag -a v0.8.1-enforcement -m "Build enforcement active"
   git push origin v0.8.1-enforcement
   ```

4. **Install hooks locally**
   ```bash
   ./install-hooks.sh
   ```

**NOW:** Every commit will run tests automatically

---

## Why This Staged Approach?

### Benefits

✅ **Clean Git History**
```
v0.8.1-tests-passing   ← Code fixes (production code only)
v0.8.1-test-suite      ← Test infrastructure
v0.8.1-enforcement     ← Quality gates
```

✅ **Easy Rollback**
```bash
# If enforcement causes issues, revert just that commit
git revert v0.8.1-enforcement

# Code fixes remain intact
```

✅ **Clear Separation**
- Commit 1: Bug fixes (what changed in production)
- Commit 2: Tests (how we verify it works)
- Commit 3: Enforcement (how we prevent regressions)

✅ **Follows TDD Philosophy**
```
1. RED → GREEN (Commit 1: fixes)
2. Document with tests (Commit 2: test suite)
3. Lock it in (Commit 3: enforcement)
```

---

## Alternative: Single Commit Approach

If you prefer one commit:

```bash
# After all tests pass
git add .
git commit -m "Complete TDD implementation: fixes + tests + enforcement

Code fixes (resolves CONFLICTS_AND_ISSUES.md):
- Wire AgendaScreen to use AgendaRepository
- Replace hardcoded paths with user config
- Verify recurring task expansion

Test infrastructure:
- 4 critical tests implemented (all passing)
- Test runner scripts and documentation
- Coverage plan for 29 additional tests

Build enforcement:
- Pre-commit hooks
- Gradle test dependencies
- GitHub Actions CI/CD pipeline

Install hooks: ./install-hooks.sh
Run tests: ./run-tests.sh critical"

git tag -a v0.8.1-tdd-complete -m "TDD cycle complete: tests pass, enforcement active"
```

---

## Recommended Workflow (My Opinion)

**Use 3-stage approach:**

1. **Stage 2 (fixes) →** Commit + Tag `v0.8.1-tests-passing`
   - This is your "it works" milestone
   - Deployable, functional code

2. **Stage 3 (tests) →** Commit + Tag `v0.8.1-test-suite`
   - Documentation of how it should work
   - Can be reviewed separately

3. **Stage 4 (enforcement) →** Commit + Tag `v0.8.1-enforcement`
   - Optional, can be enabled later
   - Easy to disable if needed

**Rationale:**
- Each commit has single responsibility
- Easy to understand what changed
- Easy to revert individual pieces
- Follows semantic versioning

---

## Current Status Checklist

**Before ANY commit:**
- [ ] All critical tests pass (`./run-tests.sh critical`)
- [ ] Build succeeds (`./gradlew assembleDebug`)
- [ ] You understand what code changed
- [ ] You've tested manually on device

**For Stage 2 (fixes commit):**
- [ ] Only production code changes staged
- [ ] Commit message explains what was fixed
- [ ] Tag created for milestone

**For Stage 3 (tests commit):**
- [ ] Test files and docs staged
- [ ] Commit message lists tests added
- [ ] Tag created

**For Stage 4 (enforcement commit):**
- [ ] Enforcement files staged
- [ ] Commit message explains enforcement layers
- [ ] Hooks installed and tested locally
- [ ] Tag created

---

## What to Do Right Now

**Option A: Fix Code First (Recommended)**
```bash
# 1. See what's failing
./run-tests.sh critical

# 2. Fix the code (4-6 hours)
# ... make changes ...

# 3. Verify tests pass
./run-tests.sh critical

# 4. Commit fixes only (Stage 2)
git add [production-files-only]
git commit -m "Fix agenda conflicts"
git tag v0.8.1-tests-passing

# 5. Later: Commit tests (Stage 3)
git add app/src/test/ TEST*.md run-tests.sh
git commit -m "Add test infrastructure"
git tag v0.8.1-test-suite

# 6. Optional: Enable enforcement (Stage 4)
git add .github/ .githooks/ BUILD_ENFORCEMENT.md
git commit -m "Enable test enforcement"
git tag v0.8.1-enforcement
```

**Option B: Commit Tests Now, Fix Later**
```bash
# 1. Commit test infrastructure (doesn't break anything)
git add app/src/test/ TEST*.md run-tests.sh CONFLICTS_AND_ISSUES.md
git commit -m "Add test suite (tests currently fail, fixes coming)"
git tag v0.8.1-test-suite-red

# 2. Later: Fix code and commit
# ... make changes ...
git commit -m "Fix agenda conflicts (tests now pass)"
git tag v0.8.1-tests-passing

# 3. Optional: Enable enforcement
# ... only when ready ...
```

---

## The Ideal TDD Answer

**For YOUR situation (tests exist, code fails):**

1. ✅ **Don't commit yet** (correct!)
2. 🔧 **Fix the code** (make tests pass)
3. ✅ **Commit fixes** (tag: v0.8.1-tests-passing)
4. 📦 **Commit tests** (tag: v0.8.1-test-suite)
5. 🔒 **Enable enforcement** (tag: v0.8.1-enforcement) - OPTIONAL

This gives you:
- Clean separation of concerns
- Revertable milestones
- Clear git history
- Optional enforcement

---

**Next Action:** Run `./run-tests.sh critical` and start fixing! 🔧
