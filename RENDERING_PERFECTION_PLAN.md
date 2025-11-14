# JediTerm Compose - Rendering Perfection Plan

## Executive Summary

This document outlines a comprehensive, phased approach to make ProperTerminal.kt rendering **pixel-perfect** with the original TerminalPanel.java implementation. All 9 phases are prioritized, with clear implementation steps, code references, and time estimates.

**Current Status:** 16/22 features complete (73%)
**Remaining Work:** 6 features (~15-20 hours)
**Target:** 100% feature parity with original JediTerm

---

## Phase 1: BLINK Animation (SLOW_BLINK + RAPID_BLINK)

**Priority:** HIGH
**Complexity:** MEDIUM
**Time Estimate:** 3-4 hours
**Status:** Not Started

### Problem
- Text with SLOW_BLINK or RAPID_BLINK attributes don't animate
- Original TerminalPanel.java has blinking at 500ms intervals
- Currently text appears static

### Original Implementation Reference
**File:** `core/src/com/jediterm/terminal/ui/TerminalPanel.java`
- Lines 1474-1484: Blink timer implementation
- Lines 1305-1356: Character drawing with blink state check

```java
// TerminalPanel.java:1474-1484
private void setupBlinkTimer() {
  Timer timer = new Timer(500, e -> {
    myBlinkState = !myBlinkState;
    repaint();
  });
  timer.start();
}
```

### Implementation Plan

**Step 1.1: Add blink state management**
- Location: `ProperTerminal.kt` after line 54
- Add: `var blinkVisible by remember { mutableStateOf(true) }`
- Purpose: Toggle visibility every 500ms

**Step 1.2: Add blink timer with LaunchedEffect**
```kotlin
// After line 76 in ProperTerminal.kt
LaunchedEffect(Unit) {
    while (true) {
        delay(500)
        blinkVisible = !blinkVisible
    }
}
```

**Step 1.3: Detect SLOW_BLINK and RAPID_BLINK**
```kotlin
// After line 228 in rendering loop
val isSlowBlink = style.hasOption(JediTextStyle.Option.SLOW_BLINK)
val isRapidBlink = style.hasOption(JediTextStyle.Option.RAPID_BLINK)
val shouldBlink = isSlowBlink || isRapidBlink
```

**Step 1.4: Conditionally hide text when blinking**
```kotlin
// Update line 247 condition
if (char != ' ' && char != '\u0000' && !isHidden && !(shouldBlink && !blinkVisible)) {
    // Draw text
}
```

**Step 1.5: Handle RAPID_BLINK (250ms intervals)**
For rapid blink, use separate timer:
```kotlin
var rapidBlinkVisible by remember { mutableStateOf(true) }
LaunchedEffect(Unit) {
    while (true) {
        delay(250)
        rapidBlinkVisible = !rapidBlinkVisible
    }
}
```

### Testing
```bash
# Test slow blink
echo -e "\x1b[5mSlow Blink\x1b[0m"

# Test rapid blink
echo -e "\x1b[6mRapid Blink\x1b[0m"
```

### Success Criteria
- [ ] SLOW_BLINK text blinks at 500ms intervals
- [ ] RAPID_BLINK text blinks at 250ms intervals
- [ ] Blinking synchronized across all cells
- [ ] No performance degradation

---

## Phase 2: Text Selection Rendering

**Priority:** HIGH
**Complexity:** HIGH
**Time Estimate:** 4-5 hours
**Status:** Not Started

### Problem
- No visual feedback when text is selected
- Original has blue highlight overlay on selected text
- Mouse selection not implemented

### Original Implementation Reference
**File:** `core/src/com/jediterm/terminal/ui/TerminalPanel.java`
- Lines 1125-1170: Selection rendering
- Lines 879-920: Mouse selection handling

```java
// TerminalPanel.java:1125-1170
private void drawSelection(Graphics2D gfx) {
  TerminalSelection selection = myTerminalTextBuffer.getSelection();
  if (selection != null) {
    Pair<Point, Point> points = selection.pointsForRun(myTerminalTextBuffer.getWidth());
    // Draw selection background
    gfx.setColor(mySettingsProvider.getSelectionColor());
    // ... draw rectangles
  }
}
```

