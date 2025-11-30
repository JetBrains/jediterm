#!/bin/bash

# Quick DECSEL Test - Fast verification of core functionality

echo "=== Quick DECSEL Test ==="
echo ""

# Test 1: Basic protection and selective erase
echo "Test 1: Basic selective erase"
printf '\e[1"qPROTECTED\e[0"q-UNPROTECTED'
printf '\e[11G'          # Move cursor after "PROTECTED"
printf '\e[?0K'          # Selective erase to end
echo ""
echo "Expected: PROTECTED"
echo ""

# Test 2: Standard erase ignores protection
echo "Test 2: Standard erase (should erase everything)"
printf '\e[1"qPROTECTED'
printf '\e[1G'
printf '\e[0K'           # Standard erase (no ?)
echo ""
echo "Expected: (empty line)"
echo ""

# Test 3: Full line selective erase
echo "Test 3: Full line selective erase"
printf 'START-\e[1"qMIDDLE\e[0"q-END'
printf '\e[1G'
printf '\e[?2K'          # Selective erase entire line
echo ""
echo "Expected: MIDDLE (only protected part)"
echo ""

# Test 4: Display selective erase
echo "Test 4: Display selective erase"
printf '\e[1"qProtected Line 1\n'
printf '\e[0"qUnprotected Line 2\n'
printf '\e[1"qProtected Line 3\n'
printf '\e[?2J'          # Selective erase display
printf '\e[H'            # Home
echo ""
echo ""
echo ""
echo "Expected: Protected Line 1 and Protected Line 3 (line 2 erased)"
echo ""

# Reset
printf '\e[0"q\e[0m'
echo "=== Test Complete ==="
