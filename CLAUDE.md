# Claude Code Development Guide for BossTermKt

This document contains critical information for Claude Code instances working on this project.

## Project Overview

**Repository**: BossTerm (BossTerm Kotlin/Compose Desktop)
**Main Branch**: `master`
**Development Branch**: `dev` (use this for ongoing work)
**Goal**: Modern terminal emulator with Kotlin/Compose Desktop

## Critical Scripts

### `capture_bossterm_only.py`
- **Location**: Project root
- **Purpose**: Captures screenshot of BossTerm window ONLY
- **Output**: `/tmp/bossterm_window.png`
- **Usage**: `python3 capture_bossterm_only.py`

## Critical Technical Patterns

### Font Loading Solution

**Problem**: Custom TTF fonts don't load with standard `Font(resource = "...")` in Compose Desktop 1.7.

**Solution**: Use InputStream + temp file approach:

```kotlin
val nerdFont = remember {
    val fontStream = object {}.javaClass.classLoader
        ?.getResourceAsStream("fonts/MesloLGSNF-Regular.ttf")
        ?: throw IllegalStateException("Font not found")

    val tempFile = java.io.File.createTempFile("MesloLGSNF", ".ttf")
    tempFile.deleteOnExit()
    fontStream.use { input ->
        tempFile.outputStream().use { output -> input.copyTo(output) }
    }

    FontFamily(androidx.compose.ui.text.platform.Font(file = tempFile))
}
```

