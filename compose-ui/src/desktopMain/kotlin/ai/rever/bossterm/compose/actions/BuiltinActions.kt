package ai.rever.bossterm.compose.actions

import androidx.compose.runtime.MutableState
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.text.AnnotatedString
import ai.rever.bossterm.terminal.model.TerminalTextBuffer
import ai.rever.bossterm.terminal.util.CharUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import ai.rever.bossterm.compose.ComposeTerminalDisplay
import ai.rever.bossterm.compose.PlatformServices
import ai.rever.bossterm.compose.SelectionMode
import ai.rever.bossterm.compose.ime.IMEState

/**
 * Creates all built-in terminal actions.
 * These actions are extracted from ProperTerminal.kt's keyboard handling.
 *
 * @param selectionStart Mutable state for selection start position (col, row)
 * @param selectionEnd Mutable state for selection end position (col, row)
 * @param textBuffer The terminal text buffer
 * @param clipboardManager The clipboard manager for copy/paste
 * @param writeUserInput Lambda that writes user input to the terminal (records in debug collector)
 * @param pasteText Lambda that pastes text with bracketed paste mode handling
 * @param searchVisible Mutable state for search bar visibility
 * @param debugPanelVisible Mutable state for debug panel visibility
 * @param imeState IME state for CJK input
 * @param display Terminal display for requesting redraws
 * @param scope Coroutine scope for async operations
 * @param selectAllCallback Callback for select all action (accesses buffer state)
 * @return ActionRegistry with all built-in actions registered
 */
fun createBuiltinActions(
    selectionStart: MutableState<Pair<Int, Int>?>,
    selectionEnd: MutableState<Pair<Int, Int>?>,
    selectionMode: MutableState<SelectionMode>,
    textBuffer: TerminalTextBuffer,
    clipboardManager: ClipboardManager,
    writeUserInput: (String) -> Unit,
    pasteText: (String) -> Unit,
    searchVisible: MutableState<Boolean>,
    debugPanelVisible: MutableState<Boolean>,
    imeState: IMEState,
    display: ComposeTerminalDisplay,
    scope: CoroutineScope,
    selectAllCallback: () -> Unit,
    isMacOS: Boolean
): ActionRegistry {
    val registry = ActionRegistry(isMacOS)

    // COPY - Ctrl/Cmd+C (only when selection exists)
    // Copies selected text to clipboard
    // If no selection, the event passes through to terminal (for process interrupt)
    val copyAction = TerminalAction(
        id = "copy",
        name = "Copy",
        keyStrokes = listOf(
            KeyStroke(key = Key.C, ctrl = true),  // Windows/Linux
            KeyStroke(key = Key.C, meta = true)   // macOS
        ),
        enabled = { selectionStart.value != null && selectionEnd.value != null },
        handler = { _ ->
            val start = selectionStart.value
            val end = selectionEnd.value
            if (start != null && end != null) {
                val selectedText = extractSelectedText(textBuffer, start, end, selectionMode.value)
                if (selectedText.isNotEmpty()) {
                    clipboardManager.setText(AnnotatedString(selectedText))
                    true  // Consume event
                } else {
                    false  // No text selected, let Ctrl+C pass through
                }
            } else {
                false  // No selection, let Ctrl+C pass through for process interrupt
            }
        }
    )

    // PASTE - Ctrl/Cmd+V
    // Pastes clipboard text to the terminal with bracketed paste mode support
    val pasteAction = TerminalAction(
        id = "paste",
        name = "Paste",
        keyStrokes = listOf(
            KeyStroke(key = Key.V, ctrl = true),  // Windows/Linux
            KeyStroke(key = Key.V, meta = true)   // macOS
        ),
        enabled = { true },
        handler = { _ ->
            val text = clipboardManager.getText()?.text
            if (!text.isNullOrEmpty()) {
                scope.launch {
                    pasteText(text)
                }
                true
            } else {
                false
            }
        }
    )

    // SEARCH - Ctrl/Cmd+F
    // Toggles search bar visibility
    val searchAction = TerminalAction(
        id = "search",
        name = "Search",
        keyStrokes = listOf(
            KeyStroke(key = Key.F, ctrl = true),  // Windows/Linux
            KeyStroke(key = Key.F, meta = true)   // macOS
        ),
        enabled = { true },
        handler = { _ ->
            searchVisible.value = !searchVisible.value
            true
        }
    )

    // CLEAR_SELECTION - Escape
    // Clears text selection if it exists
    val clearSelectionAction = TerminalAction(
        id = "clear_selection",
        name = "Clear Selection",
        keyStroke = KeyStroke(key = Key.Escape),
        enabled = { selectionStart.value != null || selectionEnd.value != null },
        handler = { _ ->
            selectionStart.value = null
            selectionEnd.value = null
            display.requestImmediateRedraw()
            true  // Consume event
        }
    )

    // TOGGLE_IME - Ctrl+Space
    // Toggles IME (Input Method Editor) for CJK input
    val toggleImeAction = TerminalAction(
        id = "toggle_ime",
        name = "Toggle IME",
        keyStroke = KeyStroke(key = Key.Spacebar, ctrl = true),
        enabled = { true },
        handler = { _ ->
            imeState.toggle()
            true
        }
    )

    // SELECT_ALL - Cmd+A (macOS only)
    // Ctrl+A passes through to terminal (tmux prefix, bash beginning-of-line, etc.)
    val selectAllAction = TerminalAction(
        id = "select_all",
        name = "Select All",
        keyStrokes = listOf(
            KeyStroke(key = Key.A, meta = true)   // macOS only, Ctrl+A passes through
        ),
        enabled = { true },
        handler = { _ ->
            selectAllCallback()
            true
        }
    )

    // DEBUG PANEL - Ctrl/Cmd+Shift+D
    // Toggles the debug panel for inspecting terminal I/O and state
    val debugPanelAction = TerminalAction(
        id = "debug_panel",
        name = "Toggle Debug Panel",
        keyStrokes = listOf(
            KeyStroke(key = Key.D, ctrl = true, shift = true),   // Windows/Linux
            KeyStroke(key = Key.D, meta = true, shift = true)    // macOS
        ),
        enabled = { true },
        handler = { _ ->
            debugPanelVisible.value = !debugPanelVisible.value
            true
        }
    )

    // Register all actions
    registry.registerAll(
        copyAction,
        pasteAction,
        searchAction,
        clearSelectionAction,
        toggleImeAction,
        selectAllAction,
        debugPanelAction
    )

    return registry
}

