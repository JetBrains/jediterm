# Claude Code Development Guide for JediTermKt

This document contains critical information for Claude Code instances working on this project.

## Project Overview

**Repository**: jediTermCompose (JediTerm Kotlin/Compose Desktop)
**Main Branch**: `master`
**Development Branch**: `dev` (use this for ongoing work)
**Goal**: Modern terminal emulator with Kotlin/Compose Desktop

## Critical Scripts

### `capture_jediterm_only.py`
- **Location**: Project root
- **Purpose**: Captures screenshot of JediTerm window ONLY
- **Output**: `/tmp/jediterm_window.png`
- **Usage**: `python3 capture_jediterm_only.py`

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

**Problem**: Creating new `JediEmulator` per chunk caused state loss and truncated CSI sequences.

**Solution**: `BlockingTerminalDataStream.kt` with single long-lived emulator:
- Implements `TerminalDataStream` with blocking behavior
- Uses queue to buffer chunks, blocks on `getChar()` instead of EOF
- Single `JediEmulator` instance preserves state across chunks

**Architecture**:
```kotlin
// Single long-lived instances
val dataStream = remember { BlockingTerminalDataStream() }
val emulator = remember { JediEmulator(dataStream, terminal) }

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

## Build & Run Commands

```bash
# Clean build
./gradlew clean && ./gradlew :compose-ui:run --no-daemon

# Kill gradle processes (when stuck)
pkill -9 -f "gradle"

# Check running instance
ps aux | grep "org.jetbrains.jediterm.compose.demo.MainKt"
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
- `compose-ui/src/desktopMain/kotlin/org/jetbrains/jediterm/compose/demo/ProperTerminal.kt`
  - Lines 97-125: Font loading
  - Lines 585-597, 670-684, 725-767: Emoji + variation selector handling
  - Lines 215-264: Dual-coroutine output processing

### Resources
- `compose-ui/src/desktopMain/resources/fonts/MesloLGSNF-Regular.ttf` (2.5MB, no spaces in filename)

## Major Features (Implemented)

### 1. Multiple Terminal Tabs (#7)
- **TabController**: Lifecycle manager for tab creation, switching, closing
- **TerminalTab**: Complete per-tab state isolation (terminal, PTY, UI state)
- **TabBar UI**: Material 3 design with close buttons
- **Auto-close**: Tabs close when shell exits
- **Working Directory Inheritance**: New tabs can inherit CWD from active tab

**Key Files**: `TabController.kt` (336 lines), `TerminalTab.kt` (223 lines), `TabBar.kt` (135 lines)

**Remaining**: Keyboard shortcuts (Ctrl+T, Ctrl+W, Ctrl+Tab), OSC 7 tracking

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
- **JSON persistence**: `~/.jediterm/settings.json`
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
- `ComposeMouseEvent.kt`: Event adapter layer converting Compose PointerEvent to JediTerm MouseEvent/MouseWheelEvent
- `ComposeTerminalDisplay.kt`: Tracks current mouse mode state from terminal
- `ProperTerminal.kt`: Decision logic in all pointer event handlers (Press, Move, Release, Scroll)

**Key Methods**:
- `isMouseReporting()`: Check if terminal app has enabled mouse mode
- `isLocalMouseAction()`: Determine if event should be handled locally
- `isRemoteMouseAction()`: Determine if event should be forwarded to app
- `pixelToCharCoords()`: Convert pixel offset to 0-based character coordinates

**Testing**: See `MOUSE_REPORTING_TEST.md` for comprehensive testing guide

**Settings**: `enableMouseReporting` (default: true)

## Known Issues & Todos

### In Progress
1. Manual testing of debug panel features

### Remaining Work
- Tab keyboard shortcuts (Ctrl+T, Ctrl+W, Ctrl+Tab) - Phase 5
- OSC 7 working directory tracking - Phase 4
- Background tab performance optimization - Phase 8

### Completed (Recent)
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
1. Build and run: `./gradlew :compose-ui:run`
2. Capture screenshot: `python3 capture_jediterm_only.py`
3. Verify rendering and functionality

### Performance
- Use `remember {}` for expensive computations
- Cache TextStyle and font measurements
- Profile with `/tmp/jediterm_*.log` files

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
3. Test with screenshot capture
4. Commit to dev branch
5. Push to GitHub
6. Create PR when feature complete
7. Merge to master when stable

---

## Last Updated
November 21, 2025 2:15 PM PST

### Recent Changes
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
