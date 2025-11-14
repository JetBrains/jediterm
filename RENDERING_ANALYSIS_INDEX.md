# JediTerm Rendering Implementation Analysis - Index

## Documents Created

This analysis compares the Compose-based `ProperTerminal.kt` with the production Swing-based `TerminalPanel.java` implementation.

### 1. RENDERING_GAPS_SUMMARY.md (Quick Reference)
**Best for:** Quick overview of what's missing
- Quick reference tables for all features
- Severity-ranked critical issues
- Line-by-line code comparisons
- Testing recommendations
- Summary statistics

**Read this if:** You want a fast 5-minute summary

### 2. RENDERING_COMPARISON.txt (Detailed Analysis)
**Best for:** Comprehensive deep-dive
- 10 critical issues with detailed explanations
- Complete code listings from both implementations
- Exactly what should be done to fix each issue
- Line number references for all source files
- Feature comparison table with status for each

**Read this if:** You need to understand and fix the issues

---

## Executive Summary

### Current Status
ProperTerminal.kt has **SEVERE GAPS** in rendering support:

| Category | Implemented | Missing |
|----------|-------------|---------|
| Text Attributes | 2/8 (25%) | UNDERLINE, DIM, INVERSE, SLOW_BLINK, RAPID_BLINK, HIDDEN |
| Color Support | ~30% | 256-color palette, grayscale, defaults, config |
| Cursor Shapes | 1/3 (33%) | Underline, vertical bar |
| Advanced Features | 0% | Blinking, selection, hyperlinks |

### Critical Issues (Ranked by Severity)

1. **256-COLOR PALETTE BROKEN** - Colors 16-255 all render as WHITE
2. **BLINKING TEXT NOT IMPLEMENTED** - No animation for SLOW_BLINK/RAPID_BLINK
3. **UNDERLINE NOT DRAWN** - Missing text decoration completely
4. **DIM TEXT NOT HANDLED** - Should be 50% brightness, shows full brightness
5. **INVERSE NOT APPLIED** - Colors not swapped for inverse video
6. **HIDDEN TEXT VISIBLE** - Security issue: passwords would be visible
7. **LIMITED CURSOR SHAPES** - Only block cursor, no underline/bar
8. **NO CURSOR BLINKING** - Cursor doesn't animate
9. **SELECTION MISSING** - No visual feedback for selected text
10. **HYPERLINKS MISSING** - URLs not styled differently

---

## Key Files Referenced

### ProperTerminal.kt
**Location:** `/Users/kshivang/Development/jeditermKt/compose-ui/src/desktopMain/kotlin/org/jetbrains/jediterm/compose/demo/ProperTerminal.kt`

Critical sections:
- **Lines 179-247:** Character rendering loop (missing attribute checks)
- **Lines 222-231:** Background drawing (only checks double-width)
- **Lines 232-233:** Only checks BOLD/ITALIC
- **Lines 261-269:** Cursor rendering (basic block only)
- **Lines 318-354:** Color conversion (hardcoded colors, ignores palette)

### TerminalPanel.java (Reference Implementation)
**Location:** `/Users/kshivang/Development/jeditermKt/ui/src/com/jediterm/terminal/ui/TerminalPanel.java`

Reference sections:
- **Lines 779-871:** Complete rendering with all features
- **Lines 1015-1047:** Proper color handling with palette
- **Lines 1214-1266:** Cursor rendering (3 shapes, blinking)
- **Lines 1305-1356:** Character drawing with ALL attributes
- **Lines 1351-1355:** Underline rendering
- **Lines 1474-1484:** DIM color averaging

### Supporting Files
- **BlinkingTextTracker.java** - Blinking mechanism
- **TextStyle.java** - Attribute definitions (8 options)
- **TerminalColor.java** - Color model
- **ColorPalette.java** - 256-color palette

---

## Critical Issue Deep-Dives

### Issue #1: 256-Color Palette Broken

**What happens:**
- Terminal sends: `ESC[38;5;196m` (set foreground to color 196 - bright red)
- ProperTerminal renders: WHITE text
- Should render: Bright red text

**Root cause:**
```kotlin
// ProperTerminal.kt lines 318-354
when (terminalColor.colorIndex) {
  0 -> Black
  1 -> Red
  // ... 14 hardcoded colors ...
  else -> Color.White  // ← EVERYTHING else becomes white!
}
```

**Correct approach:**
```kotlin
return getPalette().getForeground(terminalColor)
```

**Impact:** All extended color support unusable, custom themes broken

---

### Issue #2: Blinking Text Not Implemented

**What should happen:**
- Terminal sends: `ESC[5m` (slow blink) or `ESC[6m` (rapid blink)
- Text appears/disappears ~500ms or ~200ms respectively