/**
 * Extracts selected text from the terminal buffer.
 * Handles backwards selection, multi-row selection, and DWC (double-width character) markers.
 *
 * @param textBuffer The terminal text buffer
 * @param start Selection start position (col, row)
 * @param end Selection end position (col, row)
 * @param mode Selection mode (NORMAL for line-based, BLOCK for rectangular)
 * @return The selected text with newlines between rows
 */
private fun extractSelectedText(
    textBuffer: TerminalTextBuffer,
    start: Pair<Int, Int>,
    end: Pair<Int, Int>,
    mode: SelectionMode = SelectionMode.NORMAL
): String {
    val (startCol, startRow) = start
    val (endCol, endRow) = end

    // Determine first (earlier row) and last (later row) points
    val (firstCol, firstRow, lastCol, lastRow) = if (startRow <= endRow) {
        listOf(startCol, startRow, endCol, endRow)
    } else {
        listOf(endCol, endRow, startCol, startRow)
    }

    // Create immutable snapshot (fast, <1ms with lock, then lock released)
    // This allows PTY writers to continue during text extraction
    val snapshot = textBuffer.createSnapshot()

    val result = StringBuilder()

    for (row in firstRow..lastRow) {
        val line = snapshot.getLine(row)

        // Calculate column bounds based on selection mode
        val (colStart, colEnd) = when (mode) {
            // BLOCK mode: rectangular selection - same columns for all rows
            SelectionMode.BLOCK -> {
                minOf(firstCol, lastCol) to maxOf(firstCol, lastCol)
            }
            // NORMAL mode: line-based selection
            SelectionMode.NORMAL -> {
                if (firstRow == lastRow) {
                    // Single line: use min/max columns
                    minOf(firstCol, lastCol) to maxOf(firstCol, lastCol)
                } else {
                    // Multi-line: direction-aware columns
                    when (row) {
                        firstRow -> firstCol to (snapshot.width - 1)  // First row: from start col to end
                        lastRow -> 0 to lastCol                        // Last row: from 0 to end col
                        else -> 0 to (snapshot.width - 1)              // Middle rows: full line
                    }
                }
            }
        }

        for (col in colStart..colEnd) {
            if (col < snapshot.width) {
                val char = line.charAt(col)
                // Skip DWC markers
                if (char != CharUtils.DWC) {
                    result.append(char)
                }
            }
        }

        // Add newline between rows (except after last row)
        if (row < lastRow) {
            result.append('\n')
        }
    }

    return result.toString()
}

