#!/bin/bash
# Diagnostic script to monitor adaptive debouncing mode in real-time

echo "╔═══════════════════════════════════════════════════════════════╗"
echo "║  ADAPTIVE DEBOUNCING MODE DIAGNOSTICS                         ║"
echo "╚═══════════════════════════════════════════════════════════════╝"
echo ""
echo "This script monitors the terminal's rendering mode in real-time."
echo ""
echo "What you'll see:"
echo "  - Mode transitions (INTERACTIVE ↔ HIGH_VOLUME)"
echo "  - Current redraw rate"
echo "  - Mode switch timestamps"
echo ""
echo "Test scenarios:"
echo "  1. Launch terminal (should be INTERACTIVE)"
echo "  2. Run: cat /tmp/test_10000.txt (should switch to HIGH_VOLUME)"
echo "  3. After output stops (should auto-recover to INTERACTIVE)"
echo "  4. Open Claude Code/vim (should stay INTERACTIVE)"
echo "  5. Toggle thinking mode (watch for mode changes)"
echo ""
echo "Press ENTER to launch terminal with diagnostics..."
read -r

cd "$(dirname "$0")/.."

echo ""
echo "═══════════════════════════════════════════════════════════════"
echo "LAUNCHING TERMINAL - Watch for mode transitions below"
echo "═══════════════════════════════════════════════════════════════"
echo ""

# Launch terminal and filter for mode-related output
./gradlew :compose-ui:run --no-daemon 2>&1 | grep --line-buffered -E "(🔄|Mode:|Current rate:|REDRAW PERFORMANCE)" | while IFS= read -r line; do
    timestamp=$(date +"%H:%M:%S.%3N")

    # Colorize output for readability
    if [[ "$line" =~ "🔄" ]]; then
        # Mode transition - highlight in yellow
        echo -e "\033[1;33m[$timestamp]\033[0m $line"
    elif [[ "$line" =~ "Mode:" ]]; then
        # Mode status - highlight in cyan
        echo -e "\033[1;36m[$timestamp]\033[0m $line"
    elif [[ "$line" =~ "Current rate:" ]]; then
        # Rate info - highlight in green
        echo -e "\033[1;32m[$timestamp]\033[0m $line"
    else
        # Other metrics
        echo "[$timestamp] $line"
    fi
done