### Implementation Plan

**Step 2.1: Add selection state**
```kotlin
// After line 54
var selectionStart by remember { mutableStateOf<Pair<Int, Int>?>(null) }
var selectionEnd by remember { mutableStateOf<Pair<Int, Int>?>(null) }
```

**Step 2.2: Add mouse selection handlers**
```kotlin
// Add after onPointerEvent (line 143)
.pointerInput(Unit) {
    detectDragGestures(
        onDragStart = { offset ->
            val col = (offset.x / cellWidth).toInt()
            val row = (offset.y / cellHeight).toInt()
            selectionStart = Pair(col, row)
            selectionEnd = Pair(col, row)
        },
        onDrag = { _, offset ->
            val col = (offset.x / cellWidth).toInt()
            val row = (offset.y / cellHeight).toInt()
            selectionEnd = Pair(col, row)
        }
    )
}
```

**Step 2.3: Draw selection overlay**
```kotlin
// After line 244 (background drawing)
if (selectionStart != null && selectionEnd != null) {
    val (startCol, startRow) = selectionStart!!
    val (endCol, endRow) = selectionEnd!!

    // Check if current cell is in selection
    val isSelected = isInSelection(col, row, startCol, startRow, endCol, endRow)

    if (isSelected) {
        drawRect(
            color = Color(0xFF3399FF).copy(alpha = 0.3f),
            topLeft = Offset(x, y),
            size = Size(cellWidth, cellHeight)
        )
    }
}
```

**Step 2.4: Add selection helper function**
```kotlin
private fun isInSelection(
    col: Int, row: Int,
    startCol: Int, startRow: Int,
    endCol: Int, endRow: Int
): Boolean {
    // Normalize selection (handle reverse selection)
    val (minRow, minCol, maxRow, maxCol) = normalizeSelection(
        startCol, startRow, endCol, endRow
    )

    return when {
        row < minRow || row > maxRow -> false
        row == minRow && row == maxRow -> col in minCol..maxCol
        row == minRow -> col >= minCol
        row == maxRow -> col <= maxCol
        else -> true
    }
}
```

**Step 2.5: Copy selection to clipboard**
```kotlin
// Add keyboard shortcut
.onKeyEvent { keyEvent ->
    if (keyEvent.key == Key.C && keyEvent.isCtrlPressed) {
        copySelectionToClipboard()
        true
    } else {
        // existing key handling
    }
}
```

### Testing
- Click and drag to select text
- Verify blue highlight appears
- Ctrl+C copies selected text
- Double-click selects word
- Triple-click selects line

### Success Criteria
- [ ] Mouse drag creates selection
- [ ] Selection highlighted with blue overlay
- [ ] Copy to clipboard works
- [ ] Selection persists across redraws

---

## Phase 3: Cursor Blinking Animation

**Priority:** MEDIUM
**Complexity:** LOW
**Time Estimate:** 1-2 hours
**Status:** Not Started

### Problem
- BLINK_BLOCK, BLINK_UNDERLINE, BLINK_VERTICAL_BAR cursors don't blink
- Only show/hide logic exists, no animation
- STEADY variants work correctly

### Original Implementation Reference
**File:** `core/src/com/jediterm/terminal/ui/TerminalPanel.java`
- Lines 1214-1266: Cursor drawing with blink state

### Implementation Plan

**Step 3.1: Add cursor blink state**
```kotlin
// After line 71
var cursorBlinkVisible by remember { mutableStateOf(true) }
```

**Step 3.2: Add cursor blink timer**
```kotlin
// After line 76
LaunchedEffect(cursorShape) {
    if (cursorShape?.isBlinking() == true) {
        while (true) {
            delay(500)
            cursorBlinkVisible = !cursorBlinkVisible
        }
    }
}
```

