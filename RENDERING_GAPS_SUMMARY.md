# ProperTerminal Rendering - Critical Gaps Summary

## Quick Reference: What's Missing

### Text Attributes (from TextStyle.Option enum)

| Attribute | Status | Impact | Example |
|-----------|--------|--------|---------|
| **BOLD** | ✓ Works | - | `\x1b[1m` bold text |
| **ITALIC** | ✓ Works | - | `\x1b[3m` italic text |
| **UNDERLINED** | ✗ BROKEN | Text has no underline | `\x1b[4m` underlined text |
| **DIM** | ✗ BROKEN | Text appears full brightness | `\x1b[2m` dim text (50% brightness) |
| **INVERSE** | ✗ BROKEN | Colors not swapped | `\x1b[7m` inverse colors |
| **SLOW_BLINK** | ✗ BROKEN | No blinking | `\x1b[5m` blinking ~500ms |
| **RAPID_BLINK** | ✗ BROKEN | No blinking | `\x1b[6m` blinking ~200ms |
| **HIDDEN** | ✗ BROKEN | Text visible (security issue!) | `\x1b[8m` hidden text (passwords) |

### Color Handling

| Feature | Status | Impact | Notes |
|---------|--------|--------|-------|
| **16 ANSI Colors** | ~ Partial | Hardcoded instead of palette-based | Colors 0-15 hardcoded |
| **256-Color Palette (16-231)** | ✗ BROKEN | All render as WHITE | Xterm extended colors unusable |
| **Grayscale (232-255)** | ✗ BROKEN | All render as WHITE | Terminal grayscale broken |
| **24-bit RGB** | ✓ Works | - | Full RGB color support |
| **Default Colors** | ✗ BROKEN | Always white | Should use StyleState defaults |
| **ColorPalette Config** | ✗ BROKEN | Ignored completely | Custom themes don't work |

### Cursor Rendering

| Feature | Status | Impact | Notes |
|---------|--------|--------|-------|
| **Block Cursor** | ~ Partial | Works but no state management | Hardcoded white color |
| **Underline Cursor** | ✗ MISSING | Can't use underline style | Not implemented |
| **Vertical Bar Cursor** | ✗ MISSING | Can't use bar style | Not implemented |
| **Cursor Blinking** | ✗ MISSING | Cursor doesn't blink | No animation |
| **Cursor States** | ✗ MISSING | No showing/hidden states | Always visible |
| **Cursor Color** | ✗ BROKEN | Hardcoded white | Should match terminal style |

### Advanced Features

| Feature | Status | Impact | Notes |
|---------|--------|--------|-------|
| **Text Selection** | ✗ MISSING | No visual selection feedback | Users can't see what they selected |
| **Hyperlink Styling** | ✗ MISSING | URLs not highlighted | Links not visually distinct |
| **Double-Width Characters** | ✓ Works | - | Properly handled |
| **Input Method Editing** | ~ Partial | Unknown | May need checking |

---

## Critical Issues by Severity

### SEVERITY 1: Visual Corruption (Colors)

**Issue: Colors 16-255 All Render as WHITE**

When terminal sends: `CSI 38;5;196m` (bright red in 256-color mode)
Result in ProperTerminal: White text
Expected: Bright red text

Impact: All extended color support broken, custom color themes unusable.

**File:** `/Users/kshivang/Development/jeditermKt/compose-ui/src/desktopMain/kotlin/org/jetbrains/jediterm/compose/demo/ProperTerminal.kt`
**Lines:** 318-354
**Function:** `convertTerminalColor()`

Current code:
```kotlin
return when {
  terminalColor.isIndexed -> {
    when (terminalColor.colorIndex) {
      0 -> Color(0xFF000000) // Black
      // ... 14 more hardcoded colors ...
      else -> Color.White  // ← PROBLEM: Returns white for colors 16-255
    }
  }
```

Should be:
```kotlin
if (terminalColor.isIndexed) {
  return getPalette().getForeground(terminalColor)  // Use actual palette
}
```

---

### SEVERITY 2: Missing Text Decorations

**Issue: Underline Not Drawn**

