#!/bin/bash
# Escape sequence diagnostic script - captures raw terminal output
# This helps understand what escape sequences apps like Claude Code are sending

echo "╔═══════════════════════════════════════════════════════════════╗"
echo "║  ESCAPE SEQUENCE DIAGNOSTICS                                  ║"
echo "╚═══════════════════════════════════════════════════════════════╝"
echo ""
echo "This script captures raw escape sequences from terminal output."
echo ""
echo "What you'll see:"
echo "  - CSI sequences (ESC[...)"
echo "  - SGR codes (colors, bold, reverse video, etc.)"
echo "  - Cursor positioning commands"
echo "  - Mode changes"
echo ""
echo "Test scenarios:"
echo "  1. Launch terminal and open Claude Code"
echo "  2. Watch for cursor-related sequences"
echo "  3. Look for SGR 7 (reverse video) or SGR 27 (normal video)"
echo "  4. Look for color codes (SGR 38/48)"
echo ""
echo "Common SGR codes:"
echo "  ESC[7m  - Reverse video (swap fg/bg)"
echo "  ESC[27m - Normal video"
echo "  ESC[38;... - Set foreground color"
echo "  ESC[48;... - Set background color"
echo ""
echo "Press ENTER to launch terminal with escape sequence monitoring..."
read -r

cd "$(dirname "$0")/.."

echo ""
echo "═══════════════════════════════════════════════════════════════"
echo "LAUNCHING TERMINAL - Escape sequences will be shown below"
echo "═══════════════════════════════════════════════════════════════"
echo ""

# Create a log file for the raw output
LOG_FILE="/tmp/jediterm_escape_sequences_$(date +%Y%m%d_%H%M%S).log"

echo "Raw output will be saved to: $LOG_FILE"
echo ""

# Launch terminal and capture ALL output
# We'll use 'cat -v' to make escape sequences visible
./gradlew :compose-ui:run --no-daemon 2>&1 | tee "$LOG_FILE" | while IFS= read -r line; do
    # Only show lines that contain escape sequences or interesting patterns
    if [[ "$line" =~ $'\033' ]] || [[ "$line" =~ "CSI" ]] || [[ "$line" =~ "SGR" ]]; then
        timestamp=$(date +"%H:%M:%S.%3N")

        # Try to decode some common sequences
        decoded=""
        if [[ "$line" =~ $'\033\[7m' ]]; then
            decoded=" [REVERSE VIDEO ON]"
        elif [[ "$line" =~ $'\033\[27m' ]]; then
            decoded=" [REVERSE VIDEO OFF]"
        elif [[ "$line" =~ $'\033\[38' ]]; then
            decoded=" [SET FG COLOR]"
        elif [[ "$line" =~ $'\033\[48' ]]; then
            decoded=" [SET BG COLOR]"
        fi

        # Show the line with escape sequences visible (using sed to show ESC as ^[)
        echo -n "[$timestamp] "
        echo "$line" | sed 's/\x1b/^[/g'
        if [[ -n "$decoded" ]]; then
            echo "           └─$decoded"
        fi
    fi
done

echo ""
echo "═══════════════════════════════════════════════════════════════"
echo "Session ended. Full log saved to: $LOG_FILE"
echo "═══════════════════════════════════════════════════════════════"
