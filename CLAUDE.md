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

## Known Issues & Todos

### In Progress
1. Some symbols still rendering as ∅∅ boxes (partial font success)
2. Terminal scrolling performance could be improved
3. Need to test with different Nerd Font variants

### Completed (Recent → Oldest)
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
November 16, 2025 7:30 PM PST

### Recent Changes
- **November 16, 2025**: Added documentation for improved rendering logic
  - Blocking data stream architecture (CSI fix)
  - Window resize handling improvements
  - Updated completed tasks list

---

*This document is maintained by Claude Code instances working on this project. Update it whenever you discover new insights or solutions.*
