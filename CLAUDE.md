# Claude Code Development Guide for JediTermKt

This document contains critical information for Claude Code instances working on this project.

## Project Overview

**Repository**: jediTermCompose (JediTerm Kotlin/Compose Desktop)
**Main Branch**: `master`
**Development Branch**: `dev` (use this for ongoing work)
**Date Range**: November 14, 2025 - November 30, 2025
**Goal**: Improve terminal rendering and functionality continuously

## Critical Scripts

### `capture_jediterm_only.py`
- **Location**: Project root
- **Purpose**: Captures screenshot of JediTerm window ONLY (no other apps)
- **Output**: `/tmp/jediterm_window.png`
- **Usage**: `python3 capture_jediterm_only.py`
- **ALWAYS use this script** for capturing terminal screenshots

## Font Loading Solution (CRITICAL)

### Problem
Custom TTF fonts (Nerd Fonts) were not loading correctly in Compose Desktop 1.7 using the standard resource string approach:
```kotlin
// THIS DOESN'T WORK RELIABLY:
androidx.compose.ui.text.platform.Font(resource = "fonts/font.ttf")
```

### Solution (WORKING)
Use InputStream + temp file approach:

```kotlin
val nerdFont = remember {
    try {
        val fontStream = object {}.javaClass.classLoader?.getResourceAsStream("fonts/MesloLGSNF-Regular.ttf")
            ?: throw IllegalStateException("Font resource not found")

        val tempFile = java.io.File.createTempFile("MesloLGSNF", ".ttf")
        tempFile.deleteOnExit()
        fontStream.use { input ->
            tempFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }

        FontFamily(
            androidx.compose.ui.text.platform.Font(
                file = tempFile,
                weight = FontWeight.Normal
            )
        )
    } catch (e: Exception) {
        println("ERROR: Failed to load font: ${e.message}")
        e.printStackTrace()
        FontFamily.Monospace  // Fallback
    }
}
```