**Step 3.3: Conditionally hide cursor when blinking**
```kotlin
// Update line 286 condition
if (cursorVisible && isFocused &&
    (cursorShape?.isBlinking() != true || cursorBlinkVisible)) {
    // Draw cursor
}
```

### Testing
```bash
# Set blinking cursor
echo -e "\x1b[1 q"  # BLINK_BLOCK
echo -e "\x1b[3 q"  # BLINK_UNDERLINE
echo -e "\x1b[5 q"  # BLINK_VERTICAL_BAR
```

### Success Criteria
- [ ] BLINK_* cursors blink at 500ms
- [ ] STEADY_* cursors never blink
- [ ] Blinking stops when focus lost

---

## Phase 4: Fine-tune Color Rendering

**Priority:** MEDIUM
**Complexity:** LOW
**Time Estimate:** 2-3 hours
**Status:** Not Started

### Problem
- Default colors hardcoded to White
- No integration with ColorPalette for 0-15 colors
- Theme support missing

### Original Implementation Reference
**File:** `core/src/com/jediterm/terminal/emulator/ColorPaletteImpl.java`
- Lines 10-50: Default color palette initialization
- Theme-aware color selection

### Implementation Plan

**Step 4.1: Create ColorPalette instance**
```kotlin
// After line 63
val colorPalette = remember {
    com.jediterm.terminal.emulator.ColorPaletteImpl()
}
```

**Step 4.2: Update convertTerminalColor for 0-15**
```kotlin
// Replace lines 327-343
if (colorIndex < 16) {
    // Use ColorPalette for theme support
    val color = colorPalette.getForegroundByColorIndex(colorIndex)
    return Color(
        red = color.red / 255f,
        green = color.green / 255f,
        blue = color.blue / 255f
    )
}
```

**Step 4.3: Handle default colors**
```kotlin
// Update line 319
if (terminalColor == null) {
    // Use default foreground from palette, not hardcoded white
    val defaultColor = colorPalette.getForegroundByColorIndex(7)
    return Color(
        red = defaultColor.red / 255f,
        green = defaultColor.green / 255f,
        blue = defaultColor.blue / 255f
    )
}
```

### Testing
- Compare colors 0-15 with original
- Test dark theme colors
- Verify default text color

### Success Criteria
- [ ] Colors 0-15 match original palette
- [ ] Default colors theme-aware
- [ ] Background/foreground distinction clear

---

## Phase 5: Hyperlink Detection and Styling

**Priority:** LOW
**Complexity:** MEDIUM
**Time Estimate:** 3-4 hours
**Status:** Not Started

### Problem
- URLs and file paths not detected
- No underline or color change on hover
- No click-to-open functionality

### Original Implementation Reference
**File:** `core/src/com/jediterm/terminal/ui/TerminalPanel.java`
- Lines 1400-1450: Hyperlink detection
- Mouse hover highlighting

### Implementation Plan

**Step 5.1: Add hyperlink detection regex**
```kotlin
private val URL_REGEX = Regex(
    """https?://[^\s]+|file://[^\s]+|www\.[^\s]+"""
)
```

**Step 5.2: Detect hyperlinks in text**
```kotlin
// After getting line text
val lineText = buildString {
    for (i in 0 until width) {
        append(line.charAt(i))
    }
}
val hyperlinks = URL_REGEX.findAll(lineText)
```

**Step 5.3: Style hyperlink cells**
```kotlin
// In rendering loop
val isHyperlink = hyperlinks.any {
    col in it.range
}

if (isHyperlink) {
    fgColor = Color(0xFF0000EE) // Blue
    isUnderline = true
}
```

**Step 5.4: Add click handler**
```kotlin
.pointerInput(Unit) {
    detectTapGestures { offset ->
        val col = (offset.x / cellWidth).toInt()
        val row = (offset.y / cellHeight).toInt()

        // Check if clicked on hyperlink
        val url = findHyperlinkAt(col, row)
        if (url != null) {
            openInBrowser(url)
        }
    }
}
```

### Success Criteria
- [ ] URLs underlined in blue
- [ ] File paths detected
- [ ] Click opens in browser
- [ ] Hover changes cursor

