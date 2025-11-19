# Terminal Diagnostics Tools

Comprehensive diagnostics for debugging terminal behavior in JediTermKt.

---

## Available Diagnostic Scripts

### 1. Mode Diagnostics (`diagnose_mode.sh`)

**Purpose:** Monitor adaptive debouncing mode transitions in real-time.

**Usage:**
```bash
./diagnose_mode.sh
```

**What it shows:**
- Mode transitions: `INTERACTIVE ↔ HIGH_VOLUME`
- Current redraw rate (redraws/sec)
- Mode switch timestamps
- Efficiency metrics

**When to use:**
- TUI app UI updates are delayed (thinking mode toggle, vim splits, etc.)
- Bulk output not triggering HIGH_VOLUME mode
- Understanding when mode switches occur
- Debugging conflation behavior

**Example output:**
```
[10:30:15.123] 🔄 Redraw mode: INTERACTIVE → HIGH_VOLUME (rate: 120/sec)
[10:30:15.500] Mode: HIGH_VOLUME (50ms)
[10:30:15.501] Current rate: 45 redraws/sec
[10:30:20.000] 🔄 Redraw mode: HIGH_VOLUME → INTERACTIVE (auto-recovery)
```

---

### 2. Cursor Diagnostics (`diagnose_cursor.sh`)

**Purpose:** Monitor cursor position, shape, and visibility changes.

**Usage:**
```bash
./diagnose_cursor.sh
```

**What it shows:**
- 🔵 Cursor position changes: `(x,y) → (x',y')`
- 🔷 Cursor shape changes: `BLOCK → BAR → UNDERLINE`
- 👁️  Cursor visibility changes: `true → false`
- Debug info for cursor rendering issues

**When to use:**
- Cursor not visible in interactive apps (Claude Code, vim, etc.)
- Cursor stuck at wrong position
- Cursor shape not updating (insert mode, etc.)
- Understanding cursor behavior in TUI apps

**Example output:**
```
[10:30:15.123] 🔵 CURSOR MOVE: (0,0) → (5,0)
[10:30:15.234] 🔵 CURSOR MOVE: (5,0) → (5,1)
[10:30:15.345] 🔷 CURSOR SHAPE: STEADY_BLOCK → STEADY_BAR
[10:30:15.456] 👁️  CURSOR VISIBLE: true → false
[10:30:16.567] 👁️  CURSOR VISIBLE: false → true
```

---

### 3. Escape Sequence Diagnostics (`diagnose_escape_sequences.sh`)

**Purpose:** Capture and decode raw escape sequences from terminal output.

**Usage:**
```bash
./diagnose_escape_sequences.sh
```

