# Test Dependencies Configuration

## Required Test Dependencies

Add these to `app/build.gradle.kts` in the `dependencies` block:

```kotlin
dependencies {
    // ... existing dependencies ...

    // Testing - Core
    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)

    // Testing - MockK (mocking framework)
    testImplementation("io.mockk:mockk:1.13.8")
    testImplementation("io.mockk:mockk-android:1.13.8")

    // Testing - Room
    testImplementation(libs.androidx.room.testing)

    // Testing - Architecture Components
    testImplementation(libs.androidx.arch.core.testing)

    // Android Instrumentation Tests
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation("io.mockk:mockk-android:1.13.8")

    // Compose Testing
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
```

## Version Catalog (`gradle/libs.versions.toml`)

Add to `[versions]`:
```toml
mockk = "1.13.8"
```

Add to `[libraries]`:
```toml
mockk = { module = "io.mockk:mockk", version.ref = "mockk" }
mockk-android = { module = "io.mockk:mockk-android", version.ref = "mockk" }
```

## Test Configuration in `build.gradle.kts`

Ensure testOptions is configured:

```kotlin
android {
    // ... other config ...

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }

        // For instrumentation tests
        execution = "ANDROIDX_TEST_ORCHESTRATOR"
    }
}
```

## Additional Test Tools

### 1. Coverage Plugin (JaCoCo)

Add to `plugins`:
```kotlin
plugins {
    id("jacoco")
}
```

Add task for coverage:
```kotlin
tasks.register<JacocoReport>("testDebugUnitTestCoverage") {
    dependsOn("testDebugUnitTest")

    reports {
        xml.required.set(true)
        html.required.set(true)
    }

    val fileFilter = listOf(
        "**/R.class",
        "**/R$*.class",
        "**/BuildConfig.*",
        "**/Manifest*.*",
        "**/*Test*.*",
        "android/**/*.*"
    )

    val debugTree = fileTree("${project.buildDir}/tmp/kotlin-classes/debug") {
        exclude(fileFilter)
    }

    val mainSrc = "${project.projectDir}/src/main/kotlin"

    sourceDirectories.setFrom(files(mainSrc))
    classDirectories.setFrom(files(debugTree))
    executionData.setFrom(fileTree(project.buildDir) {
        include("jacoco/testDebugUnitTest.exec")
    })
}
```

### 2. Test Fixtures

For shared test data, add to `app/build.gradle.kts`:

```kotlin
android {
    // ... other config ...

    sourceSets {
        getByName("test") {
            java.srcDirs("src/testFixtures/kotlin")
        }
        getByName("androidTest") {
            java.srcDirs("src/testFixtures/kotlin")
        }
    }
}
```

Create directory:
```bash
mkdir -p app/src/testFixtures/kotlin/com/rrimal/notetaker
```

## Verification Commands

After adding dependencies:

```bash
# Sync Gradle
./gradlew --refresh-dependencies

# Verify test configuration
./gradlew tasks --group=verification

# Run a simple test to verify setup
./gradlew testDebugUnitTest --tests "*RecurringTaskExpansionTest.non_recurring_timestamp_returns_single_instance"
```

## Expected Output

You should see these test tasks available:
```
test - Runs all unit tests
testDebugUnitTest - Runs unit tests for debug build
testReleaseUnitTest - Runs unit tests for release build
connectedAndroidTest - Runs instrumentation tests on connected devices
testDebugUnitTestCoverage - Generates coverage report
```

## Troubleshooting

### Issue: "Could not find io.mockk:mockk:1.13.8"

**Solution:** Add Maven Central to repositories in `settings.gradle.kts`:
```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()  // Ensure this is present
    }
}
```

### Issue: "No tests found for given includes"

**Solution:** Verify test files are in correct location:
```bash
ls -R app/src/test/kotlin/
ls -R app/src/androidTest/kotlin/
```

### Issue: "Unresolved reference: mockk"

**Solution:** Sync Gradle and invalidate caches:
```bash
./gradlew --refresh-dependencies
# In Android Studio: File > Invalidate Caches / Restart
```

### Issue: Room migration tests fail

**Solution:** Add Room testing artifact:
```kotlin
androidTestImplementation("androidx.room:room-testing:2.8.4")
```

## Next Steps

1. Add dependencies to `build.gradle.kts`
2. Sync Gradle: `./gradlew --refresh-dependencies`
3. Run critical tests: `./run-tests.sh critical`
4. Fix any failures before proceeding
5. Run full test suite: `./run-tests.sh all`