When terminal sends: `CSI 4m` (underline)
Result in ProperTerminal: Normal text
Expected: Text with underline

**Lines:** 222-246 in ProperTerminal.kt

Fix needed: Add check after drawing text:
```kotlin
if (style.hasOption(JediTextStyle.Option.UNDERLINED)) {
  val lineY = y * cellHeight + cellHeight - 2
  drawLine(
    color = fgColor,
    start = Offset(x * cellWidth, lineY),
    end = Offset((x + charCount) * cellWidth, lineY),
    strokeWidth = 1f
  )
}
```

---

### SEVERITY 3: Missing Text Blinking

**Issue: Text with Blink Attributes Doesn't Blink**

When terminal sends: `CSI 5m` (slow blink) or `CSI 6m` (rapid blink)
Result in ProperTerminal: Static text
Expected: Text appears/disappears ~500ms or ~200ms

**Current State:** No blinking infrastructure at all

Required implementation:
1. Create a state variable tracking blink phase
2. Use LaunchedEffect with timer to toggle state
3. When in "off" phase, hide blinking text by inverting style

---

### SEVERITY 4: INVERSE Colors Not Swapped

**Issue: INVERSE Option Ignored**

When terminal sends: `CSI 7m` (inverse video)
Result in ProperTerminal: Normal foreground/background
Expected: Foreground and background colors swapped

**Lines:** 222-231 in ProperTerminal.kt

Current code never checks:
```kotlin
style.hasOption(JediTextStyle.Option.INVERSE)
```

Should swap colors when inverse is set.

---

### SEVERITY 5: DIM Text Not Muted

**Issue: DIM Text Appears at Full Brightness**

When terminal sends: `CSI 2m` (dim)
Result in ProperTerminal: Full brightness text
Expected: Text at ~50% brightness (average of fg and bg)

**Lines:** 235-247 in ProperTerminal.kt

Fix needed in color calculation:
```kotlin
var fgColor = convertTerminalColor(style.foreground)
if (style.hasOption(JediTextStyle.Option.DIM)) {
  val bgColor = convertTerminalColor(style.background)
  fgColor = Color(
    red = (fgColor.red + bgColor.red) / 2,
    green = (fgColor.green + bgColor.green) / 2,
    blue = (fgColor.blue + bgColor.blue) / 2,
    alpha = fgColor.alpha
  )
}
```

---

### SEVERITY 6: Hidden Text Visible (Security Issue)

**Issue: Text with HIDDEN Attribute Shows Normally**

When terminal sends: `CSI 8m` (hidden)
Result in ProperTerminal: Text visible
Expected: Text hidden (for passwords, tokens)

Common in terminal UI applications for password input.

**Current Impact:** Password fields would have text visible in ProperTerminal.

---

### SEVERITY 7: Limited Cursor Shapes

**Issue: Only Block Cursor Supported**

Terminal supports 3 cursor shapes:
- Block (filled rectangle) - ✓ Implemented
- Underline (horizontal line) - ✗ Missing
- Vertical Bar (thin vertical line) - ✗ Missing

**Lines:** 261-269 in ProperTerminal.kt

Current code:
```kotlin
drawRect(
  color = Color.White.copy(alpha = 0.5f),  // ← Hardcoded white
  topLeft = Offset(x, y),
  size = Size(cellWidth, cellHeight)
)
```

Issues:
- Only supports one shape (block)
- Cursor color hardcoded to white
- No blinking animation
- No state management (showing vs hidden)

---

### SEVERITY 8: No Cursor Blinking

**Issue: Cursor Doesn't Blink**

Expected: Cursor toggles visibility for visual feedback
Result: Cursor always shows (if focused)

Required: Track cursor blink state with timer, toggle visibility.

---

## File Locations

**Files with issues:**
- `/Users/kshivang/Development/jeditermKt/compose-ui/src/desktopMain/kotlin/org/jetbrains/jediterm/compose/demo/ProperTerminal.kt`