---

## Phase 6: Rendering Performance Optimization

**Priority:** MEDIUM
**Complexity:** MEDIUM
**Time Estimate:** 2-3 hours
**Status:** Not Started

### Problem
- Character-by-character rendering inefficient
- Full buffer redrawn on every change
- No dirty region tracking

### Implementation Plan

**Step 6.1: Add dirty region tracking**
```kotlin
var dirtyRegion by remember {
    mutableStateOf<Rect?>(null)
}
```

**Step 6.2: Only redraw changed cells**
```kotlin
// Check if cell changed since last draw
if (!cellNeedsRedraw(col, row, char, style)) {
    continue
}
```

**Step 6.3: Batch similar characters**
```kotlin
// Instead of drawing char by char, batch consecutive chars with same style
val batch = buildString {
    while (col < width && hasSameStyle(col)) {
        append(line.charAt(col))
        col++
    }
}
// Draw entire batch at once
drawText(textMeasurer, batch, ...)
```

### Success Criteria
- [ ] 60 FPS maintained
- [ ] CPU usage < 5% idle
- [ ] Smooth scrolling
- [ ] No lag on rapid output

---

## Phase 7: Strikethrough Support

**Priority:** LOW
**Complexity:** LOW
**Time Estimate:** 30 minutes
**Status:** Not Started

### Problem
- No CROSSED_OUT / STRIKETHROUGH attribute support
- Line through middle of text missing

### Implementation Plan

**Step 7.1: Detect CROSSED_OUT option**
```kotlin
// After line 228
val isStrikethrough = style.hasOption(JediTextStyle.Option.CROSSED_OUT)
```

**Step 7.2: Draw strikethrough line**
```kotlin
// After underline drawing (line 270)
if (isStrikethrough) {
    val strikeY = y + cellHeight * 0.5f  // Middle of cell
    drawLine(
        color = fgColor,
        start = Offset(x, strikeY),
        end = Offset(x + cellWidth, strikeY),
        strokeWidth = 1f
    )
}
```

### Success Criteria
- [ ] Strikethrough line drawn at vertical middle
- [ ] Line color matches text color
- [ ] Works with other attributes

---

## Phase 8: Comprehensive Testing

**Priority:** HIGH
**Complexity:** LOW
**Time Estimate:** 2-3 hours
**Status:** Not Started

### Testing Plan

**Test 8.1: Color Palette Test**
```bash
# Test all 256 colors
for i in {0..255}; do
    echo -ne "\x1b[38;5;${i}m█\x1b[0m"
    if [ $((($i + 1) % 16)) -eq 0 ]; then echo; fi
done
```

**Test 8.2: Attributes Test**
```bash
echo -e "\x1b[1mBold\x1b[0m"
echo -e "\x1b[3mItalic\x1b[0m"
echo -e "\x1b[4mUnderline\x1b[0m"
echo -e "\x1b[5mBlink\x1b[0m"
echo -e "\x1b[7mInverse\x1b[0m"
echo -e "\x1b[8mHidden\x1b[0m"
echo -e "\x1b[9mStrikethrough\x1b[0m"
echo -e "\x1b[2mDim\x1b[0m"
```

**Test 8.3: Double-width Test**
```bash
echo "你好世界 こんにちは 😀🎉"
```

**Test 8.4: Cursor Shapes Test**
```bash
echo -e "\x1b[0 q Default"
echo -e "\x1b[1 q Blinking Block"
echo -e "\x1b[2 q Steady Block"
echo -e "\x1b[3 q Blinking Underline"
echo -e "\x1b[4 q Steady Underline"
echo -e "\x1b[5 q Blinking Bar"
echo -e "\x1b[6 q Steady Bar"
```

**Test 8.5: Real Applications**
```bash
# Test with real apps
vim
htop
less
nano
tmux
```

### Success Criteria
- [ ] All 256 colors display correctly
- [ ] All attributes work
- [ ] CJK/emoji render correctly
- [ ] All cursor shapes work
- [ ] Real apps display properly

---

## Phase 9: Pixel-Perfect Comparison

