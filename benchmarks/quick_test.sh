#!/bin/bash
# Quick test to verify metrics instrumentation is working

echo "═══════════════════════════════════════════════════════════════"
echo "Quick Metrics Test - Launch JediTerm and verify metrics output"
echo "═══════════════════════════════════════════════════════════════"
echo ""

cd "$(dirname "$0")"

# Create a small test file
echo "Creating test file..."
head -n 500 /usr/share/dict/words > /tmp/quick_test.txt 2>/dev/null || \
    (echo "Test line" > /tmp/quick_test.txt; for i in {1..500}; do echo "Line $i" >> /tmp/quick_test.txt; done)

echo "✓ Test file created: /tmp/quick_test.txt (500 lines)"
echo ""
echo "Starting JediTerm..."
echo "────────────────────────────────────────────────────────────────"
echo "Instructions:"
echo "1. Terminal will launch"
echo "2. Wait 5 seconds for metrics to appear"
echo "3. Run: cat /tmp/quick_test.txt"
echo "4. Watch the REDRAW PERFORMANCE METRICS output"
echo "5. Press Ctrl+C in this terminal to stop"
echo "────────────────────────────────────────────────────────────────"
echo ""

# Launch JediTerm and capture metrics output
./gradlew :compose-ui:run --no-daemon 2>&1 | grep -E "(REDRAW|redraws|seconds|Total)"
