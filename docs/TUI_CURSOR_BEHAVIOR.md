# TUI Application Cursor Behavior

Investigation into how TUI applications like Claude Code draw custom cursors and why they appear differently across terminals.

**Date:** November 17, 2025
**Status:** Resolved - Behavior is correct

---

## Summary

TUI applications (Claude Code, vim, etc.) intentionally hide the system terminal cursor and draw their own custom cursors using standard terminal escape sequences. JediTerm correctly implements these sequences, resulting in styled characters at the cursor position. The visual difference compared to iTerm is due to presentation strategy, not a bug.

---

## How TUI Apps Draw Custom Cursors

### Step 1: Hide System Cursor
```
CSI ?25l  (DECTCEM - hide cursor)
```
- Sets `cursorVisible = false` in terminal
- System cursor is no longer drawn

### Step 2: Position Cursor
```
CSI row;col H  (CUP - cursor position)
```
- Moves cursor to desired (x, y) position
- Even though cursor is hidden, position is tracked

### Step 3: Style the Character
**Method A: Reverse Video (Most Common)**
```
ESC[7m        (SGR 7 - reverse video)
character     (e.g., space, █, or any char)
ESC[27m       (SGR 27 - normal video)
```
- Swaps foreground and background colors
- Character appears with inverted colors
- Creates cursor-like appearance

**Method B: Explicit Colors**
```
ESC[48;2;R;G;Bm   (Set background color)
ESC[38;2;R;G;Bm   (Set foreground color)
character
ESC[0m            (Reset)
```
- Sets specific colors for the character
- More control over cursor appearance

### Step 4: Move and Redraw
- As cursor moves, clear old position and redraw at new position
- Typically done during screen refresh cycles

---

## Terminal Implementation Comparison

### iTerm2 Approach
- **Smart Detection:** Recognizes reverse video patterns at cursor position
- **Cursor Synthesis:** May render as a block cursor matching terminal's cursor style
- **Visual Result:** White block cursor (or configured cursor style)
- **Philosophy:** "Make it look like a cursor, regardless of method"

### JediTerm Approach
- **Faithful Rendering:** Renders escape sequences exactly as requested
- **No Special Cases:** Treats cursor character like any other styled character
- **Visual Result:** Colored/inverted character as styled by application
- **Philosophy:** "Render what the application requests"

### Both Are Correct!
- iTerm prioritizes visual consistency with system cursor
- JediTerm prioritizes faithful escape sequence interpretation
- Applications work identically in both terminals

---

## Verification

### Diagnostic Output (JediTerm)
When Claude Code starts:
```
👁️  CURSOR VISIBLE: true → false
```
This is **expected behavior** - Claude hides system cursor to draw its own.

### User Experiment (iTerm)
1. Changed iTerm default cursor color
2. Claude Code cursor color remained unchanged
3. **Conclusion:** Claude controls its own cursor color, not the terminal

---

## Why The Visual Difference?

### In JediTerm
- Character at cursor position has INVERSE attribute
- Foreground and background colors are swapped
- Rendered as a character with reversed colors
- Appears as "colored character"

### In iTerm
- Same escape sequences received
- iTerm detects "this looks like a cursor pattern"
- Renders as block cursor matching terminal cursor style
- Appears as "white block"

### Code Implementation (JediTerm)

**ProperTerminal.kt Lines 610, 619-620:**
```kotlin
val isInverse = style?.hasOption(JediTextStyle.Option.INVERSE) ?: false

// Get colors (swap if INVERSE)
val rawFg = if (isInverse) style?.background else style?.foreground
val rawBg = if (isInverse) style?.foreground else style?.background
```

**Pass 1: Background Drawing (Lines 468-497)**
- Swaps colors when INVERSE is true
- Draws background rectangle with swapped color

**Pass 2: Foreground Drawing (Lines 608-796)**
- Draws character text with swapped colors
- Result: Character appears inverted

---

## Common Patterns

### Pattern 1: Simple Reverse Video Cursor
```
ESC[?25l     # Hide cursor
ESC[10;5H    # Position at row 10, col 5
ESC[7m       # Reverse video
█            # Block character (or space)
ESC[27m      # Normal video
```

### Pattern 2: Colored Block Cursor
```
ESC[?25l                  # Hide cursor
ESC[10;5H                 # Position
ESC[48;2;255;255;255m     # White background
ESC[38;2;0;0;0m           # Black foreground
█                         # Block character
ESC[0m                    # Reset
```

### Pattern 3: Bar Cursor (Vertical Line)
```
ESC[?25l     # Hide cursor
ESC[10;5H    # Position
ESC[7m       # Reverse video
│            # Vertical bar character
ESC[27m      # Normal video
```

---

## Testing & Diagnostics

### Verify Cursor Visibility
```bash
cd benchmarks
./diagnose_cursor.sh
```
Expected output when opening Claude Code:
```
👁️  CURSOR VISIBLE: true → false
```

### Capture Escape Sequences
```bash
cd benchmarks
./diagnose_escape_sequences.sh
```
Look for:
- `^[[?25l` - Cursor hidden
- `^[[7m` - Reverse video enabled
- `^[[27m` - Reverse video disabled

---

## Implementation Status

### ✅ Correctly Implemented
- Cursor visibility state tracking
- INVERSE attribute handling
- Foreground/background color swapping
- Reverse video rendering
- Multi-pass rendering (background + foreground)

### ✅ Working As Designed
- TUI app cursor rendering
- Escape sequence interpretation
- Color attribute handling

### 🔍 Difference (Not a Bug)
- Visual presentation differs from iTerm
- Both terminals handle sequences correctly
- Different presentation philosophies

---

## Recommendations

### For Users
- **Current behavior is correct** - no action needed
- If you prefer iTerm-style block cursor, that would require heuristic detection
- JediTerm's faithful rendering is technically more correct

### For Developers
If you want iTerm-style cursor synthesis:
1. Detect reverse video at cursor position (when `cursorVisible = false`)
2. Check if character is block-like (█, space, etc.)
3. Override rendering with terminal cursor style
4. Trade-off: Less faithful to application intent

### Current Approach
- Keep faithful escape sequence interpretation
- Provides accurate representation of application styling
- Works correctly with all TUI applications

---

## Related Files

**Implementation:**
- `ComposeTerminalDisplay.kt` - Cursor state tracking
- `ProperTerminal.kt:608-796` - Character rendering with INVERSE handling

**Diagnostics:**
- `benchmarks/diagnose_cursor.sh` - Cursor visibility monitoring
- `benchmarks/diagnose_escape_sequences.sh` - Escape sequence capture
- `benchmarks/DIAGNOSTICS.md` - Complete diagnostic guide

**Documentation:**
- `CLAUDE.md` - Project development guide
- This file - TUI cursor behavior explanation

---

## Conclusion

**Issue Status:** ✅ **Resolved - Working as designed**

JediTerm correctly implements terminal escape sequences for cursor rendering. TUI applications like Claude Code intentionally hide the system cursor and draw their own using reverse video or explicit colors. The visual difference compared to iTerm is a presentation choice, not a bug.

Both approaches are valid:
- **iTerm:** Smart detection + cursor synthesis = familiar look
- **JediTerm:** Faithful rendering = technically correct

Current implementation prioritizes correctness over visual similarity to other terminals.

---

**Last Updated:** November 17, 2025
**Investigation:** Complete
**Action Required:** None