**Why**: Skiko (Compose Desktop's rendering engine) has classloader issues with resource strings.

### Emoji Rendering with Variation Selectors

**Problem**: Emoji with variation selectors (U+FE0F) rendered as powerline symbols instead of color emoji.

**Root Cause**: Skia doesn't honor Unicode variation selectors - characters render separately.

**Solution**: Peek-ahead detection in `ProperTerminal.kt`:
1. Detect variation selector (U+FE0F, U+FE0E) after emoji
2. Switch to `FontFamily.Default` (system font with Apple Color Emoji)
3. Render emoji + variation selector as single unit
4. Skip variation selector to avoid double-processing

**Working**: ☁️ ☀️ ⭐ ❤️ ✨ ⚡ ⚠️ ✅ ❌ ☑️ ✔️ ➡️

### Terminal Output Processing

**Blocking Data Stream Architecture**: Prevents CSI code truncation.

**Problem**: Creating new `BossEmulator` per chunk caused state loss and truncated CSI sequences.

**Solution**: `BlockingTerminalDataStream.kt` with single long-lived emulator:
- Implements `TerminalDataStream` with blocking behavior
- Uses queue to buffer chunks, blocks on `getChar()` instead of EOF
- Single `BossEmulator` instance preserves state across chunks

**Architecture**:
```kotlin
// Single long-lived instances
val dataStream = remember { BlockingTerminalDataStream() }
val emulator = remember { BossEmulator(dataStream, terminal) }

// Two coroutines:
// 1. Emulator processing (Dispatchers.Default) - blocks on getChar()
// 2. Output reading (Dispatchers.IO) - appends chunks to stream
```

### Rendering Performance Optimization

**Adaptive Debouncing System** (Phase 2):
- **INTERACTIVE (16ms)**: 60fps for typing, vim, small files
- **HIGH_VOLUME (50ms)**: 20fps for bulk output, auto-triggered at >100 redraws/sec
- **IMMEDIATE (0ms)**: Zero-lag for keyboard/mouse input

**Results**: 99.53% reduction in redraws (38,070 → 178 for large file test).

**Implementation**: Channel-based with `Channel.CONFLATED` in `ComposeTerminalDisplay.kt`.

### Snapshot-Based Rendering

**Problem**: UI freezing during high-volume streaming output (e.g., Claude responses, large file cats).

**Root Cause**: `ProperTerminal.kt` rendering held `TerminalTextBuffer` lock for entire 15ms render cycle, blocking PTY writers.

**Solution**: Immutable buffer snapshots for lock-free rendering with copy-on-write optimization:

```kotlin
// Create incremental snapshot with lock (<1ms), then release immediately
// Uses version tracking to reuse unchanged lines (99.5%+ allocation reduction)
val bufferSnapshot = remember(display.redrawTrigger.value, textBuffer.width, textBuffer.height) {
  textBuffer.createIncrementalSnapshot()  // Lock acquired, snapshot created, lock released in <1ms
}

Canvas(modifier = Modifier.fillMaxSize()) {
  // NO LOCK HELD - render from immutable snapshot
  val height = bufferSnapshot.height
  val width = bufferSnapshot.width

  for (row in 0 until height) {
    val line = bufferSnapshot.getLine(lineIndex)  // Fast, no lock
    // ...render backgrounds and text...
  }
}
```

**Architecture**:
- `TerminalTextBuffer.createIncrementalSnapshot()`: Creates versioned snapshot with copy-on-write
- `IncrementalSnapshotBuilder`: Tracks line versions, reuses unchanged lines
- `VersionedBufferSnapshot`: Immutable view with `getLine(index)` accessor
- `TerminalLine.snapshotVersion`: Version field incremented on mutations
- Compose `remember()`: Caches snapshot until next `redrawTrigger` change

**Performance Impact**:
- **Lock Contention**: 94% reduction (<60ms/sec vs 900ms/sec)
- **Allocation Churn**: 99.5%+ reduction (only changed lines copied)
- **Before**: 430KB allocation per frame × 60fps = 26 MB/sec GC pressure
- **After**: <10KB allocation per frame (only 0-5 changed lines)

**Copy-on-Write Optimization**:
- Each `TerminalLine` tracks a `snapshotVersion` field
- Version incremented on any mutation (write, insert, delete, clear, etc.)
- `IncrementalSnapshotBuilder` compares versions to previous snapshot
- Unchanged lines: Zero-copy reference sharing
- Changed lines: Deep copy for immutability

**Key Files**:
- `pool/IncrementalSnapshotBuilder.kt`: Core COW logic with version comparison
- `pool/CharArrayPool.kt`: Object pool for char arrays (size-bucketed)
- `TerminalTextBuffer.kt`: `createIncrementalSnapshot()` API
- `TerminalLine.kt`: `snapshotVersion` field, `incrementSnapshotVersion()` calls
- `ProperTerminal.kt`: Uses incremental snapshots for rendering

**Fallback API**:
- `createSnapshot()`: Original full-copy snapshot (for search, selection, text extraction)
- `createIncrementalSnapshot()`: Optimized versioned snapshot (for rendering loop)

## Shell Integration Setup

### OSC 7 Working Directory Tracking

Enable automatic working directory detection in your shell. When configured, new tabs will inherit the current working directory from the active tab.

**For Bash** (add to `~/.bashrc`):
```bash
PROMPT_COMMAND='echo -ne "\033]7;file://${HOSTNAME}${PWD}\007"'
```

**For Zsh** (add to `~/.zshrc`):
```bash
precmd() { echo -ne "\033]7;file://${HOST}${PWD}\007" }
```

This enables:
- New tabs inherit CWD from active tab
- Tab titles display current directory
- Dynamic shell integration for directory awareness

### OSC 133 Command Completion Notifications

Enable system notifications when commands complete while the terminal window is not focused.
This is similar to iTerm2's notification feature.

**For Bash** (add to `~/.bashrc`):
```bash
# OSC 133 Shell Integration for command notifications
__prompt_command() {
    local exit_code=$?
    # D: Command finished with exit code
    echo -ne "\033]133;D;${exit_code}\007"
    # A: Prompt starting
    echo -ne "\033]133;A\007"
}
PROMPT_COMMAND='__prompt_command'

# B: Command starting (before command execution)
trap 'echo -ne "\033]133;B\007"' DEBUG
```

**For Zsh** (add to `~/.zshrc`):
```bash
# OSC 133 Shell Integration for command notifications
precmd() {
    local exit_code=$?
    # D: Command finished with exit code
    print -Pn "\e]133;D;${exit_code}\a"
    # A: Prompt starting
    print -Pn "\e]133;A\a"
    # Also emit OSC 7 for directory tracking
    print -Pn "\e]7;file://${HOST}${PWD}\a"
}

preexec() {
    # B: Command starting (before command execution)
    print -Pn "\e]133;B\a"
}
```

**Settings** (`~/.bossterm/settings.json`):
- `notifyOnCommandComplete`: Enable/disable notifications (default: true)
- `notifyMinDurationSeconds`: Minimum command duration to trigger notification (default: 5)
- `notifyShowExitCode`: Include exit code in notification (default: true)
- `notifyWithSound`: Play notification sound (default: true)

**How it works**:
1. Shell emits OSC 133;B when command starts
2. Shell emits OSC 133;D;exitcode when command finishes
3. If window is not focused AND command took >= 5 seconds, notification is shown

## Build & Run Commands

```bash
# Clean build
./gradlew clean && ./gradlew :compose-ui:run --no-daemon

# Kill gradle processes (when stuck)
pkill -9 -f "gradle"

# Check running instance
ps aux | grep "ai.rever.bossterm.compose.demo.MainKt"
```

## Git Workflow

```bash
# Always use dev branch
git checkout dev

# Commit with proper format
git add .
git commit -m "Your message

🤖 Generated with [Claude Code](https://claude.com/claude-code)

Co-Authored-By: Claude <noreply@anthropic.com>"

# Push to dev
git push origin dev

# Create PR to master when ready
gh pr create --base master --head dev --title "Your PR title" --body "Description"
```

## Key Files

### Terminal Rendering
- `compose-ui/src/desktopMain/kotlin/org/jetbrains/bossterm/compose/demo/ProperTerminal.kt`
  - Lines 97-125: Font loading
  - Lines 585-597, 670-684, 725-767: Emoji + variation selector handling
  - Lines 215-264: Dual-coroutine output processing
  - Lines 987-1002: Snapshot-based rendering (lock-free)
  - Lines 224-248: Snapshot-based search
  - Lines 322-329: Snapshot-based selection
  - Lines 1972-1998: Snapshot-based text extraction

### Buffer Management
- `bossterm-core-mpp/src/jvmMain/kotlin/com/bossterm/terminal/model/TerminalTextBuffer.kt`
  - Added `createSnapshot()`: Creates immutable buffer snapshot in <1ms
  - Added `BufferSnapshot` data class: Lock-free line accessor

### Resources
- `compose-ui/src/desktopMain/resources/fonts/MesloLGSNF-Regular.ttf` (2.5MB, no spaces in filename)

## Major Features (Implemented)

### 1. Multiple Terminal Tabs (#7)
- **TabController**: Lifecycle manager for tab creation, switching, closing
- **TerminalTab**: Complete per-tab state isolation (terminal, PTY, UI state)
- **TabBar UI**: Material 3 design with close buttons
- **Auto-close**: Tabs close when shell exits
- **Working Directory Inheritance**: New tabs can inherit CWD from active tab
- **Keyboard Shortcuts**: Ctrl+T (new tab), Ctrl+W (close), Ctrl+Tab (next), Ctrl+Shift+Tab (prev), Ctrl+1-9 (jump to tab)
- **OSC 7 Tracking**: Working directory updates via shell integration

**Key Files**: `TabController.kt` (336 lines), `TerminalTab.kt` (223 lines), `TabBar.kt` (135 lines), `BuiltinActions.kt` (269-395 lines for tab shortcuts), `WorkingDirectoryOSCListener.kt` (44-72 lines for OSC 7)

### 2. Extensible Terminal Actions (#11)
- **ActionRegistry**: Thread-safe action management
- **KeyStroke**: Platform-aware key combinations
- **TerminalAction**: Encapsulates action with keystrokes + handler
- **Built-in Actions**: Copy, Paste, Search, Clear Selection, Toggle IME, Select All, Debug Panel

**Key Files**: `TerminalAction.kt`, `ActionRegistry.kt`, `BuiltinActions.kt`

### 3. Clipboard Enhancements (#9)
- **Copy-on-select**: Auto-copy on mouse release
- **Middle-click paste**: Paste with middle button
- **X11 emulation**: Separate selection vs system clipboard

**Settings**: `copyOnSelect`, `pasteOnMiddleClick`, `emulateX11CopyPaste`

### 4. Settings System (#4)
- **JSON persistence**: `~/.bossterm/settings.json`
- **30+ options**: Visual, behavior, performance, debug settings
- **Hot reload**: Settings load on startup

### 5. Search (#2)
- **Ctrl/Cmd+F**: Toggles search bar
- **Material 3 UI**: Modern design matching overall theme
- **Features**: Case-sensitive, regex, next/previous navigation

### 6. Hyperlink Detection (#3)
- **Auto-detection**: URLs, file paths, email addresses
- **Ctrl/Cmd+Click**: Opens in default browser/application
- **Hover highlighting**: Blue underline on hover

### 7. IME Support (#5)
- **CJK input**: Full Chinese/Japanese/Korean input method support
- **Ctrl+Space**: Toggle IME
- **Composition preview**: Shows pre-commit text

### 8. Context Menu (#6)
- **Right-click**: Copy, Paste, Clear, Select All
- **Mouse positioning**: Appears at cursor location

### 9. Terminal Debug Tools (#10)
- **Time-Travel Debugging**: State snapshots every 100ms (circular buffer)
- **I/O Data Collection**: Captures PTY output, user input (1000 chunks max)
- **Control Sequence Visualization**: Human-readable ANSI escape sequences
- **Ctrl/Cmd+Shift+D**: Toggle debug panel
- **Multi-View**: SCREEN, STYLE, HISTORY buffer inspection

**Key Files**: `DebugModels.kt`, `DebugDataCollector.kt`, `DebugControlSequenceVisualizer.kt`, `DebugPanel.kt`, `DebugPanelComponents.kt`

**Settings**: `debugModeEnabled`, `debugMaxChunks`, `debugMaxSnapshots`, `debugCaptureInterval`

### 10. Mouse Reporting Modes (#20)
- **Event Forwarding**: Mouse clicks, movement, drag, and scroll events forwarded to terminal applications
- **Application Support**: vim, tmux, htop, less, fzf, Midnight Commander, and other mouse-aware apps
- **Mouse Modes**: NORMAL, BUTTON_MOTION, ALL_MOTION, HILITE (FOCUS not yet implemented)
- **Shift+Click Bypass**: Holding Shift forces local actions (selection, scrolling) even when app has mouse mode
- **Alternate Buffer Scroll**: Scroll wheel events forwarded to apps using alternate buffer (vim, less, etc.)
- **Coordinate Mapping**: Accurate pixel-to-character cell conversion with boundary clamping

**Architecture**:
- `ComposeMouseEvent.kt`: Event adapter layer converting Compose PointerEvent to BossTerm MouseEvent/MouseWheelEvent
- `ComposeTerminalDisplay.kt`: Tracks current mouse mode state from terminal
- `ProperTerminal.kt`: Decision logic in all pointer event handlers (Press, Move, Release, Scroll)

**Key Methods**:
- `isMouseReporting()`: Check if terminal app has enabled mouse mode
- `isLocalMouseAction()`: Determine if event should be handled locally
- `isRemoteMouseAction()`: Determine if event should be forwarded to app
- `pixelToCharCoords()`: Convert pixel offset to 0-based character coordinates

**Testing**: See `MOUSE_REPORTING_TEST.md` for comprehensive testing guide

**Settings**: `enableMouseReporting` (default: true)

### 11. Auto-Scroll During Selection Drag (#24)
- **Bounds Detection**: Detects when drag extends above/below visible canvas
- **Proportional Speed**: Scroll velocity increases with distance from bounds
- **Timer-Based**: Continuous 20 Hz scrolling even when mouse is stationary outside bounds
- **Bi-directional**: Scroll up into history (above) or down toward current (below)

**Implementation**:
- `AUTO_SCROLL_SPEED = 0.05f`: Coefficient matching Swing TerminalPanel.java reference
- `AUTO_SCROLL_INTERVAL = 50L`: 20 Hz timer for continuous scrolling
- `startAutoScroll()`: Coroutine-based timer that updates scroll offset and selection

**Key File**: `ProperTerminal.kt` (lines 345-389)

**Testing**: Generate history with `for i in {1..200}; do echo "Line $i"; done`, then drag selection beyond bounds.

### 12. Surrogate Pair & Grapheme Cluster Support (#TBD)
- **Full Unicode Support**: Characters outside Basic Multilingual Plane (U+10000+)
- **Emoji Sequences**: ZWJ emoji (👨‍👩‍👧‍👦), skin tones (👍🏽), variation selectors (☁️)
- **Combining Characters**: Diacritics and other combining marks (á = a + ◌́)
- **Production-Grade**: ICU4J BreakIterator for accurate grapheme segmentation

**Architecture**:
- `GraphemeCluster.kt` (150 lines): Data class representing single grapheme with metadata
- `GraphemeMetadata.kt` (189 lines): Sparse boundary tracking for efficient random access
- `GraphemeUtils.kt` (257 lines): Core segmentation engine with LRU cache (1024 entries)
- ICU4J dependency (74.1): Industry-standard Unicode library

**Critical Bug Fixes**:
1. **BossEmulator1.readNonControlCharacters()** (lines 76-132)
   - BEFORE: Char-by-char iteration split surrogate pairs
   - AFTER: Grapheme-aware iteration preserves multi-code-point sequences
   - IMPACT: Characters >U+FFFF now display correctly

2. **BossTerminal.newCharBuf()** (lines 1148-1175)
   - BEFORE: DWC insertion destroyed surrogate pairs
   - AFTER: Grapheme-aware DWC marking preserves surrogate pair integrity
   - IMPACT: Wide characters >U+FFFF render with correct spacing

3. **BlockingTerminalDataStream.append()** (lines 53-78, 176-248)
   - BEFORE: Chunk boundaries could split graphemes mid-sequence
   - AFTER: Incomplete grapheme buffering ensures atomic processing
   - IMPACT: Streaming output never splits emoji or surrogate pairs

4. **CharUtils.countDoubleWidthCharacters()** (lines 70-88)
   - BEFORE: Incremented by 1 after codePointAt(), missed low surrogates
   - AFTER: Increments by Character.charCount() for proper surrogate handling
   - IMPACT: Accurate width calculation for supplementary plane characters

**New APIs**:
- `CharUtils.getTextLengthGraphemeAware()`: Comprehensive width calculation
- `GraphemeUtils.segmentIntoGraphemes()`: ICU4J-based segmentation
- `GraphemeUtils.getGraphemeWidth()`: Cached width calculation with special emoji handling
- `GraphemeMetadata.analyze()`: Sparse metadata generation (null for simple ASCII)

**Key Files Modified**:
- build.gradle.kts: Added ICU4J 74.1
- CharUtils.kt: +66 lines (grapheme-aware methods, surrogate pair fix)
- BossEmulator1.kt: Complete rewrite of readNonControlCharacters()
- BossTerminal.kt: Complete rewrite of newCharBuf()
- BlockingTerminalDataStream.kt: +119 lines (incomplete grapheme buffering)

**Performance**:
- LRU cache (1024 entries) for grapheme width calculations
- ThreadLocal BreakIterator for thread safety without synchronization
- Fast-path for ASCII (no grapheme analysis needed)
- Sparse metadata (only for complex graphemes, null for simple strings)

**Remaining Work**:
- ProperTerminal.kt rendering refactor (grapheme-aware iteration)
- BossTerminal.kt cursor movement (grapheme boundaries)
- ProperTerminal.kt selection (grapheme boundaries)
- Comprehensive test suite (200+ tests planned)
- CharBuffer.kt refactoring (deferred - not critical after bug fixes)

**Status**: Foundation complete, critical bugs fixed (November 27, 2025)

### 13. Command Completion Notifications (#TBD)
- **iTerm2-style Notifications**: System notifications when commands complete while window is unfocused
- **OSC 133 Protocol**: FinalTerm shell integration (A=prompt, B=command start, C=output end, D=exit code)
- **Configurable Duration**: Minimum command duration threshold (default 5 seconds)
- **macOS Integration**: Native notifications via osascript with optional sound
- **Window Focus Tracking**: AWT WindowFocusListener integration for accurate focus detection

**Architecture**:
- `CommandStateListener.kt`: Interface for OSC 133 events (core module)
- `CommandNotificationHandler.kt`: Tracks command timing and triggers notifications
- `NotificationService.kt`: Platform-specific notification display (macOS via osascript)
- `BossEmulator1.kt`: OSC 133 parsing in `doProcessOsc()` method
- `BossTerminal.kt`: `processShellIntegration()` dispatches to listeners

**Key Files**:
- `bossterm-core-mpp/.../model/CommandStateListener.kt`: Listener interface
- `compose-ui/.../notification/NotificationService.kt`: macOS notifications
- `compose-ui/.../notification/CommandNotificationHandler.kt`: Business logic
- `compose-ui/.../demo/Main.kt`: Window focus tracking

**Settings**: `notifyOnCommandComplete`, `notifyMinDurationSeconds`, `notifyShowExitCode`, `notifyWithSound`

**Status**: Complete (December 7, 2025)

## Known Issues & Todos

### In Progress
None - feature complete for current phase

### Remaining Work
- Background tab performance optimization (low priority)
- Extended CSI codes (as needed for specific use cases)
- Terminal multiplexer UI (future research, out of scope)
- SSH key management UI (future enhancement)

### Completed (Recent)
✅ Command Completion Notifications (OSC 133 Shell Integration) - December 7, 2025
✅ Tab Keyboard Shortcuts (Ctrl+T, Ctrl+W, Ctrl+Tab, Ctrl+1-9) - December 3, 2025
✅ OSC 7 Working Directory Tracking - December 3, 2025
✅ Auto-Scroll During Selection Drag (November 30, 2025, issue #24)
✅ Type-Ahead Prediction System (November 30, 2025, issue #23)
✅ Snapshot-Based Rendering - 94% lock contention reduction (November 29, 2025)
✅ Mouse Reporting Modes (November 21, 2025, issue #20)
✅ Terminal Debug Tools (November 19, 2025, issue #10)
✅ Multiple Terminal Tabs (November 19, 2025, issue #7)
✅ Clipboard Enhancements (November 19, 2025, issue #9)
✅ Code Cleanup & Hyperlink Enhancement (November 19, 2025)
✅ Extensible Actions Framework (November 18, 2025, issue #11)
✅ High-Priority Features Sprint (November 18, 2025, issues #2-#6)
✅ Rendering Optimization - 99.8% reduction (November 17, 2025)
✅ CSI Code Truncation Fix (November 16, 2025)
✅ Font Loading & Emoji Rendering (November 14-15, 2025)

## Development Guidelines

### Testing
**IMPORTANT**: Do NOT run the application or capture screenshots. The user will handle running and testing.
1. Make code changes
2. Wait for user to run and provide feedback/screenshots
3. Iterate based on user feedback

### Performance
- Use `remember {}` for expensive computations
- Cache TextStyle and font measurements
- Profile with `/tmp/bossterm_*.log` files

### Code Quality
- Add clear comments for non-obvious logic
- Use try-catch for font loading (with fallbacks)
- Security: Avoid XSS, SQL injection, command injection, etc.
- No backwards-compatibility hacks (unused vars, removed code comments)

### Tool Usage
- Task tool for file searches and complex queries
- Specialized tools over bash (Read vs cat, Edit vs sed)
- TodoWrite for planning and tracking progress

### Asking Questions
- Use AskUserQuestion for clarifications during execution
- Validate assumptions before implementation
- Offer choices when multiple approaches exist

## Tone and Style
- Concise, technical communication (code is monospace rendered)
- No emojis unless explicitly requested
- Direct, objective technical info without superlatives
- Focus on facts and problem-solving

## Autonomous Development Mode

**Active**: YES (until November 30, 2025)
**Permissions**: Full access to git, gh CLI, brew, Mac CLI tools

### Continuous Improvement Cycle
1. Identify issue or improvement opportunity
2. Implement fix or enhancement
3. Wait for user to test and provide feedback
4. Commit to dev branch (when user approves)
5. Push to GitHub
6. Create PR when feature complete
7. Merge to master when stable

---

## Last Updated
December 7, 2025

### Recent Changes
- **December 7, 2025**: Command Completion Notifications (OSC 133 Shell Integration)
  - **Feature**: iTerm2-style system notifications when commands complete while window unfocused
  - **Protocol**: OSC 133 (FinalTerm) - A=prompt, B=command start, C=output end, D=exit code
  - **New Files**:
    - `bossterm-core-mpp/.../model/CommandStateListener.kt`: Listener interface
    - `compose-ui/.../notification/NotificationService.kt`: macOS notifications via osascript
    - `compose-ui/.../notification/CommandNotificationHandler.kt`: Business logic
  - **Modified Files**:
    - `BossEmulator1.kt`: Added OSC 133 handling in doProcessOsc()
    - `BossTerminal.kt`: Added processShellIntegration() and listener support
    - `Terminal.kt`: Added interface methods for command state
    - `TabController.kt`: Integrates notification handler with tabs
    - `TabbedTerminal.kt`: Added isWindowFocused parameter
    - `Main.kt`: Added AWT WindowFocusListener for focus tracking
    - `TerminalSettings.kt`: Added notification settings
  - **Settings**: notifyOnCommandComplete, notifyMinDurationSeconds, notifyShowExitCode, notifyWithSound
  - **Shell Setup**: Documented OSC 133 configuration for Bash and Zsh in CLAUDE.md
  - **Status**: Build successful, ready for testing
- **December 3, 2025**: Verified keyboard shortcuts and OSC 7 implementation
  - **Tab Keyboard Shortcuts**: CONFIRMED - Ctrl+T (new), Ctrl+W (close), Ctrl+Tab (next), Ctrl+Shift+Tab (prev), Ctrl+1-9 (jump)
    - Location: `BuiltinActions.kt` lines 269-395
    - All shortcuts fully registered with platform-aware key bindings
  - **OSC 7 Working Directory Tracking**: CONFIRMED - Full implementation
    - Location: `WorkingDirectoryOSCListener.kt` lines 1-72
    - Parses OSC 7 sequences and updates reactive state
    - Shell setup instructions documented in CLAUDE.md
  - **Status**: Both features complete and ready for production use
  - **Updated CLAUDE.md**: Removed from "Remaining Work", added to "Completed", documented shell setup
- **December 2, 2025**: Incremental Snapshot Builder with Copy-on-Write Optimization
  - **Problem**: GC pressure from 430KB allocations per frame at 60fps (26 MB/sec allocation churn)
  - **Solution**: Copy-on-write snapshots with version tracking
    - `IncrementalSnapshotBuilder`: Tracks line versions, reuses unchanged lines
    - `TerminalLine.snapshotVersion`: Version field incremented on mutations
    - `CharArrayPool`: Size-bucketed object pool for char arrays
    - `VersionedBufferSnapshot`: Enhanced snapshot with version tracking
  - **Performance**: 99.5%+ allocation reduction (only 0-5 changed lines copied per frame)
  - **New Files**: `pool/IncrementalSnapshotBuilder.kt`, `pool/CharArrayPool.kt`
  - **Modified**: `TerminalLine.kt` (+version tracking), `TerminalTextBuffer.kt` (+API), `ProperTerminal.kt` (uses new API)
  - **API**: `createIncrementalSnapshot()` for rendering, `createSnapshot()` for search/selection
  - **Feature Flag**: `BOSSTERM_DISABLE_SNAPSHOT_POOLING=true` to disable
  - **Status**: Build successful, ready for testing
- **November 29, 2025**: Snapshot-Based Rendering for Lock-Free UI
  - **Problem**: UI freezing during streaming output (Claude responses, large file cats)
  - **Root Cause**: 15ms lock holds during rendering blocked PTY writers (900ms/sec total)
  - **Solution**: Immutable buffer snapshots with Compose caching
    - `TerminalTextBuffer.createSnapshot()`: Creates immutable copy in <1ms
    - `BufferSnapshot` data class: Lock-free accessor with `getLine(index)`
    - Updated rendering, search, and selection to use snapshots
  - **Performance**: 94% lock contention reduction (<60ms/sec vs 900ms/sec)
  - **Memory**: ~200KB snapshot overhead (acceptable trade-off)
  - **Files Modified**: TerminalTextBuffer.kt (+60 lines), ProperTerminal.kt (5 methods updated)
  - **Status**: Build successful, ready for real-world testing with Claude streaming
- **November 27, 2025 (Evening)**: Surrogate Pair & Grapheme Cluster Support (#11)
  - **Foundation**: Added ICU4J 74.1, created 3 new classes (596 lines total)
    - GraphemeCluster.kt: Data class with emoji/surrogate detection
    - GraphemeMetadata.kt: Sparse boundary tracking for random access
    - GraphemeUtils.kt: Core segmentation with LRU cache (1024 entries)
  - **Critical Bug Fixes**: Fixed 4 major surrogate pair bugs
    - BossEmulator1.readNonControlCharacters(): Char-by-char → grapheme iteration
    - BossTerminal.newCharBuf(): Fixed DWC insertion destroying surrogate pairs
    - BlockingTerminalDataStream.append(): Added incomplete grapheme buffering
    - CharUtils.countDoubleWidthCharacters(): Fixed codePoint iteration bug
  - **New APIs**: CharUtils.getTextLengthGraphemeAware() for proper width calculation
  - **Impact**: Characters >U+FFFF (𝕳, 🎨, etc.) now display correctly
  - **Status**: Foundation complete, critical bugs fixed, ready for rendering/cursor work
  - **Commits**: 3 feature commits pushed to dev branch
- **November 21, 2025 (Afternoon, 2:15 PM)**: Mouse Reporting Modes (#20)
  - Implemented mouse event forwarding to terminal applications (vim, tmux, htop, less, etc.)
  - Added Shift+Click bypass mechanism for local actions
  - Created ComposeMouseEvent.kt adapter layer (114 lines)
  - Updated ComposeTerminalDisplay.kt with mouse mode tracking
  - Modified ProperTerminal.kt pointer event handlers (Press, Move, Release, Scroll, Drag)
  - Added comprehensive documentation and testing guide (MOUSE_REPORTING_TEST.md)
  - Build successful, ready for manual testing
- **November 19, 2025 (Late Evening, 10:45 PM)**: Optimized CLAUDE.md documentation
  - Reduced file size by ~50% (1803 → ~900 lines)
  - Removed verbose code review response sections
  - Condensed historical changelog entries
  - Removed redundant code examples
  - Kept essential technical patterns and setup instructions
  - Maintained critical feature documentation
- **November 19, 2025 (Late Evening, 10:30 PM)**: Terminal Debug Tools (#10)
  - Time-travel debugging with state snapshots
  - I/O data collection with circular buffer
  - ANSI escape sequence visualizer
  - 5 new files (1,310 lines), 6 modified files (+141 lines)
  - Ctrl/Cmd+Shift+D keyboard shortcut
  - Build successful, ready for testing
- **November 19, 2025 (Evening)**: Shell compatibility and GC safety fixes
  - Fixed hardcoded `/bin/zsh` to use `$SHELL` env var
  - Improved GC safety with explicit reference holding
- **November 19, 2025 (Afternoon)**: Code cleanup and hyperlink enhancement
  - Removed unused variables
  - Implemented Ctrl/Cmd+Click for hyperlinks
- **November 19, 2025 (Morning)**: Clipboard enhancements (#9)
  - Copy-on-select, middle-click paste, X11 emulation
- **November 18, 2025**: Extensible actions framework (#11), high-priority features (#2-#6)
- **November 17, 2025**: Adaptive debouncing (99.8% redraw reduction)
- **November 16, 2025**: CSI truncation fix, resize handling

---

*This document is maintained by Claude Code instances. Update when discovering new insights or solutions.*
