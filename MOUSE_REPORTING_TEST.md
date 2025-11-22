# Mouse Reporting Test Guide (Issue #20)

## Implementation Summary

Mouse reporting modes have been implemented to forward mouse events to terminal applications (vim, tmux, htop, fzf, etc.) while maintaining support for local actions via Shift+Click bypass.

### Files Modified
- **ComposeMouseEvent.kt** (NEW): Event adapters to convert Compose pointer events to JediTerm mouse events
- **ComposeTerminalDisplay.kt**: Added mouse mode state tracking
- **ProperTerminal.kt**: Added mouse reporting logic to all pointer event handlers

### Key Features
1. **Mouse Mode Detection**: Tracks when terminal applications enable mouse reporting
2. **Event Forwarding**: Sends mouse events (press, release, move, drag, scroll) to applications
3. **Shift+Click Bypass**: Holding Shift forces local actions (selection, scrolling) even when app has mouse mode
4. **Alternate Buffer Scroll**: Scroll wheel events forwarded to apps in alternate buffer (vim, less, etc.)
5. **Coordinate Mapping**: Accurately converts pixel positions to character cell coordinates

## Manual Testing Instructions

### Test 1: Basic Mouse Reporting with vim

**Setup:**
1. Run the application: `./gradlew :compose-ui:run`
2. Type `vim` and press Enter

**Tests:**
- ✅ **Click to position cursor**: Click anywhere in the vim window - cursor should jump to that location
- ✅ **Visual selection**:
  - Press `v` to enter visual mode
  - Click and drag to select text
  - Selection should follow mouse movements
- ✅ **Scroll wheel navigation**:
  - Scroll up/down with mouse wheel
  - Vim should move through the document

**Expected Result**: Mouse events are forwarded to vim, allowing full mouse interaction

### Test 2: Shift+Click Bypass

**Setup:**
1. Have vim open (mouse reporting active)

**Tests:**
- ✅ **Normal click**: Click text in vim - cursor moves (vim handles it)
- ✅ **Shift+Click**: Hold Shift and click text - terminal selection starts (local handling)
- ✅ **Shift+Drag**: Hold Shift, click and drag - creates blue selection highlight (bypasses vim)

**Expected Result**: Shift modifier switches between vim control and terminal control

### Test 3: Mouse Reporting with tmux

**Setup:**
1. Install tmux if not present: `brew install tmux`
2. Type `tmux` and press Enter
3. Enable mouse mode: Press `Ctrl+B`, then type `:set mouse on` and press Enter

**Tests:**
- ✅ **Click to select pane**: Create split (`Ctrl+B`, then `%`), click different panes
- ✅ **Resize panes**: Click and drag pane borders
- ✅ **Scroll in pane**: Scroll wheel should move through buffer
- ✅ **Click to select window**: Click window tabs at bottom

**Expected Result**: Full tmux mouse interaction works as in other terminals

### Test 4: Mouse Reporting with htop

**Setup:**
1. Install htop if not present: `brew install htop`
2. Type `htop` and press Enter

**Tests:**
- ✅ **Click process**: Click any process in the list - should be highlighted
- ✅ **Click buttons**: Click F-key buttons at bottom (F1-Help, F10-Quit, etc.)
- ✅ **Scroll processes**: Use mouse wheel to scroll through process list

**Expected Result**: htop mouse interaction matches standard terminal behavior

### Test 5: Mouse Reporting with less

**Setup:**
1. Create test file: `seq 1 1000 > /tmp/test.txt`
2. Type `less /tmp/test.txt` and press Enter

**Tests:**
- ✅ **Scroll with wheel**: Mouse wheel should scroll through document
- ✅ **Shift+Scroll**: Hold Shift and scroll - should use terminal history scroll (if available)

**Expected Result**: Scroll wheel navigates less document

### Test 6: Normal Terminal Mode (No Mouse Reporting)

**Setup:**
1. Exit all applications (quit vim, tmux, htop, less)
2. At shell prompt

**Tests:**
- ✅ **Click text**: Should start text selection (blue highlight)
- ✅ **Drag selection**: Click and drag to select multiple lines
- ✅ **Double-click word**: Should select entire word (if implemented)
- ✅ **Right-click context menu**: Should show context menu with Copy/Paste
- ✅ **Scroll wheel**: Should scroll terminal history

**Expected Result**: Normal terminal behavior - all mouse events handled locally

### Test 7: Mouse Button Modifiers

**Setup:**
1. Have vim open

**Tests:**
- ✅ **Ctrl+Click**: Should send Ctrl modifier to vim
- ✅ **Shift+Click**: Should force local selection (bypass)
- ✅ **Meta/Cmd+Click**: Should send Meta modifier to vim (if vim supports it)

**Expected Result**: Keyboard modifiers correctly forwarded with mouse events

### Test 8: Alternate Buffer Detection

**Setup:**
1. Type `cat` and press Enter (enters alternate buffer mode)
2. Or use `vim`, `less`, `man bash`, etc.

**Tests:**
- ✅ **Scroll wheel in alternate buffer**: Events forwarded to application
- ✅ **Shift+Scroll in alternate buffer**: Forces local history scroll
- ✅ **Exit to main buffer**: Scroll wheel returns to normal history scrolling

**Expected Result**: Alternate buffer scroll behavior matches expectations

## Settings Verification

Check `~/.jediterm/settings.json`:

```json
{
  "enableMouseReporting": true
}
```

If `enableMouseReporting` is `false`, mouse events will never be forwarded to applications.

## Known Limitations

1. **Mouse Formats**: Currently supports basic XTERM mouse reporting (CSI ? 1000). SGR (1006) and URXVT (1015) formats may need additional testing.

2. **Mouse Modes**:
   - `MOUSE_REPORTING_NORMAL`: Click events only - **IMPLEMENTED**
   - `MOUSE_REPORTING_BUTTON_MOTION`: Motion with button pressed - **IMPLEMENTED**
   - `MOUSE_REPORTING_ALL_MOTION`: All motion events - **IMPLEMENTED**
   - `MOUSE_REPORTING_HILITE`: Highlight tracking - **NOT TESTED**
   - `MOUSE_REPORTING_FOCUS`: Focus in/out events - **NOT IMPLEMENTED**

3. **Coordinate Limits**: Terminal protocols typically support up to 223 columns/rows (255-32). Larger terminals may have issues.

## Debugging

Enable debug logging by setting environment variable:
```bash
JEDITERM_DEBUG_CURSOR=true ./gradlew :compose-ui:run
```

Check mouse mode state in ComposeTerminalDisplay:
```kotlin
println("Mouse mode: ${display.mouseMode.value}")
println("Is reporting: ${display.isMouseReporting()}")
```

## Success Criteria

✅ Build successful without compilation errors
⏳ Mouse clicks position cursor in vim
⏳ Shift+Click creates terminal selection in vim
⏳ Scroll wheel works in vim/less
⏳ tmux mouse interaction functional
⏳ htop click selection works
⏳ Normal shell retains text selection capability

## Next Steps

1. Manual testing with real applications
2. Fix any issues discovered during testing
3. Add integration tests (if feasible)
4. Update CLAUDE.md with implementation details
5. Commit changes and create PR

---

**Implementation Date**: November 21, 2025
**Issue**: #20 - Implement mouse reporting modes
**Status**: Implementation complete, testing in progress