/**
 * Adds tab management keyboard shortcuts to an existing ActionRegistry.
 * These are global shortcuts that work regardless of terminal focus.
 *
 * Shortcuts:
 * - Cmd/Ctrl+T: New tab
 * - Cmd/Ctrl+W: Close current tab
 * - Ctrl+Tab: Next tab
 * - Ctrl+Shift+Tab: Previous tab
 * - Cmd/Ctrl+1-9: Switch to specific tab (1-based index)
 * - Cmd/Ctrl+N: New window
 *
 * @param registry The ActionRegistry to add actions to
 * @param onNewTab Callback to create a new tab
 * @param onCloseTab Callback to close the current tab
 * @param onNextTab Callback to switch to the next tab
 * @param onPreviousTab Callback to switch to the previous tab
 * @param onSwitchToTab Callback to switch to a specific tab by index (0-based)
 * @param onNewWindow Callback to create a new window
 * @param isMacOS Whether running on macOS (affects modifier keys)
 */
fun addTabManagementActions(
    registry: ActionRegistry,
    onNewTab: () -> Unit,
    onNewPreConnectTab: () -> Unit = {},  // Test pre-connection input
    onCloseTab: () -> Unit,
    onNextTab: () -> Unit,
    onPreviousTab: () -> Unit,
    onSwitchToTab: (Int) -> Unit,
    onNewWindow: () -> Unit = {},
    isMacOS: Boolean
) {
    // NEW TAB - Cmd/Ctrl+T
    registry.register(TerminalAction(
        id = "new_tab",
        name = "New Tab",
        keyStrokes = listOf(
            KeyStroke(key = Key.T, ctrl = true),  // Windows/Linux
            KeyStroke(key = Key.T, meta = true)   // macOS
        ),
        handler = { event ->
            onNewTab()
            true  // Consume event
        }
    ))

    // NEW PRE-CONNECT TAB - Cmd/Ctrl+Shift+T
    // Opens a new tab with pre-connection input prompts (for testing)
    registry.register(TerminalAction(
        id = "new_preconnect_tab",
        name = "New Pre-Connect Tab",
        keyStrokes = listOf(
            KeyStroke(key = Key.T, ctrl = true, shift = true),  // Windows/Linux
            KeyStroke(key = Key.T, meta = true, shift = true)   // macOS
        ),
        handler = { event ->
            onNewPreConnectTab()
            true  // Consume event
        }
    ))

    // NEW WINDOW - Cmd/Ctrl+N
    registry.register(TerminalAction(
        id = "new_window",
        name = "New Window",
        keyStrokes = listOf(
            KeyStroke(key = Key.N, ctrl = true),  // Windows/Linux
            KeyStroke(key = Key.N, meta = true)   // macOS
        ),
        handler = { event ->
            onNewWindow()
            true  // Consume event
        }
    ))

    // CLOSE TAB - Cmd/Ctrl+W
    registry.register(TerminalAction(
        id = "close_tab",
        name = "Close Tab",
        keyStrokes = listOf(
            KeyStroke(key = Key.W, ctrl = true),  // Windows/Linux
            KeyStroke(key = Key.W, meta = true)   // macOS
        ),
        handler = { event ->
            onCloseTab()
            true  // Consume event
        }
    ))

    // NEXT TAB - Ctrl+Tab
    registry.register(TerminalAction(
        id = "next_tab",
        name = "Next Tab",
        keyStrokes = listOf(
            KeyStroke(key = Key.Tab, ctrl = true)  // Both platforms use Ctrl
        ),
        handler = { event ->
            onNextTab()
            true  // Consume event
        }
    ))

    // PREVIOUS TAB - Ctrl+Shift+Tab
    registry.register(TerminalAction(
        id = "previous_tab",
        name = "Previous Tab",
        keyStrokes = listOf(
            KeyStroke(key = Key.Tab, ctrl = true, shift = true)  // Both platforms
        ),
        handler = { event ->
            onPreviousTab()
            true  // Consume event
        }
    ))

    // SWITCH TO TAB 1-9 - Cmd/Ctrl+1 through Cmd/Ctrl+9
    for (i in 1..9) {
        val key = when (i) {
            1 -> Key.One
            2 -> Key.Two
            3 -> Key.Three
            4 -> Key.Four
            5 -> Key.Five
            6 -> Key.Six
            7 -> Key.Seven
            8 -> Key.Eight
            9 -> Key.Nine
            else -> continue
        }

        registry.register(TerminalAction(
            id = "switch_to_tab_$i",
            name = "Switch to Tab $i",
            keyStrokes = listOf(
                KeyStroke(key = key, ctrl = true),  // Windows/Linux
                KeyStroke(key = key, meta = true)   // macOS
            ),
            handler = { event ->
                onSwitchToTab(i - 1)  // Convert to 0-based index
                true  // Consume event
            }
        ))
    }
}

