package ai.rever.bossterm.compose.tabs

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import ai.rever.bossterm.compose.ComposeTerminalDisplay
import ai.rever.bossterm.compose.ConnectionState
import ai.rever.bossterm.compose.PlatformServices
import ai.rever.bossterm.compose.TerminalSession
import ai.rever.bossterm.compose.terminal.BlockingTerminalDataStream
import ai.rever.bossterm.compose.features.ContextMenuController
import ai.rever.bossterm.compose.hyperlinks.Hyperlink
import ai.rever.bossterm.compose.hyperlinks.HyperlinkHoverConsumer
import ai.rever.bossterm.compose.ime.IMEState
import ai.rever.bossterm.terminal.emulator.BossEmulator
import ai.rever.bossterm.terminal.model.BossTerminal
import ai.rever.bossterm.terminal.model.TerminalTextBuffer
import ai.rever.bossterm.compose.debug.DebugDataCollector
import ai.rever.bossterm.compose.typeahead.ComposeTypeAheadModel
import ai.rever.bossterm.core.typeahead.TerminalTypeAheadManager
import java.util.UUID

/**
 * Represents a single terminal tab with its own terminal session, process, and UI state.
 *
 * This encapsulates all per-tab state that was previously managed in ProperTerminal's
 * remember {} blocks, enabling multiple independent terminal sessions in the same window.
 *
 * Architecture:
 * - Each tab has its own complete terminal stack: PTY → BlockingTerminalDataStream →
 *   BossEmulator → BossTerminal → TerminalTextBuffer → ComposeTerminalDisplay
 * - Independent coroutine scope for managing background jobs (emulator processing,
 *   PTY output reading, process monitoring)
 * - Separate UI state (selection, search, scrolling, IME, context menu)
 * - Working directory tracking via OSC 7 for directory inheritance
 */
