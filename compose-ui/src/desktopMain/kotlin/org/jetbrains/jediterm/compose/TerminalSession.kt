package org.jetbrains.jediterm.compose

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import com.jediterm.core.typeahead.TerminalTypeAheadManager
import com.jediterm.terminal.model.JediTerminal
import com.jediterm.terminal.model.TerminalTextBuffer
import org.jetbrains.jediterm.compose.debug.DebugDataCollector
import org.jetbrains.jediterm.compose.features.ContextMenuController
import org.jetbrains.jediterm.compose.hyperlinks.Hyperlink
import org.jetbrains.jediterm.compose.hyperlinks.HyperlinkHoverConsumer
import org.jetbrains.jediterm.compose.ime.IMEState
import org.jetbrains.jediterm.compose.typeahead.ComposeTypeAheadModel

/**
 * Interface representing a terminal session abstraction.
 *
 * This interface provides a clean contract for terminal sessions independent of
 * their concrete representation (tabs, split panes, embedded terminals, etc.).
 *
 * Benefits:
 * - **Testability**: Easy to mock sessions for UI testing
 * - **Modularity**: Clear contract independent of "tab" representation
 * - **Future flexibility**: Could have non-tab sessions (e.g., split panes)
 *
 * @see org.jetbrains.jediterm.compose.tabs.TerminalTab for the primary implementation
 */
interface TerminalSession {

    // === Identity ===

    /**
     * Unique identifier for this session.
     */
    val id: String

    /**
     * Display title for this session (mutable, updated based on shell activity).
     */
    val title: MutableState<String>

    // === Core Terminal Components ===

    /**
     * The main terminal instance that handles terminal operations.
     */
    val terminal: JediTerminal

    /**
     * The text buffer that stores terminal content and scrollback history.
     */
    val textBuffer: TerminalTextBuffer

    /**
     * The display adapter that handles terminal rendering and redraw requests.
     */
    val display: ComposeTerminalDisplay

    // === Process State ===

    /**
     * Handle to the shell process. Null during initialization.
     */
    val processHandle: MutableState<PlatformServices.ProcessService.ProcessHandle?>

    /**
     * Connection state of the terminal (Initializing, Connected, Disconnected).
     */
    val connectionState: MutableState<ConnectionState>

    /**
     * Current working directory of the shell (tracked via OSC 7).
     */
    val workingDirectory: MutableState<String?>

    // === UI State ===

    /**
     * Whether this session currently has keyboard focus.
     */
    val isFocused: MutableState<Boolean>

    /**
     * Whether this session is currently visible (rendering to UI).
     */
    val isVisible: MutableState<Boolean>

    /**
     * Current scroll offset in lines from the bottom of the buffer.
     */
    val scrollOffset: MutableState<Int>

    // === Search State ===

    /**
     * Whether the search bar is visible for this session.
     */
    val searchVisible: MutableState<Boolean>

    /**
     * Current search query text.
     */
    val searchQuery: MutableState<String>

    /**
     * List of search match positions (row, column) in the terminal buffer.
     */
    val searchMatches: MutableState<List<Pair<Int, Int>>>

    /**
     * Current search match index (for next/previous navigation).
     */
    val currentSearchMatchIndex: MutableState<Int>

    // === Selection State ===

    /**
     * Selection start position (row, column) or null if no selection.
     */
    val selectionStart: MutableState<Pair<Int, Int>?>

    /**
     * Selection end position (row, column) or null if no selection.
     */
    val selectionEnd: MutableState<Pair<Int, Int>?>

    /**
     * Selection clipboard for X11 emulation mode (copy-on-select).
     */
    val selectionClipboard: MutableState<String?>

    /**
     * Current selection mode (NORMAL for line-based, BLOCK for rectangular).
     */
    val selectionMode: MutableState<SelectionMode>

    // === Input Method (IME) ===

    /**
     * IME (Input Method Editor) state for CJK input support.
     */
    val imeState: IMEState

    // === Context Menu ===

    /**
     * Context menu controller for right-click menu.
     */
    val contextMenuController: ContextMenuController

    // === Hyperlinks ===

    /**
     * Detected hyperlinks in the terminal buffer with their positions and URLs.
     */
    val hyperlinks: MutableState<List<Hyperlink>>

    /**
     * Currently hovered hyperlink (for cursor styling and click handling).
     */
    val hoveredHyperlink: MutableState<Hyperlink?>

    /**
     * Read-only view of registered hover consumers.
     */
    val hoverConsumers: List<HyperlinkHoverConsumer>

    /**
     * Register a hover consumer to receive hyperlink hover events.
     */
    fun addHoverConsumer(consumer: HyperlinkHoverConsumer)

    /**
     * Unregister a hover consumer.
     */
    fun removeHoverConsumer(consumer: HyperlinkHoverConsumer)

    // === Debug Tools ===

    /**
     * Whether debug mode is enabled for this session.
     */
    val debugEnabled: MutableState<Boolean>

    /**
     * Whether the debug panel UI is currently visible.
     */
    val debugPanelVisible: MutableState<Boolean>

    /**
     * Debug data collector for capturing I/O chunks and terminal state snapshots.
     * Null when debug mode is disabled.
     */
    val debugCollector: DebugDataCollector?

    // === Type-Ahead Prediction ===

    /**
     * Type-ahead terminal model for applying predictions to the buffer.
     * Null when type-ahead is disabled.
     */
    val typeAheadModel: ComposeTypeAheadModel?

    /**
     * Type-ahead manager that tracks predictions and latency statistics.
     * Null when type-ahead is disabled.
     */
    val typeAheadManager: TerminalTypeAheadManager?

    // === Lifecycle Methods ===

    /**
     * Lifecycle callback invoked when this session becomes visible.
     */
    fun onVisible()

    /**
     * Lifecycle callback invoked when this session becomes hidden.
     */
    fun onHidden()

    /**
     * Clean up resources when closing this session.
     */
    fun dispose()

    // === Input Methods ===

    /**
     * Paste text with proper bracketed paste mode handling.
     *
     * @param text The text to paste from clipboard
     */
    fun pasteText(text: String)

    /**
     * Write user input to the process and record in debug collector.
     *
     * @param text The text to send to the shell
     */
    fun writeUserInput(text: String)
}
