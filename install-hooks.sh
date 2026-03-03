#!/bin/bash
# Install Git hooks for test-driven development

set -e

echo "================================================"
echo "  Installing Git Hooks"
echo "================================================"
echo ""

# Create .git/hooks directory if it doesn't exist
mkdir -p .git/hooks

# Copy pre-commit hook
if [ -f ".githooks/pre-commit" ]; then
    cp .githooks/pre-commit .git/hooks/pre-commit
    chmod +x .git/hooks/pre-commit
    echo "✅ Installed pre-commit hook"
    echo "   → Runs critical tests before each commit"
else
    echo "❌ .githooks/pre-commit not found"
    exit 1
fi

# Make test runner executable
if [ -f "run-tests.sh" ]; then
    chmod +x run-tests.sh
    echo "✅ Made run-tests.sh executable"
fi

echo ""
echo "================================================"
echo "  Git Hooks Installed Successfully!"
echo "================================================"
echo ""
echo "What happens now:"
echo "  • Every commit will run critical tests"
echo "  • Commit blocked if tests fail"
echo "  • Ensures only passing code is committed"
echo ""
echo "To skip tests (NOT RECOMMENDED):"
echo "  git commit --no-verify"
echo ""
echo "To test the hook:"
echo "  git commit --allow-empty -m 'Test commit'"
echo ""