data class TerminalTab(
    /**
     * Unique identifier for this tab (UUID).
     */
    override val id: String = UUID.randomUUID().toString(),

    /**
     * Display title shown in the tab bar (mutable, can be updated based on shell activity).
     * Default format: "Shell 1", "Shell 2", etc.
     */
    override val title: MutableState<String>,

    // === Core Terminal Components ===

    /**
     * The main terminal instance that handles terminal operations and rendering.
     */
    override val terminal: BossTerminal,

    /**
     * The text buffer that stores terminal content and scrollback history.
     */
    override val textBuffer: TerminalTextBuffer,

    /**
     * The display adapter that handles terminal rendering and redraw requests.
     */
    override val display: ComposeTerminalDisplay,

    /**
     * Blocking data stream that feeds character data to the emulator.
     * CRITICAL: Must be long-lived to prevent CSI sequence truncation.
     */
    val dataStream: BlockingTerminalDataStream,

    /**
     * The terminal emulator that processes escape sequences and terminal protocols.
     * CRITICAL: Must be long-lived (stateful) to preserve emulator state across chunks.
     */
    val emulator: BossEmulator,

    // === Process Management ===

    /**
     * Handle to the shell process (PTY). Null during initialization.
     */
    override val processHandle: MutableState<PlatformServices.ProcessService.ProcessHandle?>,

    /**
     * Current working directory of the shell, tracked via OSC 7 sequences.
     * Used for inheriting CWD when creating new tabs.
     */
    override val workingDirectory: MutableState<String?>,

    /**
     * Connection state of the terminal (Initializing, Connected, Disconnected).
     */
    override val connectionState: MutableState<ConnectionState>,

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
    override val isFocused: MutableState<Boolean>,

    /**
     * Current scroll offset in lines from the bottom of the buffer.
     */
    override val scrollOffset: MutableState<Int>,

    /**
     * Whether the search bar is visible for this tab.
     */
    override val searchVisible: MutableState<Boolean>,

    /**
     * Current search query text.
     */
    override val searchQuery: MutableState<String>,

    /**
     * List of search match positions (row, column) in the terminal buffer.
     */
    override val searchMatches: MutableState<List<Pair<Int, Int>>>,

    /**
     * Current search match index (for next/previous navigation).
     */
    override val currentSearchMatchIndex: MutableState<Int>,

    /**
     * Selection start position (row, column) or null if no selection.
     */
    override val selectionStart: MutableState<Pair<Int, Int>?>,

    /**
     * Selection end position (row, column) or null if no selection.
     */
    override val selectionEnd: MutableState<Pair<Int, Int>?>,

    /**
     * Selection clipboard for X11 emulation mode (copy-on-select).
     */
    override val selectionClipboard: MutableState<String?>,

    /**
     * Current selection mode (NORMAL for line-based, BLOCK for rectangular).
     * Defaults to NORMAL. Set to BLOCK when Alt+Drag is detected.
     */
    override val selectionMode: MutableState<ai.rever.bossterm.compose.SelectionMode> = mutableStateOf(ai.rever.bossterm.compose.SelectionMode.NORMAL),

    /**
     * IME (Input Method Editor) state for CJK input support.
     */
    override val imeState: IMEState,

    /**
     * Context menu controller for right-click menu.
     */
    override val contextMenuController: ContextMenuController,

    /**
     * Detected hyperlinks in the terminal buffer with their positions and URLs.
     */
    override val hyperlinks: MutableState<List<Hyperlink>>,

    /**
     * Currently hovered hyperlink (for cursor styling and click handling).
     */
    override val hoveredHyperlink: MutableState<Hyperlink?>,

    // === Debug Tools ===

    /**
     * Whether debug mode is enabled for this tab.
     * When enabled, I/O data is captured for visualization in the debug panel.
     * This controls data collection (background), not UI visibility.
     */
    override val debugEnabled: MutableState<Boolean> = mutableStateOf(false),

    /**
     * Whether the debug panel UI is currently visible.
     * Defaults to false even when debugEnabled is true (toggled with Cmd/Ctrl+Shift+D).
     */
    override val debugPanelVisible: MutableState<Boolean> = mutableStateOf(false),

    /**
     * Debug data collector for capturing I/O chunks and terminal state snapshots.
     * Null when debug mode is disabled to avoid memory overhead.
     */
    override val debugCollector: DebugDataCollector? = null,

    // === Type-Ahead Prediction ===

    /**
     * Type-ahead terminal model for applying predictions to the buffer.
     * Null when type-ahead is disabled.
     */
    override val typeAheadModel: ComposeTypeAheadModel? = null,

    /**
     * Type-ahead manager that tracks predictions and latency statistics.
     * Null when type-ahead is disabled.
     */
    override val typeAheadManager: TerminalTypeAheadManager? = null
) : TerminalSession {
    /**
     * Whether this tab is currently rendering to the UI.
     * False for background tabs (still processing output, but UI updates paused).
     * Thread-safe: Uses MutableState for safe access from multiple coroutines.
     */
    override val isVisible: MutableState<Boolean> = mutableStateOf(false)

    // === Hyperlink Hover Consumers ===

    /**
     * List of registered hover consumers for hyperlink hover events.
     * External clients can register to receive onMouseEntered/onMouseExited callbacks.
     */
    private val _hoverConsumers = mutableListOf<HyperlinkHoverConsumer>()

    /**
     * Read-only view of registered hover consumers.
     */
    override val hoverConsumers: List<HyperlinkHoverConsumer> get() = _hoverConsumers

    /**
     * Register a hover consumer to receive hyperlink hover events.
     * @param consumer The consumer to register
     */
    override fun addHoverConsumer(consumer: HyperlinkHoverConsumer) {
        _hoverConsumers.add(consumer)
    }

    /**
     * Unregister a hover consumer.
     * @param consumer The consumer to remove
     */
    override fun removeHoverConsumer(consumer: HyperlinkHoverConsumer) {
        _hoverConsumers.remove(consumer)
    }

    /**
     * Lifecycle callback invoked when this tab becomes visible (user switches to it).
     * Note: Redraw optimization is already implemented via Phase 2 adaptive debouncing.
     * TabController checks isVisible flag to skip redraws for hidden tabs.
     */
    override fun onVisible() {
        isVisible.value = true
    }

    /**
     * Lifecycle callback invoked when this tab becomes hidden (user switches away).
     * Note: Redraw optimization is already implemented via Phase 2 adaptive debouncing.
     * TabController checks isVisible flag to skip redraws for hidden tabs.
     */
    override fun onHidden() {
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
    override fun dispose() {
        // Cancel all coroutines in this scope
        coroutineScope.cancel()

        // Terminal cleanup (if needed)
        // terminal.close() may not be available in all BossTerm versions
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
    override fun pasteText(text: String) {
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
    override fun writeUserInput(text: String) {
        // Record in debug collector
        debugCollector?.recordChunk(text, ai.rever.bossterm.compose.debug.ChunkSource.USER_INPUT)

        // Send to process
        kotlinx.coroutines.runBlocking {
            processHandle.value?.write(text)
        }
    }
}

// Hyperlink data class is already defined in ai.rever.bossterm.compose.hyperlinks package
