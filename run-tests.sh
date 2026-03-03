#!/bin/bash
# Test Runner Script for Note Taker App
# Usage: ./run-tests.sh [all|unit|integration|critical|coverage]

set -e  # Exit on error

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo "================================================"
echo "  Note Taker Test Suite"
echo "  Version: 0.8.0"
echo "================================================"
echo ""

# Function to run tests and check result
run_test() {
    local test_name=$1
    local test_command=$2

    echo -e "${YELLOW}Running: $test_name${NC}"
    if eval "$test_command"; then
        echo -e "${GREEN}✓ $test_name PASSED${NC}"
        return 0
    else
        echo -e "${RED}✗ $test_name FAILED${NC}"
        return 1
    fi
}

# Parse command line argument
TEST_TYPE=${1:-all}

case $TEST_TYPE in
    "critical")
        echo "Running CRITICAL tests only (conflict resolution)"
        echo "----------------------------------------------"
        run_test "Agenda Data Source Consistency" \
            "./gradlew testDebugUnitTest --tests '*AgendaDataSourceConsistencyTest'"
        run_test "Agenda Configuration" \
            "./gradlew testDebugUnitTest --tests '*AgendaConfigurationTest'"
        run_test "Recurring Task Expansion" \
            "./gradlew testDebugUnitTest --tests '*RecurringTaskExpansionTest'"
        run_test "Database Migrations" \
            "./gradlew connectedAndroidTest --tests '*DatabaseMigrationTest'"
        ;;

    "unit")
        echo "Running ALL unit tests"
        echo "----------------------------------------------"
        run_test "Unit Tests" \
            "./gradlew testDebugUnitTest"
        ;;

    "integration")
        echo "Running ALL integration tests (requires connected device)"
        echo "----------------------------------------------"

        # Check if device is connected
        if ! adb devices | grep -q "device$"; then
            echo -e "${RED}Error: No Android device connected${NC}"
            echo "Please connect a device or start an emulator"
            exit 1
        fi

        run_test "Integration Tests" \
            "./gradlew connectedAndroidTest"
        ;;

    "coverage")
        echo "Running tests with coverage report"
        echo "----------------------------------------------"
        run_test "Unit Tests with Coverage" \
            "./gradlew testDebugUnitTestCoverage"

        COVERAGE_REPORT="app/build/reports/coverage/test/debug/index.html"
        if [ -f "$COVERAGE_REPORT" ]; then
            echo ""
            echo -e "${GREEN}Coverage report generated:${NC}"
            echo "  file://$PWD/$COVERAGE_REPORT"
            echo ""

            # Try to open in browser (macOS)
            if command -v open &> /dev/null; then
                echo "Opening coverage report in browser..."
                open "$COVERAGE_REPORT"
            fi
        fi
        ;;

    "all")
        echo "Running ALL tests (unit + integration)"
        echo "----------------------------------------------"

        # Run unit tests first
        echo ""
        echo "=== Phase 1: Unit Tests ==="
        run_test "Unit Tests" \
            "./gradlew testDebugUnitTest"

        # Check for connected device
        echo ""
        echo "=== Phase 2: Integration Tests ==="
        if adb devices | grep -q "device$"; then
            run_test "Integration Tests" \
                "./gradlew connectedAndroidTest"
        else
            echo -e "${YELLOW}⚠ Skipping integration tests (no device connected)${NC}"
            echo "  To run integration tests: ./run-tests.sh integration"
        fi
        ;;

    *)
        echo -e "${RED}Error: Invalid test type '$TEST_TYPE'${NC}"
        echo ""
        echo "Usage: ./run-tests.sh [all|unit|integration|critical|coverage]"
        echo ""
        echo "Test Types:"
        echo "  all         - Run all tests (unit + integration)"
        echo "  unit        - Run unit tests only"
        echo "  integration - Run integration tests (requires device)"
        echo "  critical    - Run critical conflict resolution tests"
        echo "  coverage    - Generate coverage report"
        echo ""
        exit 1
        ;;
esac

echo ""
echo "================================================"
echo -e "${GREEN}Test Suite Complete!${NC}"
echo "================================================"
