#!/bin/bash

# DECSEL (Selective Erase) Test Script
# Tests DECSCA (character protection) and selective erase operations

# Color codes for output
RED='\033[0;31m'
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}DECSEL Implementation Test${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""

# Test 1: DECSCA - Character Protection Setting
echo -e "${GREEN}Test 1: DECSCA - Character Protection${NC}"
echo "Setting protection ON, writing 'PROTECTED', then OFF, writing 'UNPROTECTED'"
printf '\e[1"q'          # Enable protection
printf 'PROTECTED'
printf '\e[0"q'          # Disable protection
printf 'UNPROTECTED'
echo ""
echo "Expected: PROTECTEDUNPROTECTED"
echo ""
sleep 2

# Test 2: DECSEL Mode 0 - Erase from cursor to end (selective)
echo -e "${GREEN}Test 2: DECSEL Mode 0 - Erase cursor to end${NC}"
printf '\e[1"qPROT\e[0"qUNPROT'  # PROT protected, UNPROT unprotected
printf '\e[6G'           # Move cursor to column 6 (after PROT)
printf '\e[?0K'          # Selective erase to end of line
echo ""
echo "Expected: PROT  (UNPROT should be erased, PROT remains)"
echo ""
sleep 2

# Test 3: DECSEL Mode 1 - Erase from start to cursor (selective)
echo -e "${GREEN}Test 3: DECSEL Mode 1 - Erase start to cursor${NC}"
printf '\e[0"qUNPROT\e[1"qPROT'  # UNPROT unprotected, PROT protected
printf '\e[8G'           # Move cursor to column 8 (before PROT)
printf '\e[?1K'          # Selective erase from start to cursor
echo ""
echo "Expected:     PROT (UNPROT should be erased, PROT remains)"
echo ""
sleep 2

# Test 4: DECSEL Mode 2 - Erase entire line (selective)
echo -e "${GREEN}Test 4: DECSEL Mode 2 - Erase entire line${NC}"
printf 'START \e[1"qPROTECTED\e[0"q END'  # Protected middle section
printf '\e[1G'           # Move cursor to start
printf '\e[?2K'          # Selective erase entire line
echo ""
echo "Expected:      PROTECTED      (START and END erased, PROTECTED remains)"
echo ""
sleep 2

# Test 5: Standard vs Selective Erase Comparison
echo -e "${GREEN}Test 5: Standard Erase (ignores protection)${NC}"
printf '\e[1"qPROTECTED'  # Write protected text
printf '\e[1G'           # Move cursor to start
printf '\e[0K'           # Standard erase (without ?)
echo ""
echo "Expected: (empty line - standard erase ignores protection)"
echo ""
sleep 2

# Test 6: DECSED Mode 0 - Selective erase display (cursor to end)
echo -e "${GREEN}Test 6: DECSED Mode 0 - Erase cursor to end of display${NC}"
printf 'Line 1: \e[1"qPROTECTED\e[0"q\n'
printf 'Line 2: \e[0"qUNPROTECTED\e[0"q\n'
printf 'Line 3: \e[1"qPROTECTED\e[0"q\n'
printf '\e[2;1H'         # Move to line 2, column 1
printf '\e[?0J'          # Selective erase from cursor to end of display
echo ""
echo "Expected:"
echo "Line 1: PROTECTED"
echo "Line 2: "
echo "Line 3: PROTECTED"
echo "(Lines 2 and 3 unprotected text erased, protected remains)"
echo ""
sleep 3

# Test 7: DECSED Mode 2 - Selective erase entire display
echo -e "${GREEN}Test 7: DECSED Mode 2 - Erase entire display${NC}"
printf '\e[1"qLine 1: PROTECTED\n'
printf '\e[0"qLine 2: UNPROTECTED\n'
printf '\e[1"qLine 3: PROTECTED\n'
printf '\e[0"qLine 4: UNPROTECTED\n'
printf '\e[?2J'          # Selective erase entire display
printf '\e[H'            # Move cursor to home
echo ""
echo "Expected:"
echo "Line 1: PROTECTED"
echo ""
echo "Line 3: PROTECTED"
echo ""
echo "(Lines 2 and 4 erased, protected lines remain)"
echo ""
sleep 3

# Test 8: Mixed protected/unprotected on same line
echo -e "${GREEN}Test 8: Mixed protection on single line${NC}"
printf '\e[0"qAAA\e[1"qBBB\e[0"qCCC\e[1"qDDD\e[0"qEEE'
printf '\e[1G'           # Move to start
printf '\e[?2K'          # Selective erase entire line
echo ""
echo "Expected:    BBB   DDD    (A, C, E erased; B, D protected)"
echo ""
sleep 2

# Test 9: Protection persists until explicitly changed
echo -e "${GREEN}Test 9: Protection state persistence${NC}"
printf '\e[1"q'          # Enable protection
printf 'AAA '
printf 'BBB '            # Still protected (not explicitly disabled)
printf 'CCC'
printf '\e[0"q'          # Disable protection
printf '\e[1G'
printf '\e[?2K'          # Selective erase
echo ""
echo "Expected: AAA BBB CCC (all protected because state persisted)"
echo ""
sleep 2

# Test 10: Empty protection (edge case)
echo -e "${GREEN}Test 10: Protection on empty content${NC}"
printf '\e[1"q'          # Enable protection (but write nothing)
printf '\e[0"q'          # Disable protection
printf 'UNPROTECTED'
printf '\e[1G'
printf '\e[?0K'          # Selective erase
echo ""
echo "Expected: (empty - no protected content to preserve)"
echo ""
sleep 2

# Summary
echo ""
echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}Test Summary${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""
echo "Key sequences tested:"
echo "  - ESC [ 1 \" q    : Enable protection (DECSCA)"
echo "  - ESC [ 0 \" q    : Disable protection (DECSCA)"
echo "  - ESC [ 2 \" q    : Disable protection (DECSCA)"
echo "  - ESC [ ? 0 K     : Selective erase cursor to end (DECSEL)"
echo "  - ESC [ ? 1 K     : Selective erase start to cursor (DECSEL)"
echo "  - ESC [ ? 2 K     : Selective erase entire line (DECSEL)"
echo "  - ESC [ ? 0 J     : Selective erase cursor to end display (DECSED)"
echo "  - ESC [ ? 2 J     : Selective erase entire display (DECSED)"
echo ""
echo -e "${YELLOW}Note: Standard erase (without ?) always erases everything${NC}"
echo -e "${YELLOW}      regardless of protection status.${NC}"
echo ""

# Reset
printf '\e[0"q'          # Ensure protection is off
printf '\e[0m'           # Reset all attributes