/**
 * Adds split pane management keyboard shortcuts to an existing ActionRegistry.
 *
 * Split Shortcuts (iTerm2 style):
 * - Cmd+D / Ctrl+D: Split pane vertically (left/right)
 * - Cmd+Shift+H / Ctrl+Shift+H: Split pane horizontally (top/bottom)
 * - Cmd+Shift+W / Ctrl+Shift+W: Close current pane
 * - Cmd+Option+Arrow / Ctrl+Alt+Arrow: Navigate between panes directionally
 * - Cmd+] / Ctrl+]: Navigate to next pane
 * - Cmd+[ / Ctrl+[: Navigate to previous pane
 *
 * @param registry The ActionRegistry to add actions to
 * @param onSplitVertical Callback to split pane vertically (left/right)
 * @param onSplitHorizontal Callback to split pane horizontally (top/bottom)
 * @param onClosePane Callback to close the focused pane
 * @param onNavigateUp Callback to navigate focus up
 * @param onNavigateDown Callback to navigate focus down
 * @param onMoveToNewTab Callback to move focused pane to new tab (null to disable)
 * @param onNavigateLeft Callback to navigate focus left
 * @param onNavigateRight Callback to navigate focus right
 * @param onNavigateNext Callback to navigate to next pane (cycles)
 * @param onNavigatePrevious Callback to navigate to previous pane (cycles)
 * @param isMacOS Whether running on macOS (affects modifier keys)
 */
