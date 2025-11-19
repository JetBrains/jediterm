#!/bin/bash
# Cursor diagnostic script - monitors cursor position, shape, and visibility

echo "╔═══════════════════════════════════════════════════════════════╗"
echo "║  CURSOR DIAGNOSTICS                                           ║"
echo "╚═══════════════════════════════════════════════════════════════╝"
echo ""
echo "This script monitors cursor events in real-time."
echo ""
echo "What you'll see:"
echo "  🔵 CURSOR MOVE: Position changes (x,y)"
echo "  🔷 CURSOR SHAPE: Shape changes (BLOCK, UNDERLINE, BAR, etc.)"
echo "  👁️  CURSOR VISIBLE: Visibility changes (true/false)"
echo ""
echo "Test scenarios:"
echo "  1. Launch terminal → Should see cursor at (0,0)"
echo "  2. Type characters → Should see cursor moving"
echo "  3. Open Claude Code → Watch for cursor visibility changes"
echo "  4. Move cursor with arrows → Should see position updates"
echo "  5. Enter insert mode (vim) → May see shape changes"
echo ""
echo "Press ENTER to launch terminal with cursor diagnostics..."
read -r

cd "$(dirname "$0")/.."

echo ""
echo "═══════════════════════════════════════════════════════════════"
echo "LAUNCHING TERMINAL - Cursor debugging ENABLED"
echo "═══════════════════════════════════════════════════════════════"
echo ""

# Enable cursor debugging via environment variable
export JEDITERM_DEBUG_CURSOR=true

# Launch terminal and filter for cursor events
./gradlew :compose-ui:run --no-daemon 2>&1 | grep --line-buffered -E "(🔵|🔷|👁️|DEBUG Cursor)" | while IFS= read -r line; do
    timestamp=$(date +"%H:%M:%S.%3N")

    # Colorize output for readability
    if [[ "$line" =~ "🔵" ]]; then
        # Cursor move - highlight in blue
        echo -e "\033[1;34m[$timestamp]\033[0m $line"
    elif [[ "$line" =~ "🔷" ]]; then
        # Cursor shape - highlight in cyan
        echo -e "\033[1;36m[$timestamp]\033[0m $line"
    elif [[ "$line" =~ "👁️" ]]; then
        # Cursor visibility - highlight in yellow
        echo -e "\033[1;33m[$timestamp]\033[0m $line"
    elif [[ "$line" =~ "DEBUG Cursor" ]]; then
        # Debug cursor info - highlight in magenta
        echo -e "\033[1;35m[$timestamp]\033[0m $line"
    else
        echo "[$timestamp] $line"
    fi
done
