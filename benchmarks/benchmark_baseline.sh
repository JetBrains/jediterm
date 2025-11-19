#!/bin/bash
# Phase 1: Baseline Performance Measurement Script
# This script runs various workload tests to measure current redraw performance

set -e

echo "╔═══════════════════════════════════════════════════════════════╗"
echo "║  PHASE 1: BASELINE REDRAW PERFORMANCE MEASUREMENT             ║"
echo "║  JediTermKt - Terminal Rendering Optimization                 ║"
echo "╚═══════════════════════════════════════════════════════════════╝"
echo ""

# Ensure we're in the project directory
cd "$(dirname "$0")"

# Create benchmark results directory
RESULTS_DIR="/tmp/jediterm_baseline_metrics"
mkdir -p "$RESULTS_DIR"
TIMESTAMP=$(date +"%Y%m%d_%H%M%S")
RESULTS_FILE="$RESULTS_DIR/baseline_${TIMESTAMP}.txt"

echo "Results will be saved to: $RESULTS_FILE"
echo ""

# Function to create test files if they don't exist
create_test_files() {
    echo "Creating test files..."

    # Small test file (~100KB)
    if [ ! -f /tmp/test_small.txt ]; then
        head -n 1000 /usr/share/dict/words > /tmp/test_small.txt 2>/dev/null || \
        yes "This is a test line with some content" | head -n 1000 > /tmp/test_small.txt
    fi

    # Medium test file (~1MB)
    if [ ! -f /tmp/test_medium.txt ]; then
        cat /usr/share/dict/words /usr/share/dict/words /usr/share/dict/words > /tmp/test_medium.txt 2>/dev/null || \
        yes "This is a test line with some content" | head -n 10000 > /tmp/test_medium.txt
    fi

    # Large test file (~5MB)
    if [ ! -f /tmp/test_large.txt ]; then
        for i in {1..10}; do
            cat /usr/share/dict/words >> /tmp/test_large.txt 2>/dev/null || \
            yes "This is a test line with some content" | head -n 5000 >> /tmp/test_large.txt
        done
    fi

    echo "✓ Test files created"
    echo ""
}

# Create test files
create_test_files

# Function to display test header
test_header() {
    echo "═══════════════════════════════════════════════════════════════"
    echo "TEST: $1"
    echo "═══════════════════════════════════════════════════════════════"
}

# Function to wait for user
wait_for_user() {
    echo ""
    echo "Press ENTER when ready to continue..."
    read -r
}

# Save system info
{
    echo "╔═══════════════════════════════════════════════════════════════╗"
    echo "║  SYSTEM INFORMATION                                           ║"
    echo "╚═══════════════════════════════════════════════════════════════╝"
    echo ""
    echo "Date: $(date)"
    echo "Hostname: $(hostname)"
    echo "OS: $(uname -s) $(uname -r)"
    echo "CPU: $(sysctl -n machdep.cpu.brand_string 2>/dev/null || echo 'Unknown')"
    echo "Memory: $(sysctl -n hw.memsize 2>/dev/null | awk '{print $1/1024/1024/1024 " GB"}' || echo 'Unknown')"
    echo ""
} > "$RESULTS_FILE"

echo "╔═══════════════════════════════════════════════════════════════╗"
echo "║  TEST SCENARIOS                                               ║"
echo "╚═══════════════════════════════════════════════════════════════╝"
echo ""
echo "This script will guide you through running various test scenarios."
echo "You'll need to manually execute commands in the JediTerm terminal."
echo ""
echo "Make sure JediTerm is built and ready to run."
echo ""

wait_for_user

# Check if already built
echo "Checking build status..."
if ./gradlew :compose-ui:assemble --no-daemon > /tmp/jediterm_build.log 2>&1; then
    echo "✓ Build complete"
else
    echo "⚠️ Build failed - check /tmp/jediterm_build.log"
    echo "Continuing anyway (binary may already exist)..."
fi
echo ""

