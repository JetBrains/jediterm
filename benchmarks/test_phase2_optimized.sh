#!/bin/bash
# Phase 2: Optimized Performance Test
# Tests the adaptive debouncing implementation

set -e

echo "╔═══════════════════════════════════════════════════════════════╗"
echo "║  PHASE 2: OPTIMIZED PERFORMANCE TEST                          ║"
echo "║  Adaptive Debouncing Implementation                           ║"
echo "╚═══════════════════════════════════════════════════════════════╝"
echo ""

cd "$(dirname "$0")"

# Results file
RESULTS_FILE="/tmp/phase2_optimized_results_$(date +%Y%m%d_%H%M%S).txt"

echo "Results will be saved to: $RESULTS_FILE"
echo ""

# Ensure test files exist
if [ ! -f /tmp/test_500.txt ]; then
    echo "Creating test files..."
    head -n 500 /usr/share/dict/words > /tmp/test_500.txt 2>/dev/null || \
        (for i in {1..500}; do echo "Line $i with some text content"; done > /tmp/test_500.txt)

    head -n 2000 /usr/share/dict/words > /tmp/test_2000.txt 2>/dev/null || \
        (for i in {1..2000}; do echo "Line $i with some text content"; done > /tmp/test_2000.txt)

    head -n 10000 /usr/share/dict/words > /tmp/test_10000.txt 2>/dev/null || \
        (for i in {1..10000}; do echo "Line $i with some text content"; done > /tmp/test_10000.txt)

    echo "✓ Test files created"
fi

echo ""
echo "╔═══════════════════════════════════════════════════════════════╗"
echo "║  WHAT TO LOOK FOR                                             ║"
echo "╚═══════════════════════════════════════════════════════════════╝"
echo ""
echo "During testing, watch for:"
echo ""
echo "1. 🔄 Mode Transitions:"
echo "   - Should see: 'Redraw mode: INTERACTIVE → HIGH_VOLUME'"
echo "   - Happens when >100 redraws/sec detected"
echo ""
echo "2. 📊 Metrics Display:"
echo "   - Mode: INTERACTIVE or HIGH_VOLUME"
echo "   - Current rate: Should be ≤60 redraws/sec for large files"
echo "   - Coalesced redraws: Count of skipped redraws"
echo "   - Efficiency: % of redraws saved"
echo ""
echo "3. 🎯 Expected Results:"
echo "   - Small file (500 lines): ~24-60 redraws/sec (INTERACTIVE)"
echo "   - Medium file (2K lines): ~20-60 redraws/sec (HIGH_VOLUME)"
echo "   - Large file (10K lines): ~20-60 redraws/sec (HIGH_VOLUME)"
echo "   - Typing: IMMEDIATE mode, zero lag"
echo ""
echo "4. 🔄 Auto-Recovery:"
echo "   - After bulk output stops, should see:"
echo "   - 'Redraw mode: HIGH_VOLUME → INTERACTIVE (auto-recovery)'"
echo ""
echo "Press ENTER to continue..."
read -r

# Save header
{
    echo "╔═══════════════════════════════════════════════════════════════╗"
    echo "║  PHASE 2: OPTIMIZED PERFORMANCE RESULTS                      ║"
    echo "╚═══════════════════════════════════════════════════════════════╝"
    echo ""
    echo "Date: $(date)"
    echo ""
} > "$RESULTS_FILE"

# Function to prompt for test
run_test() {
    local test_num="$1"
    local test_name="$2"
    local command="$3"
    local expected="$4"

    echo ""
    echo "═══════════════════════════════════════════════════════════════"
    echo "TEST $test_num: $test_name"
    echo "═══════════════════════════════════════════════════════════════"
    echo ""
    echo "1. Launch terminal: ./gradlew :compose-ui:run --no-daemon"
    echo "2. Run command: $command"
    echo "3. Expected: $expected"
    echo "4. Copy the FINAL metrics output"
    echo "5. Close terminal"
    echo ""
    echo "Press ENTER when you've copied the metrics..."
    read -r

    {
        echo "═══════════════════════════════════════════════════════════════"
        echo "TEST $test_num: $test_name"
        echo "═══════════════════════════════════════════════════════════════"
        echo ""
        echo "Command: $command"
        echo "Expected: $expected"
        echo ""
    } >> "$RESULTS_FILE"

    echo "Paste metrics (Ctrl+D when done):"
    cat >> "$RESULTS_FILE"
    echo "" >> "$RESULTS_FILE"
}

# Test 1: Idle
run_test "1" "Idle Terminal (30 seconds)" \
    "(wait 30 seconds)" \
    "INTERACTIVE mode, ~1-5 redraws/sec"

# Test 2: Small file
run_test "2" "Small File (500 lines)" \
    "cat /tmp/test_500.txt" \
    "INTERACTIVE mode, ~24-60 redraws/sec"

# Test 3: Medium file
run_test "3" "Medium File (2,000 lines)" \
    "cat /tmp/test_2000.txt" \
    "HIGH_VOLUME mode triggered, ~20-60 redraws/sec"

# Test 4: Large file
run_test "4" "Large File (10,000 lines)" \
    "cat /tmp/test_10000.txt" \
    "HIGH_VOLUME mode, ~20-60 redraws/sec, 80-90% efficiency"

# Test 5: Interactive typing
echo ""
echo "═══════════════════════════════════════════════════════════════"
echo "TEST 5: Interactive Typing"
echo "═══════════════════════════════════════════════════════════════"
echo ""
echo "1. Launch terminal: ./gradlew :compose-ui:run --no-daemon"
echo "2. Type rapidly for 30 seconds"
echo "3. Check for ANY perceptible lag (should be NONE)"
echo "4. Did you notice any lag? (y/n)"
read -r lag_response
echo ""
echo "Enter your observations:"
read -r typing_obs

{
    echo "═══════════════════════════════════════════════════════════════"
    echo "TEST 5: Interactive Typing"
    echo "═══════════════════════════════════════════════════════════════"
    echo ""
    echo "Lag detected: $lag_response"
    echo "Observations: $typing_obs"
    echo ""
} >> "$RESULTS_FILE"

# Summary
echo ""
echo "╔═══════════════════════════════════════════════════════════════╗"
echo "║  TESTS COMPLETE                                               ║"
echo "╚═══════════════════════════════════════════════════════════════╝"
echo ""
echo "Results saved to: $RESULTS_FILE"
echo ""
echo "To compare with baseline:"
echo "  diff /tmp/baseline_results_20251117_101815.txt $RESULTS_FILE"
echo ""
echo "View results:"
echo "  cat $RESULTS_FILE"
echo ""