**What it shows:**
- CSI sequences (ESC[...)
- SGR codes (colors, bold, reverse video, etc.)
- Cursor positioning commands
- Mode changes
- Decoded sequence meanings

**When to use:**
- Understanding how TUI apps draw custom cursors
- Debugging color rendering differences
- Investigating reverse video (SGR 7) usage
- Comparing escape sequence handling between terminals

**Common SGR codes:**
- `ESC[7m` - Reverse video (swap fg/bg colors)
- `ESC[27m` - Normal video (disable reverse)
- `ESC[38;...` - Set foreground color
- `ESC[48;...` - Set background color
- `ESC[1m` - Bold
- `ESC[3m` - Italic

**Example output:**
```
[10:30:15.123] ^[[7m [REVERSE VIDEO ON]
[10:30:15.234] ^[[38;2;255;255;255m [SET FG COLOR]
[10:30:15.345] ^[[27m [REVERSE VIDEO OFF]
```

**Output saved to:** `/tmp/jediterm_escape_sequences_YYYYMMDD_HHMMSS.log`

**Note:** TUI apps like Claude Code often use reverse video (SGR 7) to draw custom cursors by inverting the colors of a character at the cursor position.

---

## Environment Variables

### Cursor Debugging

Enable/disable cursor diagnostics:
```bash
export JEDITERM_DEBUG_CURSOR=true   # Enable
export JEDITERM_DEBUG_CURSOR=false  # Disable (default)
```

**Note:** The `diagnose_cursor.sh` script automatically sets this to `true`.

### Future: Configurable Debounce Timing

*(Not yet implemented)*
```bash
export JEDITERM_INTERACTIVE_DEBOUNCE_MS=16  # Default
export JEDITERM_HIGH_VOLUME_DEBOUNCE_MS=50  # Default
```

---

## Common Issues & Diagnostics

### Issue 1: TUI App UI Updates Delayed

**Symptoms:**
- Toggle thinking mode in Claude Code → doesn't update immediately
- Create vim split → shows after delay
- Press key in less → UI lags behind

**Diagnosis:**
```bash
./diagnose_mode.sh
# Watch for mode during the UI update
# Expected: Should stay INTERACTIVE (16ms)
# If switching to HIGH_VOLUME: That's the problem
```

**What to look for:**
- Does mode switch to HIGH_VOLUME during UI update?
- What's the current rate when update happens?
- Are mode transitions frequent (oscillation)?

**Potential fixes:**
1. Reduce INTERACTIVE debounce (16ms → 8ms or 4ms)
2. Add alternate screen buffer detection
3. Use IMMEDIATE priority for TUI updates

---

### Issue 2: Cursor Not Visible

**Symptoms:**
- Cursor missing in Claude Code
- Cursor at wrong position
- Cursor shape not changing in vim insert mode

**Diagnosis:**
```bash
./diagnose_cursor.sh
# Open Claude Code and watch cursor events
```

**What to look for:**
- Is `setCursorVisible(false)` being called?
- Is cursor position off-screen? (x > width or y > height)
- Is cursor shape being set correctly?
- Are there rapid visibility toggles?

**Common causes:**
1. **Cursor hidden by app:** App calls `CSI ?25l` (hide cursor) - This is EXPECTED for TUI apps
2. **Position out of bounds:** Cursor at (-1,-1) or (9999,9999)
3. **Render issue:** Cursor visible but not drawing (z-index, alpha, etc.)
4. **Blink state:** Cursor blinking but timing is off

**Important:** Many TUI apps (Claude Code, vim in TUI mode) intentionally hide the system cursor and draw their own custom cursor using styled characters with reverse video or explicit colors.

**Potential fixes:**
1. Check if `cursorVisible` state is false - if app set it false, that's expected behavior
2. Verify cursor coordinates are in bounds: `0 ≤ x < width, 0 ≤ y < height`
3. Verify cursor alpha (should be 0.7 when focused, 0.3 when unfocused)
4. Check blink state for BLINK_* cursor shapes

---

### Issue 3: Custom Cursor Rendering Differences

**Symptoms:**
- TUI app cursor looks different than in other terminals (iTerm, etc.)
- Cursor appears as colored character instead of block
- Cursor style doesn't match expectations

**Diagnosis:**
```bash
./diagnose_escape_sequences.sh
# Open Claude Code or TUI app and watch for SGR 7 (reverse video)
```

**What to look for:**
- Does app use `ESC[7m` (reverse video) to draw cursor?
- What colors are set via `ESC[38;...` or `ESC[48;...`?
- Are there frequent SGR mode changes at cursor position?

**Understanding:**
- **iTerm behavior:** May detect reverse video patterns and render as block cursor
- **JediTerm behavior:** Faithfully renders styled characters as requested by app
- Both are correct - just different presentation strategies

**Common patterns:**
1. App sets `CSI ?25l` to hide system cursor
2. App positions cursor at specific (x,y)
3. App uses `ESC[7m` (reverse video) on character at cursor position
4. Result: Character appears with inverted colors (background becomes foreground)

---

### Issue 4: Bulk Output Not Triggering HIGH_VOLUME

**Symptoms:**
- `cat large_file.txt` stays in INTERACTIVE mode
- High CPU usage during bulk output
- No efficiency savings

**Diagnosis:**
```bash
./diagnose_mode.sh
# Run: cat /tmp/test_10000.txt
# Should see: INTERACTIVE → HIGH_VOLUME transition
```

**What to look for:**
- Does mode switch when rate exceeds 100/sec?
- What's the peak rate during bulk output?
- Does it auto-recover to INTERACTIVE?

**Potential fixes:**
1. Lower burst threshold (100/sec → 50/sec)
2. Check if burst detection is working
3. Verify channel conflation is enabled

---

## Advanced Debugging

### Enable All Diagnostics

```bash
# Terminal 1: Mode diagnostics
./diagnose_mode.sh

# Terminal 2: Cursor diagnostics
./diagnose_cursor.sh

# Terminal 3: Escape sequence diagnostics (captures to log file)
./diagnose_escape_sequences.sh
```

### Manual Debugging

Launch terminal with environment variables:
```bash
export JEDITERM_DEBUG_CURSOR=true
./gradlew :compose-ui:run --no-daemon 2>&1 | tee /tmp/terminal_debug.log
```

Then analyze the log:
```bash
# Count cursor moves
grep "CURSOR MOVE" /tmp/terminal_debug.log | wc -l

# Find visibility changes
grep "CURSOR VISIBLE" /tmp/terminal_debug.log

# Find mode transitions
grep "Redraw mode" /tmp/terminal_debug.log

# Check redraw rate distribution
grep "Current rate" /tmp/terminal_debug.log | awk '{print $NF}' | sort -n
```

---

## Understanding Cursor Behavior

### Cursor Visibility States

| State | Meaning | When it happens |
|-------|---------|-----------------|
| `true` | Cursor visible | Normal typing, editing |
| `false` | Cursor hidden | App explicitly hides cursor (vim in TUI mode, etc.) |

### Cursor Shapes

| Shape | Description | Common in |
|-------|-------------|-----------|
| `STEADY_BLOCK` | Solid block (default) | Normal shell, default vim |
| `BLINK_BLOCK` | Blinking block | Some terminal configs |
| `STEADY_BAR` | Thin vertical bar | Insert mode (vim, emacs) |
| `BLINK_BAR` | Blinking vertical bar | Insert mode with blink |
| `STEADY_UNDERLINE` | Horizontal line | Replace mode |
| `BLINK_UNDERLINE` | Blinking horizontal line | Replace mode with blink |

### Cursor Position Coordinates

- **X (column):** 0-indexed, left to right
- **Y (row):** 1-indexed in JediTerm, adjusted to 0-indexed for rendering
- **Adjustment:** `adjustedCursorY = (cursorY - 1).coerceAtLeast(0)`

**Example:**
- App sets cursor to (10, 5)
- Render at: x = 10 * cellWidth, y = (5-1) * cellHeight = 4 * cellHeight

---

## Tips for Effective Debugging

1. **Start simple:** Run one diagnostic at a time
2. **Reproduce consistently:** Find reliable steps to trigger the issue
3. **Compare behaviors:** Test in both JediTerm and iTerm2/another terminal
4. **Capture timing:** Use diagnostic scripts to timestamp events
5. **Save logs:** Redirect output to file for analysis: `./diagnose_cursor.sh > cursor_log.txt`
6. **Test incrementally:** After fixes, re-run diagnostics to verify

---

## Contributing Diagnostics

If you discover a new issue that needs diagnostics:

1. Identify what needs to be monitored (state changes, events, etc.)
2. Add debug logging in the relevant code (use environment variable to enable)
3. Create a diagnostic script in `benchmarks/`
4. Document usage in this file
5. Add example output and common issues

---

## Related Files

**Core Implementation:**
- `ComposeTerminalDisplay.kt` - Display interface with cursor state
- `ProperTerminal.kt` - Rendering logic with cursor drawing

**Documentation:**
- `docs/optimization/PHASE2_IMPLEMENTATION.md` - Adaptive debouncing details
- `benchmarks/README.md` - Performance benchmarking guide

---

**Last Updated:** November 17, 2025
**Diagnostic Version:** 1.0