# Test 1: Idle state (baseline CPU usage)
test_header "Test 1: Idle Terminal (Baseline CPU Usage)"
{
    echo ""
    echo "═══════════════════════════════════════════════════════════════"
    echo "TEST 1: IDLE TERMINAL (BASELINE CPU USAGE)"
    echo "═══════════════════════════════════════════════════════════════"
    echo ""
    echo "Description: Measure CPU usage and redraw rate with no activity"
    echo "Duration: 30 seconds"
    echo ""
} >> "$RESULTS_FILE"

echo "Instructions:"
echo "1. Start JediTerm: ./gradlew :compose-ui:run --no-daemon"
echo "2. Wait for terminal to load completely"
echo "3. Do NOT type anything - let it sit idle"
echo "4. Observe the REDRAW PERFORMANCE METRICS in the console"
echo "5. Let it run for 30 seconds"
echo "6. Close the terminal when done"
echo ""
wait_for_user

# Test 2: Small file output
test_header "Test 2: Small File Output (~100KB, 1000 lines)"
{
    echo ""
    echo "═══════════════════════════════════════════════════════════════"
    echo "TEST 2: SMALL FILE OUTPUT (~100KB, 1000 lines)"
    echo "═══════════════════════════════════════════════════════════════"
    echo ""
} >> "$RESULTS_FILE"

echo "Instructions:"
echo "1. Start JediTerm: ./gradlew :compose-ui:run --no-daemon"
echo "2. Run: cat /tmp/test_small.txt"
echo "3. Observe the redraw metrics during output"
echo "4. Note the final metrics after output completes"
echo "5. Copy the metrics and paste them into: $RESULTS_FILE"
echo "6. Close the terminal when done"
echo ""
wait_for_user

# Test 3: Medium file output
test_header "Test 3: Medium File Output (~1MB, 10,000 lines)"
{
    echo ""
    echo "═══════════════════════════════════════════════════════════════"
    echo "TEST 3: MEDIUM FILE OUTPUT (~1MB, 10,000 lines)"
    echo "═══════════════════════════════════════════════════════════════"
    echo ""
} >> "$RESULTS_FILE"

echo "Instructions:"
echo "1. Start JediTerm: ./gradlew :compose-ui:run --no-daemon"
echo "2. Run: cat /tmp/test_medium.txt"
echo "3. Observe the redraw metrics during output"
echo "4. Note peak redraws/sec and total redraws"
echo "5. Monitor CPU usage in Activity Monitor"
echo "6. Copy the metrics and paste them into: $RESULTS_FILE"
echo "7. Close the terminal when done"
echo ""
wait_for_user

# Test 4: Large file output
test_header "Test 4: Large File Output (~5MB, 50,000+ lines)"
{
    echo ""
    echo "═══════════════════════════════════════════════════════════════"
    echo "TEST 4: LARGE FILE OUTPUT (~5MB, 50,000+ lines)"
    echo "═══════════════════════════════════════════════════════════════"
    echo ""
} >> "$RESULTS_FILE"

echo "Instructions:"
echo "1. Start JediTerm: ./gradlew :compose-ui:run --no-daemon"
echo "2. Run: cat /tmp/test_large.txt"
echo "3. Watch for stuttering or frame drops"
echo "4. Monitor CPU usage (should be very high)"
echo "5. Note peak redraws/sec (expected: 1000+ redraws/sec)"
echo "6. Copy the metrics and paste them into: $RESULTS_FILE"
echo "7. Close the terminal when done"
echo ""
wait_for_user

# Test 5: Rapid repeated output
test_header "Test 5: Rapid Repeated Output (10,000 lines)"
{
    echo ""
    echo "═══════════════════════════════════════════════════════════════"
    echo "TEST 5: RAPID REPEATED OUTPUT (10,000 lines)"
    echo "═══════════════════════════════════════════════════════════════"
    echo ""
} >> "$RESULTS_FILE"

echo "Instructions:"
echo "1. Start JediTerm: ./gradlew :compose-ui:run --no-daemon"
echo "2. Run: yes 'Test line with emoji ☁️ symbols ❯▶' | head -10000"
echo "3. This is the WORST CASE scenario (constant output)"
echo "4. Observe peak redraws/sec (expected: 2000+ redraws/sec)"
echo "5. Check for visual stuttering or dropped frames"
echo "6. Copy the metrics and paste them into: $RESULTS_FILE"
echo "7. Close the terminal when done"
echo ""
wait_for_user