fun addSplitPaneActions(
    registry: ActionRegistry,
    onSplitVertical: () -> Unit,
    onSplitHorizontal: () -> Unit,
    onClosePane: () -> Unit,
    onMoveToNewTab: (() -> Unit)?,
    onNavigateUp: () -> Unit,
    onNavigateDown: () -> Unit,
    onNavigateLeft: () -> Unit,
    onNavigateRight: () -> Unit,
    onNavigateNext: () -> Unit,
    onNavigatePrevious: () -> Unit,
    isMacOS: Boolean
) {
    // SPLIT VERTICAL - Cmd+D (macOS) / Ctrl+D (Windows/Linux)
    // Creates left/right split
    registry.register(TerminalAction(
        id = "split_vertical",
        name = "Split Vertically",
        keyStrokes = listOf(
            KeyStroke(key = Key.D, ctrl = true),  // Windows/Linux
            KeyStroke(key = Key.D, meta = true)   // macOS
        ),
        handler = { _ ->
            onSplitVertical()
            true
        }
    ))

    // SPLIT HORIZONTAL - Cmd+Shift+H (macOS) / Ctrl+Shift+H (Windows/Linux)
    // Creates top/bottom split
    registry.register(TerminalAction(
        id = "split_horizontal",
        name = "Split Horizontally",
        keyStrokes = listOf(
            KeyStroke(key = Key.H, ctrl = true, shift = true),  // Windows/Linux
            KeyStroke(key = Key.H, meta = true, shift = true)   // macOS
        ),
        handler = { _ ->
            onSplitHorizontal()
            true
        }
    ))

    // CLOSE PANE - Cmd+Shift+W (macOS) / Ctrl+Shift+W (Windows/Linux)
    // Closes the focused pane (or tab if last pane)
    registry.register(TerminalAction(
        id = "close_pane",
        name = "Close Pane",
        keyStrokes = listOf(
            KeyStroke(key = Key.W, ctrl = true, shift = true),  // Windows/Linux
            KeyStroke(key = Key.W, meta = true, shift = true)   // macOS
        ),
        handler = { _ ->
            onClosePane()
            true
        }
    ))

    // MOVE TO NEW TAB - Cmd+Shift+M (macOS) / Ctrl+Shift+M (Windows/Linux)
    // Moves the focused pane to a new tab (only when splits exist)
    if (onMoveToNewTab != null) {
        registry.register(TerminalAction(
            id = "move_to_new_tab",
            name = "Move Pane to New Tab",
            keyStrokes = listOf(
                KeyStroke(key = Key.M, ctrl = true, shift = true),  // Windows/Linux
                KeyStroke(key = Key.M, meta = true, shift = true)   // macOS
            ),
            handler = { _ ->
                onMoveToNewTab()
                true
            }
        ))
    }

    // NAVIGATE UP - Cmd+Option+Up (macOS) / Ctrl+Alt+Up (Windows/Linux)
    registry.register(TerminalAction(
        id = "navigate_pane_up",
        name = "Navigate Pane Up",
        keyStrokes = listOf(
            KeyStroke(key = Key.DirectionUp, ctrl = true, alt = true),  // Windows/Linux
            KeyStroke(key = Key.DirectionUp, meta = true, alt = true)   // macOS
        ),
        handler = { _ ->
            onNavigateUp()
            true
        }
    ))

    // NAVIGATE DOWN - Cmd+Option+Down (macOS) / Ctrl+Alt+Down (Windows/Linux)
    registry.register(TerminalAction(
        id = "navigate_pane_down",
        name = "Navigate Pane Down",
        keyStrokes = listOf(
            KeyStroke(key = Key.DirectionDown, ctrl = true, alt = true),  // Windows/Linux
            KeyStroke(key = Key.DirectionDown, meta = true, alt = true)   // macOS
        ),
        handler = { _ ->
            onNavigateDown()
            true
        }
    ))

    // NAVIGATE LEFT - Cmd+Option+Left (macOS) / Ctrl+Alt+Left (Windows/Linux)
    registry.register(TerminalAction(
        id = "navigate_pane_left",
        name = "Navigate Pane Left",
        keyStrokes = listOf(
            KeyStroke(key = Key.DirectionLeft, ctrl = true, alt = true),  // Windows/Linux
            KeyStroke(key = Key.DirectionLeft, meta = true, alt = true)   // macOS
        ),
        handler = { _ ->
            onNavigateLeft()
            true
        }
    ))

    // NAVIGATE RIGHT - Cmd+Option+Right (macOS) / Ctrl+Alt+Right (Windows/Linux)
    registry.register(TerminalAction(
        id = "navigate_pane_right",
        name = "Navigate Pane Right",
        keyStrokes = listOf(
            KeyStroke(key = Key.DirectionRight, ctrl = true, alt = true),  // Windows/Linux
            KeyStroke(key = Key.DirectionRight, meta = true, alt = true)   // macOS
        ),
        handler = { _ ->
            onNavigateRight()
            true
        }
    ))

    // NAVIGATE TO NEXT PANE - Cmd+] (macOS) / Ctrl+] (Windows/Linux)
    registry.register(TerminalAction(
        id = "navigate_next_pane",
        name = "Next Pane",
        keyStrokes = listOf(
            KeyStroke(key = Key.RightBracket, ctrl = true),  // Windows/Linux
            KeyStroke(key = Key.RightBracket, meta = true)   // macOS
        ),
        handler = { _ ->
            onNavigateNext()
            true
        }
    ))

    // NAVIGATE TO PREVIOUS PANE - Cmd+[ (macOS) / Ctrl+[ (Windows/Linux)
    registry.register(TerminalAction(
        id = "navigate_previous_pane",
        name = "Previous Pane",
        keyStrokes = listOf(
            KeyStroke(key = Key.LeftBracket, ctrl = true),  // Windows/Linux
            KeyStroke(key = Key.LeftBracket, meta = true)   // macOS
        ),
        handler = { _ ->
            onNavigatePrevious()
            true
        }
    ))
}
