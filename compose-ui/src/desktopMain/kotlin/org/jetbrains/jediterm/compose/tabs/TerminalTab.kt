package org.jetbrains.jediterm.compose.tabs

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.jetbrains.jediterm.compose.ComposeTerminalDisplay
import org.jetbrains.jediterm.compose.ConnectionState
import org.jetbrains.jediterm.compose.PlatformServices
import org.jetbrains.jediterm.compose.demo.BlockingTerminalDataStream
import org.jetbrains.jediterm.compose.features.ContextMenuController
import org.jetbrains.jediterm.compose.hyperlinks.Hyperlink
import org.jetbrains.jediterm.compose.ime.IMEState
import com.jediterm.terminal.emulator.JediEmulator
import com.jediterm.terminal.model.JediTerminal
import com.jediterm.terminal.model.TerminalTextBuffer
import org.jetbrains.jediterm.compose.debug.DebugDataCollector
import java.util.UUID

/**
 * Represents a single terminal tab with its own terminal session, process, and UI state.
 *
 * This encapsulates all per-tab state that was previously managed in ProperTerminal's
 * remember {} blocks, enabling multiple independent terminal sessions in the same window.
 *
 * Architecture:
 * - Each tab has its own complete terminal stack: PTY → BlockingTerminalDataStream →
 *   JediEmulator → JediTerminal → TerminalTextBuffer → ComposeTerminalDisplay
 * - Independent coroutine scope for managing background jobs (emulator processing,
 *   PTY output reading, process monitoring)
 * - Separate UI state (selection, search, scrolling, IME, context menu)
 * - Working directory tracking via OSC 7 for directory inheritance
 */
