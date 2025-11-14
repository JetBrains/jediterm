#!/bin/bash
# Interactive test script for JediTerm Compose rendering features
# Run this script inside the JediTerm window to test all features

echo "=========================================="
echo "JediTerm Compose Rendering Tests"
echo "=========================================="
echo ""
sleep 1

# Test 1: Basic Colors
echo "Test 1: Basic 16 Colors"
echo "Normal colors:"
for i in {0..7}; do
    echo -ne "\e[38;5;${i}m■\e[0m "
done
echo ""
echo "Bright colors:"
for i in {8..15}; do
    echo -ne "\e[38;5;${i}m■\e[0m "
done
echo ""
echo ""
sleep 2

# Test 2: Text Attributes
echo "Test 2: Text Attributes"
echo -e "\e[1mBOLD text\e[0m"
sleep 1
echo -e "\e[2mDIM text\e[0m"
sleep 1
echo -e "\e[3mITALIC text\e[0m"
sleep 1
echo -e "\e[4mUNDERLINED text\e[0m"
sleep 1
echo -e "\e[7mINVERSE text\e[0m"
sleep 1
echo -e "\e[8mHIDDEN text (should be invisible)\e[0m <- HIDDEN"
sleep 1
echo ""

# Test 3: BLINK Animations
echo "Test 3: BLINK Animations (watch for 5 seconds)"
echo -e "\e[5mSLOW BLINK text\e[0m (500ms)"
echo -e "\e[6mRAPID BLINK text\e[0m (250ms)"
sleep 5
echo ""

# Test 4: Combined Attributes
echo "Test 4: Combined Attributes"
echo -e "\e[1;4;31mBOLD + UNDERLINE + RED\e[0m"
sleep 1
echo -e "\e[2;3;34mDIM + ITALIC + BLUE\e[0m"
sleep 1
echo -e "\e[1;7;32mBOLD + INVERSE + GREEN\e[0m"
sleep 1
echo ""

# Test 5: 256-Color Palette Sample
echo "Test 5: 256-Color Palette (sample)"
echo "Colors 16-51 (first row of 6x6x6 cube):"
for i in {16..51}; do
    echo -ne "\e[38;5;${i}m█\e[0m"
    if [ $(((i - 15) % 36)) -eq 0 ]; then echo ""; fi
done
echo ""
echo "Grayscale (232-255):"
for i in {232..255}; do
    echo -ne "\e[38;5;${i}m█\e[0m"
done
echo ""
echo ""
sleep 2

# Test 6: Cursor Shapes (DECSCUSR)
echo "Test 6: Cursor Shapes"
echo "Watch the cursor change shape (2 seconds each)"
echo ""

echo "BLINK_BLOCK (ESC [ 1 SP q)"
echo -ne "\e[1 q"
sleep 2

echo "STEADY_BLOCK (ESC [ 2 SP q)"
echo -ne "\e[2 q"
sleep 2

echo "BLINK_UNDERLINE (ESC [ 3 SP q)"
echo -ne "\e[3 q"
sleep 2

echo "STEADY_UNDERLINE (ESC [ 4 SP q)"
echo -ne "\e[4 q"
sleep 2

echo "BLINK_VERTICAL_BAR (ESC [ 5 SP q)"
echo -ne "\e[5 q"
sleep 2

echo "STEADY_VERTICAL_BAR (ESC [ 6 SP q)"
echo -ne "\e[6 q"
sleep 2

echo "Reset to default (BLINK_BLOCK)"
echo -ne "\e[1 q"
echo ""

# Test 7: Double-width Characters
echo "Test 7: Double-width Characters"
echo "ASCII: Hello World"
echo "CJK: 你好世界 こんにちは 안녕하세요"
echo "Emoji: 🌍 🚀 ⭐ 💻 🎉"
echo ""
sleep 2

# Test 8: Text Selection
echo "Test 8: Text Selection"
echo "Try to select this text with your mouse!"
echo "Then press Ctrl/Cmd+C to copy it to clipboard"
echo ""
sleep 2

# Final message
echo "=========================================="
echo "Tests Complete!"
echo "=========================================="
echo ""
echo "Visual Checklist:"
echo "✓ Colors rendered correctly (16 basic + 256-color palette)"
echo "✓ Text attributes visible (bold, dim, italic, underline, inverse)"
echo "✓ HIDDEN text is invisible"
echo "✓ BLINK animations working (slow 500ms, rapid 250ms)"
echo "✓ Cursor shapes changed correctly"
echo "✓ Cursor blinking for BLINK_* shapes"
echo "✓ Double-width characters display properly"
echo "✓ Text selection works (drag mouse to test)"
echo ""