# Test 6: Interactive typing
test_header "Test 6: Interactive Typing"
{
    echo ""
    echo "═══════════════════════════════════════════════════════════════"
    echo "TEST 6: INTERACTIVE TYPING"
    echo "═══════════════════════════════════════════════════════════════"
    echo ""
} >> "$RESULTS_FILE"

echo "Instructions:"
echo "1. Start JediTerm: ./gradlew :compose-ui:run --no-daemon"
echo "2. Type rapidly for 30 seconds"
echo "3. Use arrow keys, backspace, etc."
echo "4. Check for ANY perceptible lag (should be none)"
echo "5. Note redraws/sec during typing (expected: 10-50 redraws/sec)"
echo "6. Copy the metrics and paste them into: $RESULTS_FILE"
echo "7. Close the terminal when done"
echo ""
wait_for_user

# Test 7: Vim editing
test_header "Test 7: Vim Editing"
{
    echo ""
    echo "═══════════════════════════════════════════════════════════════"
    echo "TEST 7: VIM EDITING"
    echo "═══════════════════════════════════════════════════════════════"
    echo ""
} >> "$RESULTS_FILE"

echo "Instructions:"
echo "1. Start JediTerm: ./gradlew :compose-ui:run --no-daemon"
echo "2. Run: vim /tmp/test_medium.txt"
echo "3. Scroll rapidly with j/k/Ctrl-D/Ctrl-U"
echo "4. Check for lag or stuttering (should be none)"
echo "5. Note redraws/sec during scrolling"
echo "6. Exit vim with :q!"
echo "7. Copy the metrics and paste them into: $RESULTS_FILE"
echo "8. Close the terminal when done"
echo ""
wait_for_user

# Test 8: Less paging
test_header "Test 8: Less Paging"
{
    echo ""
    echo "═══════════════════════════════════════════════════════════════"
    echo "TEST 8: LESS PAGING"
    echo "═══════════════════════════════════════════════════════════════"
    echo ""
} >> "$RESULTS_FILE"

echo "Instructions:"
echo "1. Start JediTerm: ./gradlew :compose-ui:run --no-daemon"
echo "2. Run: less /tmp/test_large.txt"
echo "3. Use Space/b to page up/down rapidly"
echo "4. Check for smooth scrolling"
echo "5. Exit with 'q'"
echo "6. Copy the metrics and paste them into: $RESULTS_FILE"
echo "7. Close the terminal when done"
echo ""
wait_for_user

# Summary
echo ""
echo "╔═══════════════════════════════════════════════════════════════╗"
echo "║  PHASE 1 COMPLETE - BASELINE METRICS COLLECTED                ║"
echo "╚═══════════════════════════════════════════════════════════════╝"
echo ""
echo "Results saved to: $RESULTS_FILE"
echo ""
echo "SUMMARY OF EXPECTED FINDINGS:"
echo "────────────────────────────────────────────────────────────────"
echo "• Idle state: 10-30 redraws/sec (cursor blink + UI updates)"
echo "• Small file (1K lines): 500-1000 redraws/sec"
echo "• Medium file (10K lines): 1000-2000 redraws/sec"
echo "• Large file (50K+ lines): 2000-5000 redraws/sec"
echo "• Rapid output (yes|head): 3000-10000 redraws/sec (WORST CASE)"
echo "• Interactive typing: 10-100 redraws/sec"
echo "• Vim scrolling: 50-200 redraws/sec"
echo "• Less paging: 50-200 redraws/sec"
echo ""
echo "OPTIMIZATION OPPORTUNITIES:"
echo "────────────────────────────────────────────────────────────────"
echo "• Bulk output can be throttled to 60fps (16ms) = 60 redraws/sec"
echo "• Expected reduction: 90-95% fewer redraws during bulk output"
echo "• Interactive apps should maintain <16ms response (imperceptible)"
echo ""
echo "Next: Edit $RESULTS_FILE to add your manual observations"
echo "Then: Proceed to Phase 2 (Design Strategy)"
echo ""
