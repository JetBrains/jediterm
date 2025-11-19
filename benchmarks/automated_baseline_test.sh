#!/bin/bash
# Automated baseline performance test
# Captures actual metrics from terminal output

set -e

echo "╔═══════════════════════════════════════════════════════════════╗"
echo "║  AUTOMATED BASELINE PERFORMANCE TEST                          ║"
echo "╚═══════════════════════════════════════════════════════════════╝"
echo ""

cd "$(dirname "$0")"

# Create test files with known sizes
echo "Creating test files..."

# Small: 500 lines
head -n 500 /usr/share/dict/words > /tmp/test_500.txt 2>/dev/null || \
    (for i in {1..500}; do echo "Line $i with some text content"; done > /tmp/test_500.txt)

# Medium: 2000 lines
head -n 2000 /usr/share/dict/words > /tmp/test_2000.txt 2>/dev/null || \
    (for i in {1..2000}; do echo "Line $i with some text content"; done > /tmp/test_2000.txt)

# Large: 10000 lines
head -n 10000 /usr/share/dict/words > /tmp/test_10000.txt 2>/dev/null || \
    (for i in {1..10000}; do echo "Line $i with some text content"; done > /tmp/test_10000.txt)

echo "✓ Test files created:"
echo "  - /tmp/test_500.txt (500 lines)"
echo "  - /tmp/test_2000.txt (2,000 lines)"
echo "  - /tmp/test_10000.txt (10,000 lines)"
echo ""

# Results file
RESULTS_FILE="/tmp/baseline_results_$(date +%Y%m%d_%H%M%S).txt"

# Function to extract metrics from output
extract_metrics() {
    local test_name="$1"
    echo "═══════════════════════════════════════════════════════════════" >> "$RESULTS_FILE"
    echo "TEST: $test_name" >> "$RESULTS_FILE"
    echo "═══════════════════════════════════════════════════════════════" >> "$RESULTS_FILE"
    echo "" >> "$RESULTS_FILE"
}

echo "╔═══════════════════════════════════════════════════════════════╗"
echo "║  INSTRUCTIONS                                                 ║"
echo "╚═══════════════════════════════════════════════════════════════╝"
echo ""
echo "This script will guide you through 4 quick tests."
echo "For each test:"
echo "  1. Launch JediTerm: ./gradlew :compose-ui:run --no-daemon"
echo "  2. Run the specified command"
echo "  3. Copy the FINAL metrics output"
echo "  4. Close terminal and paste metrics when prompted"
echo ""
echo "Results will be saved to: $RESULTS_FILE"
echo ""

# Test 1: Idle baseline
echo "═══════════════════════════════════════════════════════════════"
echo "TEST 1: Idle Terminal (30 seconds)"
echo "═══════════════════════════════════════════════════════════════"
echo ""
echo "1. Launch terminal: ./gradlew :compose-ui:run --no-daemon"
echo "2. Wait 30 seconds (do nothing)"
echo "3. Copy the last REDRAW PERFORMANCE METRICS output"
echo "4. Close terminal"
echo ""
echo "Press ENTER when you've copied the metrics..."
read -r

extract_metrics "Test 1: Idle (30 seconds)"
echo "Expected: 2-5 redraws/sec"
echo "Paste metrics (Ctrl+D when done):"
cat >> "$RESULTS_FILE"
echo "" >> "$RESULTS_FILE"

# Test 2: Small file
echo ""
echo "═══════════════════════════════════════════════════════════════"
echo "TEST 2: Small File (500 lines)"
echo "═══════════════════════════════════════════════════════════════"
echo ""
echo "1. Launch terminal: ./gradlew :compose-ui:run --no-daemon"
echo "2. Run: cat /tmp/test_500.txt"
echo "3. Wait 5 seconds after output completes"
echo "4. Copy the FINAL metrics output"
echo "5. Close terminal"
echo ""
echo "Press ENTER when you've copied the metrics..."
read -r

extract_metrics "Test 2: Small File (500 lines)"
echo "Expected: 200-500 redraws/sec during output"
echo "Paste metrics (Ctrl+D when done):"
cat >> "$RESULTS_FILE"
echo "" >> "$RESULTS_FILE"

# Test 3: Medium file
echo ""
echo "═══════════════════════════════════════════════════════════════"
echo "TEST 3: Medium File (2,000 lines)"
echo "═══════════════════════════════════════════════════════════════"
echo ""
echo "1. Launch terminal: ./gradlew :compose-ui:run --no-daemon"
echo "2. Run: cat /tmp/test_2000.txt"
echo "3. Wait 5 seconds after output completes"
echo "4. Copy the FINAL metrics output"
echo "5. Close terminal"
echo ""
echo "Press ENTER when you've copied the metrics..."
read -r

extract_metrics "Test 3: Medium File (2,000 lines)"
echo "Expected: 500-1000 redraws/sec during output"
echo "Paste metrics (Ctrl+D when done):"
cat >> "$RESULTS_FILE"
echo "" >> "$RESULTS_FILE"

# Test 4: Large file
echo ""
echo "═══════════════════════════════════════════════════════════════"
echo "TEST 4: Large File (10,000 lines)"
echo "═══════════════════════════════════════════════════════════════"
echo ""
echo "1. Launch terminal: ./gradlew :compose-ui:run --no-daemon"
echo "2. Run: cat /tmp/test_10000.txt"
echo "3. Wait 5 seconds after output completes"
echo "4. Copy the FINAL metrics output"
echo "5. Close terminal"
echo ""
echo "Press ENTER when you've copied the metrics..."
read -r

extract_metrics "Test 4: Large File (10,000 lines)"
echo "Expected: 1000-3000 redraws/sec during output"
echo "Paste metrics (Ctrl+D when done):"
cat >> "$RESULTS_FILE"
echo "" >> "$RESULTS_FILE"

# Summary
echo ""
echo "╔═══════════════════════════════════════════════════════════════╗"
echo "║  TESTS COMPLETE                                               ║"
echo "╚═══════════════════════════════════════════════════════════════╝"
echo ""
echo "Results saved to: $RESULTS_FILE"
echo ""
echo "To view results:"
echo "  cat $RESULTS_FILE"
echo ""
echo "Summary of your baseline data:"
cat "$RESULTS_FILE"