**Reference implementations to study:**
- `/Users/kshivang/Development/jeditermKt/ui/src/com/jediterm/terminal/ui/TerminalPanel.java` (Main rendering)
- `/Users/kshivang/Development/jeditermKt/ui/src/com/jediterm/terminal/ui/BlinkingTextTracker.java` (Blinking)
- `/Users/kshivang/Development/jeditermKt/core/src/com/jediterm/terminal/TextStyle.java` (Attributes)
- `/Users/kshivang/Development/jeditermKt/core/src/com/jediterm/terminal/TerminalColor.java` (Colors)

---

## Line-by-Line Comparison

### Color Conversion

**ProperTerminal.kt (lines 318-354):**
```kotlin
private fun convertTerminalColor(terminalColor: TerminalColor?): Color {
  if (terminalColor == null) return Color.White
  
  return when {
    terminalColor.isIndexed -> {
      when (terminalColor.colorIndex) {
        0 -> Color(0xFF000000)
        1 -> Color(0xFFCD0000)
        // ... hardcoded colors ...
        else -> Color.White  // ← Colors 16-255 become white
      }
    }
    else -> {
      val color = terminalColor.toColor()
      Color(red = color.red / 255f, green = color.green / 255f, blue = color.blue / 255f)
    }
  }
}
```

**TerminalPanel.java (lines 1025-1047):**
```java
private Color getForeground(TextStyle style) {
  TerminalColor foreground = style.getForeground();
  return foreground != null ? toForeground(foreground) : getWindowForeground();
}

private Color toForeground(TerminalColor terminalColor) {
  if (terminalColor.isIndexed()) {
    return getPalette().getForeground(terminalColor);  // ← Uses actual palette
  }
  return terminalColor.toColor();
}
```

**Key difference:** Uses ColorPalette for indexed colors instead of hardcoding.

---

## Recommendations

### Immediate (Required for Basic Functionality)
1. Fix 256-color palette → Use ColorPalette instead of hardcoding
2. Implement INVERSE color swapping
3. Draw underlines for UNDERLINED attribute
4. Calculate muted colors for DIM attribute

### High Priority (Common Use Cases)
5. Implement text blinking (SLOW_BLINK, RAPID_BLINK)
6. Implement HIDDEN text support
7. Support underline and bar cursor shapes
8. Fix default color handling (use StyleState)

### Medium Priority (Polish)
9. Implement cursor blinking animation
10. Implement selection rendering
11. Add hyperlink highlighting
12. Respect ColorPalette configuration

### Low Priority (Enhancement)
13. Cache rendered glyphs for performance
14. Support strikethrough (if added to TextStyle)
15. Support other text decorations

---

## Testing Recommendations

After fixes, test with:

```bash
# Color test (should show different colors, not all white)
for i in {16..255}; do echo -ne "\x1b[38;5;${i}mColor $i\x1b[0m "; done

# Underline test (should show underline)
echo -e "\x1b[4mUnderlined text\x1b[0m"

# Dim test (should show dimmed text)
echo -e "\x1b[2mDim text\x1b[0m"

# Inverse test (should show swapped colors)
echo -e "\x1b[7mInverse video\x1b[0m"

# Blinking test (should see blinking text ~500ms)
echo -e "\x1b[5mBlinking text\x1b[0m"

# Hidden test (should NOT see text)
echo -e "\x1b[8mHidden text\x1b[0m"

# Cursor shape test (depends on terminal app setting cursor shape)
# Most terminal apps send CSI ? 25 h (block), CSI ? 3 h (underline), CSI ? 5 h (bar)
```

---

## Summary Statistics

- **Total TextStyle Options:** 8
- **Implemented Properly:** 2 (BOLD, ITALIC)
- **Partially Implemented:** 1 (INVERSE - no color swap)
- **Not Implemented:** 5 (UNDERLINED, DIM, SLOW_BLINK, RAPID_BLINK, HIDDEN)

- **Color Features Supported:** ~3 (24-bit RGB, partial 16-color)
- **Color Features Broken:** ~4 (256-color palette, grayscale, defaults, config)

- **Cursor Shapes Supported:** 1 (block)
- **Cursor Shapes Missing:** 2 (underline, bar)
- **Cursor Features Missing:** 2 (blinking, state management)

**Overall Compatibility: ~30%** (only basic text + 24-bit RGB works)