data class TerminalTab(
    /**
     * Unique identifier for this tab (UUID).
     */
    val id: String = UUID.randomUUID().toString(),

    /**
     * Display title shown in the tab bar (mutable, can be updated based on shell activity).
     * Default format: "Shell 1", "Shell 2", etc.
     */
    val title: MutableState<String>,

    // === Core Terminal Components ===

    /**
     * The main terminal instance that handles terminal operations and rendering.
     */
    val terminal: JediTerminal,

    /**
     * The text buffer that stores terminal content and scrollback history.
     */
    val textBuffer: TerminalTextBuffer,

    /**
     * The display adapter that handles terminal rendering and redraw requests.
     */
    val display: ComposeTerminalDisplay,

    /**
     * Blocking data stream that feeds character data to the emulator.
     * CRITICAL: Must be long-lived to prevent CSI sequence truncation.
     */
    val dataStream: BlockingTerminalDataStream,

    /**
     * The terminal emulator that processes escape sequences and terminal protocols.
     * CRITICAL: Must be long-lived (stateful) to preserve emulator state across chunks.
     */
    val emulator: JediEmulator,

    // === Process Management ===

    /**
     * Handle to the shell process (PTY). Null during initialization.
     */
    val processHandle: MutableState<PlatformServices.ProcessService.ProcessHandle?>,

    /**
     * Current working directory of the shell, tracked via OSC 7 sequences.
     * Used for inheriting CWD when creating new tabs.
     */
    val workingDirectory: MutableState<String?>,

    /**
     * Connection state of the terminal (Initializing, Connected, Disconnected).
     */
    val connectionState: MutableState<ConnectionState>,

    /**
     * Callback invoked when the shell process exits.
     * Called by TabController before auto-closing the tab.
     * Can be used for cleanup, logging, or custom exit handling.
     */
    val onProcessExit: (() -> Unit)? = null,

    // === Coroutine Management ===

    /**
     * Coroutine scope for this tab's background jobs:
     * - Emulator processing (Dispatchers.Default)
     * - PTY output reading (Dispatchers.IO)
     * - Process exit monitoring (Dispatchers.IO)
     *
     * Cancelled when tab is closed to clean up resources.
     */
    val coroutineScope: CoroutineScope,

    // === UI State ===

    /**
     * Whether this tab currently has keyboard focus.
     */
    val isFocused: MutableState<Boolean>,

    /**
     * Current scroll offset in lines from the bottom of the buffer.
     */
    val scrollOffset: MutableState<Int>,

    /**
     * Whether the search bar is visible for this tab.
     */
    val searchVisible: MutableState<Boolean>,

    /**
     * Current search query text.
     */
    val searchQuery: MutableState<String>,

    /**
     * List of search match positions (row, column) in the terminal buffer.
     */
    val searchMatches: MutableState<List<Pair<Int, Int>>>,

    /**
     * Current search match index (for next/previous navigation).
     */
    val currentSearchMatchIndex: MutableState<Int>,

    /**
     * Selection start position (row, column) or null if no selection.
     */
    val selectionStart: MutableState<Pair<Int, Int>?>,

    /**
     * Selection end position (row, column) or null if no selection.
     */
    val selectionEnd: MutableState<Pair<Int, Int>?>,

    /**
     * Selection clipboard for X11 emulation mode (copy-on-select).
     */
    val selectionClipboard: MutableState<String?>,

    /**
     * IME (Input Method Editor) state for CJK input support.
     */
    val imeState: IMEState,

    /**
     * Context menu controller for right-click menu.
     */
    val contextMenuController: ContextMenuController,

    /**
     * Detected hyperlinks in the terminal buffer with their positions and URLs.
     */
    val hyperlinks: MutableState<List<Hyperlink>>,

    /**
     * Currently hovered hyperlink (for cursor styling and click handling).
     */
    val hoveredHyperlink: MutableState<Hyperlink?>,

    // === Debug Tools ===

    /**
     * Whether debug mode is enabled for this tab.
     * When enabled, I/O data is captured for visualization in the debug panel.
     * This controls data collection (background), not UI visibility.
     */
    val debugEnabled: MutableState<Boolean> = mutableStateOf(false),

    /**
     * Whether the debug panel UI is currently visible.
     * Defaults to false even when debugEnabled is true (toggled with Cmd/Ctrl+Shift+D).
     */
    val debugPanelVisible: MutableState<Boolean> = mutableStateOf(false),

    /**
     * Debug data collector for capturing I/O chunks and terminal state snapshots.
     * Null when debug mode is disabled to avoid memory overhead.
     */
    val debugCollector: DebugDataCollector? = null
) {
    /**
     * Whether this tab is currently rendering to the UI.
     * False for background tabs (still processing output, but UI updates paused).
     * Thread-safe: Uses MutableState for safe access from multiple coroutines.
     */
    val isVisible: MutableState<Boolean> = mutableStateOf(false)

    /**
     * Lifecycle callback invoked when this tab becomes visible (user switches to it).
     * Note: Redraw optimization is already implemented via Phase 2 adaptive debouncing.
     * TabController checks isVisible flag to skip redraws for hidden tabs.
     */
    fun onVisible() {
        isVisible.value = true
    }

    /**
     * Lifecycle callback invoked when this tab becomes hidden (user switches away).
     * Note: Redraw optimization is already implemented via Phase 2 adaptive debouncing.
     * TabController checks isVisible flag to skip redraws for hidden tabs.
     */
    fun onHidden() {
        isVisible.value = false
    }

    /**
     * Clean up resources when closing this tab.
     * - Cancels all coroutines
     * - Releases terminal resources
     *
     * Note: Process termination is handled by TabController.closeTab() to prevent
     * potential GC issues where the tab might be collected before kill() completes.
     */
    fun dispose() {
        // Cancel all coroutines in this scope
        coroutineScope.cancel()

        // Terminal cleanup (if needed)
        // terminal.close() may not be available in all JediTerm versions
    }

    /**
     * Paste text with proper bracketed paste mode handling.
     * When bracketed paste mode is enabled by the terminal application,
     * wraps the text with ESC[200~ and ESC[201~ escape sequences.
     *
     * Also normalizes newlines: CRLF/LF → CR (terminal standard).
     *
     * @param text The text to paste from clipboard
     */
    fun pasteText(text: String) {
        if (text.isEmpty()) return

        // Normalize newlines: CRLF/LF → CR (terminal standard)
        var normalized = text.replace("\r\n", "\n").replace('\n', '\r')

        // Wrap with bracketed paste sequences if mode enabled
        if (display.bracketedPasteMode.value) {
            normalized = "\u001b[200~$normalized\u001b[201~"
        }

        writeUserInput(normalized)
    }

    /**
     * Write user input to the process and record in debug collector.
     * Centralizes input handling to ensure all user input is captured for debugging.
     *
     * @param text The text to send to the shell
     */
    fun writeUserInput(text: String) {
        // Record in debug collector
        debugCollector?.recordChunk(text, org.jetbrains.jediterm.compose.debug.ChunkSource.USER_INPUT)

        // Send to process
        kotlinx.coroutines.runBlocking {
            processHandle.value?.write(text)
        }
    }
}

// Hyperlink data class is already defined in org.jetbrains.jediterm.compose.hyperlinks package
