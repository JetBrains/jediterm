package org.jetbrains.jediterm.compose.actions

import androidx.compose.runtime.MutableState
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.text.AnnotatedString
import com.jediterm.terminal.model.TerminalTextBuffer
import com.jediterm.terminal.util.CharUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.jediterm.compose.ComposeTerminalDisplay
import org.jetbrains.jediterm.compose.PlatformServices
import org.jetbrains.jediterm.compose.ime.IMEState

/**
 * Creates all built-in terminal actions.
 * These actions are extracted from ProperTerminal.kt's keyboard handling.
 *
 * @param selectionStart Mutable state for selection start position (col, row)
 * @param selectionEnd Mutable state for selection end position (col, row)
 * @param textBuffer The terminal text buffer
 * @param clipboardManager The clipboard manager for copy/paste
 * @param writeUserInput Lambda that writes user input to the terminal (records in debug collector)
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
    textBuffer: TerminalTextBuffer,
    clipboardManager: ClipboardManager,
    writeUserInput: (String) -> Unit,
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
                val selectedText = extractSelectedText(textBuffer, start, end)
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
    // Pastes clipboard text to the terminal
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
                    writeUserInput(text)
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

    // SELECT_ALL - Ctrl/Cmd+A
    // Selects all text in the terminal (history + screen)
    val selectAllAction = TerminalAction(
        id = "select_all",
        name = "Select All",
        keyStrokes = listOf(
            KeyStroke(key = Key.A, ctrl = true),  // Windows/Linux
            KeyStroke(key = Key.A, meta = true)   // macOS
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
 * @return The selected text with newlines between rows
 */
private fun extractSelectedText(
    textBuffer: TerminalTextBuffer,
    start: Pair<Int, Int>,
    end: Pair<Int, Int>
): String {
    val (startCol, startRow) = start
    val (endCol, endRow) = end

    // Normalize selection to handle backwards dragging
    val (minRow, maxRow) = if (startRow < endRow) {
        startRow to endRow
    } else {
        endRow to startRow
    }

    val (minCol, maxCol) = if (startRow == endRow) {
        // Same row - compare columns
        if (startCol < endCol) startCol to endCol else endCol to startCol
    } else {
        // Different rows - use natural order
        if (startRow < endRow) startCol to endCol else endCol to startCol
    }

    textBuffer.lock()
    return try {
        val result = StringBuilder()

        for (row in minRow..maxRow) {
            val line = textBuffer.getLine(row) ?: continue

            val colStart = if (row == minRow) minCol else 0
            val colEnd = if (row == maxRow) maxCol else (textBuffer.width - 1)

            for (col in colStart..colEnd) {
                if (col < textBuffer.width) {
                    val char = line.charAt(col)
                    // Skip DWC markers
                    if (char != CharUtils.DWC) {
                        result.append(char)
                    }
                }
            }

            // Add newline between rows (except after last row)
            if (row < maxRow) {
                result.append('\n')
            }
        }

        result.toString()
    } finally {
        textBuffer.unlock()
    }
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
 *
 * @param registry The ActionRegistry to add actions to
 * @param onNewTab Callback to create a new tab
 * @param onCloseTab Callback to close the current tab
 * @param onNextTab Callback to switch to the next tab
 * @param onPreviousTab Callback to switch to the previous tab
 * @param onSwitchToTab Callback to switch to a specific tab by index (0-based)
 * @param isMacOS Whether running on macOS (affects modifier keys)
 */
fun addTabManagementActions(
    registry: ActionRegistry,
    onNewTab: () -> Unit,
    onCloseTab: () -> Unit,
    onNextTab: () -> Unit,
    onPreviousTab: () -> Unit,
    onSwitchToTab: (Int) -> Unit,
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
