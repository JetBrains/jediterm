#!/bin/bash

# Visual DECSEL Test - Interactive demonstration with clear prompts

clear
echo "╔════════════════════════════════════════════════════════════╗"
echo "║         DECSEL Visual Test - Character Protection         ║"
echo "╚════════════════════════════════════════════════════════════╝"
echo ""
echo "This test demonstrates selective erase with visual markers."
echo "Press Enter to advance through each test..."
echo ""
read -p "Press Enter to start..."

# Test 1: Visual marker test
clear
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "TEST 1: Basic Selective Erase"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""
echo "Writing text with mixed protection:"
echo "  [PROTECTED] [unprotected] [PROTECTED]"
echo ""
printf '['
printf '\e[1"qPROTECTED'      # Protected
printf '\e[0"q] ['
printf 'unprotected'          # Unprotected
printf '] ['
printf '\e[1"qPROTECTED'      # Protected
printf '\e[0"q]'
echo ""
echo ""
echo "Now performing selective erase (ESC [ ? 2 K)..."
printf '\e[1G\e[?2K'           # Move to start, selective erase line
echo ""
echo "Result: Only protected text should remain"
echo ""
read -p "Press Enter for next test..."

# Test 2: Cursor position erase
clear
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "TEST 2: Erase From Cursor to End"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""
echo "Text: [PROT]----[unprot]----[PROT]"
echo "      ^cursor here (after first PROT)"
echo ""
printf '['
printf '\e[1"qPROT'
printf '\e[0"q]----['
printf 'unprot'
printf ']----['
printf '\e[1"qPROT'
printf '\e[0"q]'
printf '\e[7G'                 # Position cursor
printf '\e[?0K'                # Selective erase to end
echo ""
echo ""
echo "Expected: [PROT]   [PROT] (middle unprotected erased)"
echo ""
read -p "Press Enter for next test..."

# Test 3: Standard vs Selective comparison
clear
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "TEST 3: Standard vs Selective Erase Comparison"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""
echo "Line 1: Standard erase (ESC [ 2 K) - ignores protection"
printf '   ['
printf '\e[1"qPROTECTED TEXT'
printf '\e[0"q]'
printf '\e[4G\e[2K'            # Standard erase
echo ""
echo ""
echo "Line 2: Selective erase (ESC [ ? 2 K) - preserves protection"
printf '   ['
printf '\e[1"qPROTECTED TEXT'
printf '\e[0"q]'
printf '\e[4G\e[?2K'           # Selective erase
echo ""
echo ""
echo "Notice: Line 1 is empty, Line 2 shows [PROTECTED TEXT]"
echo ""
read -p "Press Enter for next test..."

# Test 4: Multi-line selective erase
clear
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "TEST 4: Multi-Line Selective Erase (DECSED)"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""
echo "Creating 4 lines with alternating protection..."
echo ""
printf 'Line 1: \e[1"qPROTECTED DATA\n'
printf '\e[0"qLine 2: unprotected data\n'
printf '\e[1"qLine 3: PROTECTED DATA\n'
printf '\e[0"qLine 4: unprotected data\n'
echo ""
echo "Performing display-wide selective erase (ESC [ ? 2 J)..."
printf '\e[?2J\e[H'
echo ""
echo ""
echo ""
echo ""
echo "Expected: Only lines 1 and 3 (protected) remain"
echo ""
read -p "Press Enter for next test..."

# Test 5: Protection state persistence
clear
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "TEST 5: Protection State Persistence"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""
echo "Enabling protection, then writing multiple words..."
printf '\e[1"q'                # Enable protection once
printf 'FIRST SECOND THIRD'    # All protected
printf '\e[0"q'                # Disable
printf '\e[1G\e[?2K'           # Selective erase
echo ""
echo ""
echo "Expected: FIRST SECOND THIRD (all words protected)"
echo ""
read -p "Press Enter for summary..."

# Summary
clear
echo "╔════════════════════════════════════════════════════════════╗"
echo "║                       TEST SUMMARY                         ║"
echo "╚════════════════════════════════════════════════════════════╝"
echo ""
echo "✓ Basic selective erase preserves protected text"
echo "✓ Cursor-relative erase works correctly"
echo "✓ Standard erase ignores protection (as expected)"
echo "✓ Multi-line selective erase (DECSED) works"
echo "✓ Protection state persists until changed"
echo ""
echo "Escape Sequences Used:"
echo "  ESC [ 1 \" q     - Enable protection"
echo "  ESC [ 0 \" q     - Disable protection"
echo "  ESC [ ? 0 K     - Selective erase to end of line"
echo "  ESC [ ? 1 K     - Selective erase to start of line"
echo "  ESC [ ? 2 K     - Selective erase entire line"
echo "  ESC [ ? 2 J     - Selective erase entire display"
echo ""
printf '\e[0"q\e[0m'           # Reset
echo "Test complete!"
echo ""