**Current:** No blinking at all, text always visible

**Implementation needed:**
1. Track time with `LaunchedEffect` + `System.currentTimeMillis()`
2. Toggle state every 200-500ms
3. When in "off" phase, invert style to hide text
4. Trigger recomposition on state change

**Reference:** See `/BlinkingTextTracker.java` for time tracking logic

---

### Issue #3: Text Attributes Not Applied

**8 TextStyle.Option attributes:**
- ✓ BOLD - Implemented (lines 232-233 checks it)
- ✓ ITALIC - Implemented (lines 232-233 checks it)
- ✗ UNDERLINED - Not checked anywhere
- ✗ DIM - Not checked anywhere
- ✗ INVERSE - Not checked anywhere
- ✗ SLOW_BLINK - Not checked anywhere
- ✗ RAPID_BLINK - Not checked anywhere
- ✗ HIDDEN - Not checked anywhere

**Fix needed:** Add checks after line 247:
```kotlin
if (style.hasOption(JediTextStyle.Option.UNDERLINED)) {
  // Draw underline
}
if (style.hasOption(JediTextStyle.Option.DIM)) {
  // Reduce brightness
}
if (style.hasOption(JediTextStyle.Option.INVERSE)) {
  // Swap colors
}
// etc...
```

---

### Issue #4: Cursor Only Supports Block Shape

**Three cursor shapes supported by terminals:**

| Shape | Used for | Rendering |
|-------|----------|-----------|
| Block | Default text mode | Filled rectangle (or outline when blinking off) |
| Underline | Insert mode | Horizontal line at bottom |
| Vertical Bar | Some editors | Thin vertical line on left |

**Current:** Only block cursor at hardcoded white color

**Needed:**
- Detect cursor shape from terminal settings
- Draw appropriate shape based on setting
- Support blinking variants (BLINK_BLOCK, STEADY_BLOCK, etc.)
- Use cursor color from style, not hardcoded white

---

## How to Use This Analysis

### For Understanding Current Issues
1. Read **RENDERING_GAPS_SUMMARY.md** first (10 mins)
2. Pick an issue to understand deeper
3. Read relevant section in **RENDERING_COMPARISON.txt**
4. Look at reference code in TerminalPanel.java

### For Fixing Issues
1. Pick highest-priority issue from severity ranking
2. Read detailed explanation in RENDERING_COMPARISON.txt
3. Study reference implementation in TerminalPanel.java
4. See "What should be done" section for fix approach
5. Implement in ProperTerminal.kt

### Recommended Fix Order

1. **Fix ColorPalette** (1-2 hours)
   - Replace hardcoded colors with palette lookup
   - Fix 256-color and default colors

2. **Implement INVERSE** (30 mins)
   - Swap foreground/background when option set
   - Handle null colors with defaults

3. **Draw Underlines** (30 mins)
   - Check UNDERLINED option
   - Draw horizontal line at bottom

4. **Implement DIM** (30 mins)
   - Detect DIM option
   - Average foreground with background color

5. **Implement Blinking** (2-3 hours)
   - Create time-tracking state
   - Invert style during "off" phase
   - Trigger recomposition

6. **Cursor Improvements** (2-3 hours)
   - Add underline/bar shapes
   - Add blinking animation
   - Use proper cursor colors

---

## Quick Reference: Code Locations

### What to Change
- **ProperTerminal.kt:**
  - Lines 179-247: Character rendering
  - Lines 261-269: Cursor rendering
  - Lines 318-354: Color conversion

### What to Reference
- **TerminalPanel.java:**
  - Lines 779-871: Complete rendering logic
  - Lines 1305-1356: Character drawing with attributes
  - Lines 1214-1266: Cursor rendering

- **BlinkingTextTracker.java:** Entire file - blinking implementation

- **TextStyle.java, TerminalColor.java, ColorPalette.java:** Data structures

---

## Summary

**Overall Compatibility: ~30%**

Only basic text rendering + 24-bit RGB colors work correctly.

Missing implementations affect:
- Terminal color schemes (256-color palette completely broken)
- Text styling (5 of 8 attributes missing)
- User interactions (cursor customization, blinking)
- Advanced features (selection, hyperlinks)

**Effort to fix:** ~10-15 hours for comprehensive implementation

**Critical path:** Fix colors → INVERSE → Underline → DIM → Blinking

---

## Files Generated

1. **RENDERING_ANALYSIS_INDEX.md** - This file
2. **RENDERING_GAPS_SUMMARY.md** - Quick reference (357 lines)
3. **RENDERING_COMPARISON.txt** - Detailed analysis (566 lines)

Generated: 2024-11-13