**Priority:** MEDIUM
**Complexity:** LOW
**Time Estimate:** 1-2 hours
**Status:** Not Started

### Comparison Plan

**Step 9.1: Side-by-side screenshots**
- Run original TerminalPanel.java
- Run ProperTerminal.kt
- Compare visually

**Step 9.2: Automated pixel comparison**
```kotlin
// Compare screenshots programmatically
fun compareRendering(original: BufferedImage, new: BufferedImage): Double {
    // Calculate pixel difference percentage
}
```

**Step 9.3: Benchmark performance**
```kotlin
// Measure FPS, CPU, memory
fun benchmarkRendering() {
    // 1000 lines rapid output
    // Measure frame time
    // Compare with original
}
```

### Success Criteria
- [ ] < 1% visual difference
- [ ] Performance within 10% of original
- [ ] All features work identically

---

## Summary Timeline

| Phase | Feature | Priority | Time | Dependencies |
|-------|---------|----------|------|--------------|
| 1 | BLINK Animation | HIGH | 3-4h | None |
| 2 | Text Selection | HIGH | 4-5h | None |
| 3 | Cursor Blink | MEDIUM | 1-2h | Phase 1 |
| 4 | Color Tuning | MEDIUM | 2-3h | None |
| 5 | Hyperlinks | LOW | 3-4h | Phase 2 |
| 6 | Performance | MEDIUM | 2-3h | All phases |
| 7 | Strikethrough | LOW | 30m | None |
| 8 | Testing | HIGH | 2-3h | All phases |
| 9 | Comparison | MEDIUM | 1-2h | Phase 8 |

**Total Estimated Time:** 19-27 hours
**Recommended Order:** 1 → 3 → 4 → 7 → 2 → 5 → 6 → 8 → 9

---

## Current Feature Matrix

| Feature | Status | Priority | Phase |
|---------|--------|----------|-------|
| BOLD | ✅ Complete | - | - |
| ITALIC | ✅ Complete | - | - |
| UNDERLINED | ✅ Complete | - | - |
| DIM | ✅ Complete | - | - |
| INVERSE | ✅ Complete | - | - |
| HIDDEN | ✅ Complete | - | - |
| SLOW_BLINK | ❌ Missing | HIGH | 1 |
| RAPID_BLINK | ❌ Missing | HIGH | 1 |
| CROSSED_OUT | ❌ Missing | LOW | 7 |
| 256-Color | ✅ Complete | - | - |
| Double-width | ✅ Complete | - | - |
| Cursor Shapes | ✅ Complete | - | - |
| Cursor Blink | ❌ Missing | MEDIUM | 3 |
| Text Selection | ❌ Missing | HIGH | 2 |
| Hyperlinks | ❌ Missing | LOW | 5 |
| Scrollback | ✅ Complete | - | - |
| Performance | ⚠️ Needs Work | MEDIUM | 6 |

**Progress:** 11/17 features = 65% complete
**Target:** 17/17 features = 100% complete

---

## Getting Started

### Immediate Next Steps (Priority Order)

1. **START HERE:** Phase 1 - BLINK Animation (3-4 hours)
   - Most visible user-facing feature
   - Blocks cursor blink (Phase 3)

2. **Phase 3:** Cursor Blinking (1-2 hours)
   - Quick win after Phase 1
   - Reuses blink timer logic

3. **Phase 4:** Color Tuning (2-3 hours)
   - Important for visual accuracy
   - Independent, can be done anytime

4. **Phase 7:** Strikethrough (30 minutes)
   - Quick win
   - Simple addition

Then tackle the complex features (Selection, Hyperlinks) and optimization.

---

## Notes

- **Blink Animation:** Use separate timers for SLOW (500ms) and RAPID (250ms)
- **Selection:** Consider using TerminalTextBuffer.getSelection() for consistency
- **Performance:** Profile before optimizing - may not be needed
- **Testing:** Set up automated tests early to catch regressions

**Last Updated:** 2025-11-14
**Document Version:** 1.0
**Author:** Development Team
