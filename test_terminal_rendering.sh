#!/bin/bash
# Comprehensive JediTerm Compose Terminal Rendering Test

echo "=========================================="
echo "JediTerm Compose Terminal Rendering Tests"
echo "=========================================="
echo ""

# Test 1: Basic Colors (16 colors)
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

# Test 2: 256-Color Palette
echo "Test 2: 256-Color Palette (colors 16-255)"
echo "6x6x6 color cube (216 colors):"
for i in {16..231}; do
    echo -ne "\e[38;5;${i}m█\e[0m"
    if [ $(((i - 15) % 36)) -eq 0 ]; then echo ""; fi
done
echo ""
echo "Grayscale ramp (24 shades):"
for i in {232..255}; do
    echo -ne "\e[38;5;${i}m█\e[0m"
done
echo ""
echo ""

# Test 3: Text Attributes
echo "Test 3: Text Attributes"
echo -e "\e[1mBOLD text\e[0m"
echo -e "\e[2mDIM text\e[0m"
echo -e "\e[3mITALIC text\e[0m"
echo -e "\e[4mUNDERLINED text\e[0m"
echo -e "\e[7mINVERSE text\e[0m"
echo -e "\e[8mHIDDEN text (should be invisible)\e[0m <- HIDDEN"
echo -e "\e[5mSLOW BLINK text\e[0m (500ms)"
echo -e "\e[6mRAPID BLINK text\e[0m (250ms)"
echo ""

# Test 4: Combined Attributes
echo "Test 4: Combined Attributes"
echo -e "\e[1;4;31mBOLD + UNDERLINE + RED\e[0m"
echo -e "\e[2;3;34mDIM + ITALIC + BLUE\e[0m"
echo -e "\e[1;7;32mBOLD + INVERSE + GREEN\e[0m"
echo ""

# Test 5: Cursor Shapes (DECSCUSR)
echo "Test 5: Cursor Shapes"
echo "Setting cursor to BLINK_BLOCK (ESC [ 1 SP q)"
echo -ne "\e[1 q"
sleep 2
echo "Setting cursor to STEADY_BLOCK (ESC [ 2 SP q)"
echo -ne "\e[2 q"
sleep 2
echo "Setting cursor to BLINK_UNDERLINE (ESC [ 3 SP q)"
echo -ne "\e[3 q"
sleep 2
echo "Setting cursor to STEADY_UNDERLINE (ESC [ 4 SP q)"
echo -ne "\e[4 q"
sleep 2
echo "Setting cursor to BLINK_VERTICAL_BAR (ESC [ 5 SP q)"
echo -ne "\e[5 q"
sleep 2
echo "Setting cursor to STEADY_VERTICAL_BAR (ESC [ 6 SP q)"
echo -ne "\e[6 q"
sleep 2
echo "Resetting cursor to default"
echo -ne "\e[1 q"
echo ""

# Test 6: Double-width Characters
echo "Test 6: Double-width Characters"
echo "ASCII: Hello World"
echo "CJK: 你好世界 こんにちは 안녕하세요"
echo "Emoji: 🌍 🚀 ⭐ 💻 🎉"
echo ""

# Test 7: Complex Rendering
echo "Test 7: Complex Combined Rendering"
echo -e "\e[1;38;5;208m█\e[0m\e[38;5;209m█\e[0m\e[38;5;210m█\e[0m GRADIENT with BOLD"
echo -e "\e[5;38;5;46mBLINKING\e[0m \e[4;38;5;51mUNDERLINED\e[0m \e[7;38;5;201mINVERSE\e[0m"
echo ""

echo "=========================================="
echo "Tests Complete!"
echo "=========================================="
echo ""
echo "Visual Verification Checklist:"
echo "✓ All colors render correctly (especially 16-255)"
echo "✓ Text attributes visible (bold, dim, italic, underline, inverse)"
echo "✓ HIDDEN text is invisible"
echo "✓ SLOW_BLINK and RAPID_BLINK are animating"
echo "✓ Cursor shapes change correctly"
echo "✓ Double-width characters display properly"
echo "✓ Text selection works (drag mouse to select)"
echo "✓ Ctrl/Cmd+C copies selected text"
echo "✓ Cursor blinks for BLINK_* shapes"
echo ""