### Why This Works
- Skiko (Compose Desktop's rendering engine) has classloader issues with resource strings
- Creating a temp file from the InputStream bypasses these issues
- The temp file is deleted on JVM exit (`deleteOnExit()`)
- Font file MUST be in `compose-ui/src/desktopMain/resources/fonts/`
- Font file IS packaged in the JAR correctly

### Verification
Check if font is in JAR:
```bash
jar tf .gradleBuild/compose-ui/libs/compose-ui-desktop-*.jar | grep -i "font\|ttf"
```

## Emoji Rendering with Variation Selectors (CRITICAL FIX)

### Problem
Emoji with Unicode variation selectors (U+FE0F, U+FE0E) were rendering as monochrome powerline symbols instead of proper colorful emoji. Example: ☁️ (U+2601 + U+FE0F) rendered as ☁ powerline glyph from MesloLGS Nerd Font.

### Root Cause
**Skia/Skiko limitation**: Compose Desktop's rendering engine does NOT honor Unicode variation selectors. Characters are rendered separately at different column positions:
- U+2601 renders using MesloLGS NF (powerline symbol)
- U+FE0F renders separately (invisible or box)

### Solution (IMPLEMENTED)
Implemented peek-ahead detection in `ProperTerminal.kt` (lines 585-597, 670-684, 725-767):

1. **Peek-ahead detection** (lines 585-590): Check if next character is variation selector
2. **Font switching** (lines 670-674): Use `FontFamily.Default` (system font with Apple Color Emoji) for emoji+variation selector pairs
3. **Render both together** (lines 725-729): Concatenate emoji + variation selector and render as single unit
4. **Skip variation selector** (lines 765-767): Advance column after rendering to avoid double-processing
5. **Fallback skip logic** (lines 592-597): Skip standalone variation selectors for non-emoji pairs

### Working Emoji
✅ ☁️ (cloud), ☀️ (sun), ⭐ (star), ❤️ (heart), ✨ (sparkles), ⚡ (lightning)
✅ ⚠️ (warning), ✅ (check mark), ❌ (cross mark), ☑️ (ballot box), ✔️ (check)
✅ ➡️ (arrows), ©️ (copyright), ®️ (registered), ™️ (trademark)
✅ Nerd Font symbols: ▲, ❯, ▶, ★, ✓, ♥, →, ← (no variation selectors needed)

### Test Command
```bash
echo 'Emoji test: ☀️ ⭐ ❤️ ✨ ⚡ ☁️ ⚠️ ✅ ❌ ☑️ ✔️ ➡️'
```

## Current Font Status

### Font Strategy
- **MesloLGS Nerd Font**: Default for all text and Nerd Font symbols
- **System Font (Apple Color Emoji)**: Automatically used for emoji with variation selectors
- **Automatic detection**: Terminal detects emoji+variation selector pairs and switches fonts dynamically

## Build & Run Commands

```bash
# Clean build
./gradlew clean && ./gradlew :compose-ui:run --no-daemon

# Build with logging
./gradlew :compose-ui:run --no-daemon 2>&1 | tee /tmp/jediterm_build.log

# Kill all gradle processes (when stuck)
pkill -9 -f "gradle"

# Check for running terminal instance
ps aux | grep "org.jetbrains.jediterm.compose.demo.MainKt"
```

## Git Workflow

### Branches
- `master`: Stable, tested code
- `dev`: Active development (USE THIS)

### Committing
```bash
# Always commit from dev branch
git checkout dev

# Commit with proper message format
git add .
git commit -m "Your message

🤖 Generated with [Claude Code](https://claude.com/claude-code)

Co-Authored-By: Claude <noreply@anthropic.com>"

# Push to dev
git push origin dev

# When ready, create PR to master
gh pr create --base master --head dev --title "Your PR title" --body "Description"
```

## Key Files

### Terminal Rendering
- `compose-ui/src/desktopMain/kotlin/org/jetbrains/jediterm/compose/demo/ProperTerminal.kt`
  - Lines 97-125: Font loading code
  - Lines 127-133: Text measurement style
  - Main rendering logic

### Resources
- `compose-ui/src/desktopMain/resources/fonts/MesloLGSNF-Regular.ttf`
  - Nerd Font file (2.5MB)
  - Must have NO SPACES in filename

## Terminal Output Processing (CRITICAL)

### Blocking Data Stream Architecture
The terminal now uses a **stateful, single-emulator approach** that prevents CSI code truncation:

#### Problem (Fixed)
- Creating new `JediEmulator` instance per output chunk caused state loss
- When CSI sequences (like `ESC[6n`) spanned multiple chunks, they would be truncated
- Resulted in visible escape codes appearing on screen during nvim startup or window resize

#### Solution (Implemented)
Created `BlockingTerminalDataStream.kt` that:
- Implements `TerminalDataStream` interface with blocking behavior
- Uses internal queue to buffer incoming data chunks
- Blocks on `getChar()` instead of throwing EOF at chunk boundaries
- Maintains single long-lived `JediEmulator` instance to preserve state

#### Architecture
```kotlin
// Single long-lived instances (ProperTerminal.kt:75-84)
val dataStream = remember { BlockingTerminalDataStream() }
val emulator = remember { JediEmulator(dataStream, terminal) }

// Two separate coroutines:
// 1. Emulator processing (Dispatchers.Default) - blocks on getChar()
// 2. Output reading (Dispatchers.IO) - appends chunks to stream
```

#### Key Files
- `BlockingTerminalDataStream.kt` (121 lines) - Core blocking stream implementation
- `ProperTerminal.kt:215-264` - Dual-coroutine output processing logic

### Window Resize Handling (CRITICAL)

#### Problem (Fixed)
- Terminal would crash with negative dimensions during window resize
- Error: `IllegalArgumentException: maxWidth(-110) must be >= than minWidth(0)`

#### Solution (Implemented)
Added comprehensive dimension validation in `ProperTerminal.kt:258-264`:
- Minimum window size: 10x10 pixels
- Minimum terminal size: 2x2 characters
- Uses `coerceAtLeast()` to prevent negative/zero dimensions

## Rendering Performance Optimization (CRITICAL)

### Adaptive Debouncing System (Phase 2 - November 17, 2025)

**Problem:**
- Terminal was redrawing on EVERY character from PTY output
- Large files (10K lines) triggered 30,232 redraws in 45 seconds (672 redraws/sec)
- CPU usage 80-100% during bulk output
- 99% of redraws wasted (human eye sees max 60fps)

**Solution Implemented:**
Channel-based adaptive debouncing with three rendering modes:

1. **INTERACTIVE (16ms debounce)** - 60fps for typing, vim, small files
2. **HIGH_VOLUME (50ms debounce)** - 20fps for bulk output, auto-triggered at >100 redraws/sec
3. **IMMEDIATE (0ms)** - Zero-lag for keyboard/mouse input

**Architecture:**
```kotlin
// ComposeTerminalDisplay.kt
private val redrawChannel = Channel<RedrawRequest>(Channel.CONFLATED)

// Conflation automatically drops intermediate redraws
// Only keeps latest request when processing can't keep up
fun requestRedraw() {
    redrawChannel.trySend(RedrawRequest(NORMAL))  // Debounced
}

fun requestImmediateRedraw() {
    redrawChannel.trySend(RedrawRequest(IMMEDIATE))  // Zero-lag for user input
}
```

**Performance Results:**

| Test | Before (Baseline) | After (Optimized) | Improvement |
|------|------------------|-------------------|-------------|
| Idle (30s) | 52 redraws (1.7/sec) | 5 redraws (0.2/sec) | **-90%** |
| Small file (500 lines) | 1,694 redraws (24/sec) | 16 redraws (0.8/sec) | **-99.1%** |
| Medium file (2K lines) | 6,092 redraws (122/sec) | 138 redraws (6.9/sec) | **-97.7%** |
| Large file (10K lines) | 30,232 redraws (672/sec) | 19 redraws (1.3/sec) | **-99.8%** 🔥 |
| **OVERALL REDUCTION** | **38,070 total redraws** | **178 total redraws** | **-99.53%** |

**User Experience:**
- ✅ Zero typing lag (user reported "more snappy really good")
- ✅ Smooth visual quality maintained
- ✅ CPU usage reduced by ~70-80% (estimated)
- ✅ No regression for small files or interactive apps

**Key Files:**
- `ComposeTerminalDisplay.kt` (+180 lines) - Adaptive debouncing logic
- `ProperTerminal.kt` (+4 lines) - Immediate redraw for user input
- `docs/optimization/` - Detailed analysis and design docs
- `benchmarks/` - Performance test scripts

**Metrics Display:**
Every 5 seconds during operation:
```
┌─────────────────────────────────────────────────────────┐
│ REDRAW PERFORMANCE (Phase 2 - Adaptive Debouncing)     │
├─────────────────────────────────────────────────────────┤
│ Mode:                 INTERACTIVE (16ms)                │
│ Current rate:                   13 redraws/sec          │
│ Total redraws:                  19 redraws              │
│ Coalesced redraws:               0 skipped              │
│ Efficiency:                    0.0% saved               │
│ Average rate:                  1.3 redraws/sec          │
│ Total runtime:                15.0 seconds              │
└─────────────────────────────────────────────────────────┘
```

**Testing:**
```bash
# Run performance tests
cd benchmarks
./test_phase2_optimized.sh

# Or manual test
cd ..
./gradlew :compose-ui:run --no-daemon
# In terminal: cat /tmp/test_10000.txt
# Watch console for mode transitions and metrics
```

## Known Issues & Todos

### In Progress
1. Some symbols still rendering as ∅∅ boxes (partial font success)
2. Need to test with different Nerd Font variants

### Completed (Recent → Oldest)
✅ **Ctrl+F consistency fix** (November 18, 2025, commit df1d183) - Search shortcut works from any focus state
✅ **Context menu positioning** (November 18, 2025, commit 3e7e92d) - Menu appears at mouse cursor
✅ **Search bar redesign** (November 18, 2025, commits fa0ad29, 35d6a89) - Modern Material 3 UI with proper event isolation
✅ **Context menu implementation** (November 18, 2025, commit f7bff7a) - Right-click menu with copy/paste/clear
✅ **IME (CJK) support** (November 18, 2025, commit c546c78) - Full Chinese/Japanese/Korean input method support
✅ **High-priority features batch** (November 18, 2025, commit 0f59c9c) - Settings, Search, Hyperlinks, IME
✅ **Rendering optimization** (November 17, 2025) - 99.8% reduction in redraws
✅ **Adaptive debouncing** - Three-mode system with burst detection
✅ **Zero-lag user input** - Immediate mode for keyboard/mouse
✅ **CSI code truncation fix** (commit e147d0b) - No more visible escape codes
✅ **Terminal crash fix** (commit 59f4154) - Resize no longer crashes terminal
✅ **Stateful emulator architecture** - Single long-lived emulator with blocking stream
✅ Font loading mechanism (File-based approach)
✅ Dev branch created
✅ Screenshot capture script documented
✅ Most Nerd Font symbols rendering correctly

## Development Guidelines

### Testing
1. Build and run the terminal
2. Execute test command: `echo 'Emoji test: ☁ ❯ ▶ ∅∅ ∅∅ ★ ✓ ♥ → ←'`
3. Capture screenshot with `python3 capture_jediterm_only.py`
4. Verify symbol rendering

### Performance
- Use `remember {}` for expensive computations
- Cache TextStyle and font measurements
- Profile with `/tmp/jediterm_*.log` files

### Code Quality
- Add clear comments for non-obvious logic
- Use try-catch for font loading (with fallbacks)
- Log errors with `println()` or proper logger

## Extensible Terminal Actions Framework (Issue #11)

### Overview
Implemented a plugin-style action framework to replace hardcoded keyboard handling with an extensible, testable system. This allows for easy addition of new keyboard shortcuts and customization without modifying core terminal code.

**Implementation Date**: November 18, 2025
**GitHub Issue**: [#11](https://github.com/kshivang/jediTermCompose/issues/11)

### Architecture

**Core Components**:

1. **KeyStroke** (`TerminalAction.kt`) - Data class representing a keyboard combination
   - Stores key + modifiers (ctrl, shift, alt, meta)
   - Platform-aware matching (macOS uses Cmd/meta, Windows/Linux use Ctrl)
   - Supports multiple keystrokes per action (e.g., Ctrl+C and Cmd+C for same action)

2. **TerminalAction** (`TerminalAction.kt`) - Encapsulates an action with its keystrokes and handler
   - Unique ID and display name
   - List of keystrokes that trigger the action
   - Enabled predicate for conditional availability
   - Handler function that executes the action

3. **ActionRegistry** (`ActionRegistry.kt`) - Thread-safe registry for managing actions
   - Register/unregister actions
   - Find actions by ID or KeyEvent
   - Execute actions with enable/disable support
   - Platform-aware (isMacOS parameter)

4. **Built-in Actions** (`BuiltinActions.kt`) - Factory for creating all built-in terminal actions
   - Copy (Cmd/Ctrl+C) - Only when selection exists
   - Paste (Cmd/Ctrl+V)
   - Search (Cmd/Ctrl+F) - Toggles search bar
   - Clear Selection (Escape)
   - Toggle IME (Ctrl+Space) - For CJK input
   - Select All (Cmd/Ctrl+A)

### Key Files

#### compose-ui/src/desktopMain/kotlin/org/jetbrains/jediterm/compose/actions/
- `TerminalAction.kt` (90 lines) - Data classes for KeyStroke and TerminalAction
- `ActionRegistry.kt` (120 lines) - Thread-safe action registry with ConcurrentHashMap
- `BuiltinActions.kt` (233 lines) - Factory function + all built-in action handlers

#### Modified Files
- `ProperTerminal.kt` - Refactored keyboard handling (removed 118 lines of hardcoded logic)
  - Lines 336-372: macOS detection + action registry creation
  - Lines 734-780: Simplified onKeyEvent using ActionRegistry

### Usage Examples

**Creating a Custom Action**:
```kotlin
val customAction = TerminalAction(
    id = "custom_action",
    name = "My Custom Action",
    keyStroke = KeyStroke(key = Key.K, ctrl = true, shift = true),
    enabled = { /* condition */ true },
    handler = { keyEvent ->
        // Handle the action
        println("Custom action triggered!")
        true  // Return true if event was consumed
    }
)

// Register it
actionRegistry.register(customAction)
```

**Creating Platform-Specific Actions**:
```kotlin
val action = TerminalAction(
    id = "copy",
    name = "Copy",
    keyStrokes = listOf(
        KeyStroke(key = Key.C, ctrl = true),  // Windows/Linux
        KeyStroke(key = Key.C, meta = true)   // macOS (Cmd key)
    ),
    handler = { event -> /* ... */ }
)
```

**Dynamic Lambda Access Pattern (CRITICAL)**:
When passing resources that are initialized later (e.g., processHandle), use lambdas:
```kotlin
// WRONG - Captures null value:
processHandle = processHandle

// CORRECT - Captures variable by reference:
getProcessHandle = { processHandle }
```

### Platform Awareness

The framework automatically handles platform differences:
- **macOS**: Uses Cmd (meta) key as primary modifier
- **Windows/Linux**: Uses Ctrl key as primary modifier
- Actions can define multiple keystrokes to support both platforms
- KeyStroke matching logic automatically checks correct modifier based on `isMacOS` flag

### Testing

**Unit Tests**: `compose-ui/src/commonTest/kotlin/org/jetbrains/jediterm/compose/actions/ActionRegistryTest.kt`
- Tests action registration and retrieval
- Tests KeyStroke properties
- Tests TerminalAction enabled/disabled states
- Tests ActionRegistry operations (clear, size, getAllActions)
- Note: KeyEvent matching tested via manual integration testing (KeyEvent constructor is internal)

**Manual Integration Testing**:
All shortcuts verified working:
- ✅ Copy (Cmd/Ctrl+C) - Only with selection
- ✅ Paste (Cmd/Ctrl+V) - Fixed lambda capture issue
- ✅ Search (Cmd/Ctrl+F) - Toggles search bar
- ✅ Clear selection (Escape)
- ✅ Select all (Cmd/Ctrl+A)
- ✅ Toggle IME (Ctrl+Space) - For CJK input

### Benefits

1. **Extensibility**: Add new actions without modifying core terminal code
2. **Testability**: Actions can be unit tested independently
3. **Maintainability**: Clear separation of concerns, actions defined in one place
4. **Customization**: Users can override or add shortcuts in the future
5. **Platform Support**: Automatic handling of macOS vs Windows/Linux shortcuts
6. **Thread Safety**: ConcurrentHashMap ensures safe concurrent access

### Future Enhancements

- User-configurable shortcuts (load from settings)
- Action groups and categories
- Visual shortcut editor UI
- Action enable/disable from settings
- Macro recording (sequence of actions)
- Context-sensitive actions (enabled based on terminal state)

## Resources & References

### Compose Desktop Font Loading
- [Stack Overflow: Font loading in Compose Desktop](https://stackoverflow.com/questions/66546700/how-to-load-fonts-in-jetpack-compose-desktop)
- [GitHub Issue #4184](https://github.com/JetBrains/compose-multiplatform/issues/4184) - Classloader issues

### Nerd Fonts
- [Official Site](https://www.nerdfonts.com/)
- MesloLGS NF: Includes powerline glyphs and programming ligatures
- Alternative: JetBrainsMono Nerd Font, FiraCode Nerd Font

## Autonomous Development Mode

**Active**: YES
**End Date**: November 30, 2025
**Permissions**: Full access to git, gh CLI, brew, Mac CLI tools

### Continuous Improvement Cycle
1. Identify issue or improvement opportunity
2. Implement fix or enhancement
3. Test with screenshot capture
4. Commit to dev branch
5. Push to GitHub
6. Create PR when feature is complete
7. Merge to dev, then to master when stable
8. Repeat

### Guidelines
- Work independently until November 30, 2025
- Create PRs for significant features
- Use `dev` branch as main development branch
- Merge to `master` when features are tested and stable
- Document all findings in this file
- Use TodoWrite tool to track progress
- Capture screenshots for visual verification

## Last Updated
November 18, 2025 6:30 PM PST

### Recent Changes
- **November 18, 2025 (Evening)**: Implemented extensible terminal actions framework (#11)
  - Created plugin-style action system with KeyStroke, TerminalAction, and ActionRegistry classes
  - Refactored ProperTerminal.kt to use ActionRegistry (removed 118 lines of hardcoded logic)
  - Implemented 6 built-in actions: Copy, Paste, Search, Clear Selection, Toggle IME, Select All
  - Platform-aware shortcuts (macOS Cmd vs Windows/Linux Ctrl)
  - Fixed paste lambda capture issue for dynamic processHandle access
  - Created comprehensive unit tests (ActionRegistryTest.kt)
  - All shortcuts manually verified working
  - Closed GitHub issue #11
- **November 18, 2025 (Afternoon)**: Completed high-priority feature implementation sprint
  - Implemented Settings System (#4) - JSON persistence, 30+ options
  - Implemented Text Search (#2) - Ctrl+F with modern Material 3 UI
  - Implemented Hyperlink Detection (#3) - Clickable URLs with Ctrl+Click
  - Implemented IME Support (#5) - Full CJK input method support
  - Implemented Context Menu (#6) - Right-click menu with copy/paste/clear
  - Fixed context menu positioning to appear at mouse cursor
  - Fixed search bar text cutoff and input leak issues
  - Fixed Ctrl+F consistency - works from any focus state
  - Closed GitHub issues #2, #3, #4, #5, #6
- **November 16, 2025**: Added documentation for improved rendering logic
  - Blocking data stream architecture (CSI fix)
  - Window resize handling improvements
  - Updated completed tasks list

---

*This document is maintained by Claude Code instances working on this project. Update it whenever you discover new insights or solutions.*
