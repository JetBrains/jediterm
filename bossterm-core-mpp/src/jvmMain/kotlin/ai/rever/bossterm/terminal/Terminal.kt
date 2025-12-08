package ai.rever.bossterm.terminal

import ai.rever.bossterm.core.Color
import ai.rever.bossterm.core.util.CellPosition
import ai.rever.bossterm.core.util.TermSize
import ai.rever.bossterm.terminal.emulator.mouse.MouseFormat
import ai.rever.bossterm.terminal.emulator.mouse.MouseMode
import ai.rever.bossterm.terminal.model.StyleState
import ai.rever.bossterm.terminal.model.CommandStateListener
import ai.rever.bossterm.terminal.model.TerminalApplicationTitleListener
import ai.rever.bossterm.terminal.model.TerminalResizeListener
import java.io.UnsupportedEncodingException

/**
 * Executes terminal commands interpreted by [ai.rever.bossterm.terminal.emulator.Emulator], receives text
 *
 * @author traff
 */
interface Terminal {
    fun resize(newTermSize: TermSize, origin: RequestOrigin)

    fun beep()

    /**
     * Set progress bar state.
     * Used by OSC 1337;SetProgress (iTerm2) and OSC 9;4 (Windows Terminal).
     */
    fun setProgress(state: TerminalDisplay.ProgressState, progress: Int) {}

    fun backspace()

    fun horizontalTab()

    fun carriageReturn()

    fun newLine()

    fun mapCharsetToGL(num: Int)

    fun mapCharsetToGR(num: Int)

    fun designateCharacterSet(tableNumber: Int, ch: Char)

    fun setAnsiConformanceLevel(level: Int)

    @Throws(UnsupportedEncodingException::class)
    fun writeDoubleByte(bytes: CharArray?)

    fun writeCharacters(string: String?)

    /**
     * REP - Repeat the preceding graphic character count times.
     * CSI Ps b
     */
    fun repeatPrecedingCharacter(count: Int)

    fun distanceToLineEnd(): Int

    fun reverseIndex()

    fun index()

    fun nextLine()

    fun fillScreen(c: Char)

    fun saveCursor()

    fun restoreCursor()

    fun reset(clearScrollBackBuffer: Boolean)

    fun characterAttributes(textStyle: TextStyle?)

    fun setCharacterProtection(enabled: Boolean)

    fun setScrollingRegion(top: Int, bottom: Int)

    fun scrollUp(count: Int)

    fun scrollDown(count: Int)

    fun resetScrollRegions()

    fun cursorHorizontalAbsolute(x: Int)

    fun linePositionAbsolute(y: Int)

    fun cursorPosition(x: Int, y: Int)

    fun cursorUp(countY: Int)

    fun cursorDown(dY: Int)

    fun cursorForward(dX: Int)

    fun cursorBackward(dX: Int)

    fun cursorShape(shape: CursorShape)

    fun eraseInLine(arg: Int)

    fun selectiveEraseInLine(arg: Int)

    fun deleteCharacters(count: Int)

    fun ambiguousCharsAreDoubleWidth(): Boolean

    val terminalWidth: Int

    val terminalHeight: Int

    val size: TermSize

    fun eraseInDisplay(arg: Int)

    fun selectiveEraseInDisplay(arg: Int)

    fun setModeEnabled(mode: TerminalMode?, enabled: Boolean)

    fun disconnected()

    val cursorX: Int

    val cursorY: Int

    val cursorPosition: CellPosition

    fun singleShiftSelect(num: Int)

    fun setWindowTitle(name: String)

    fun saveWindowTitleOnStack()

    fun restoreWindowTitleFromStack()

    fun setIconTitle(name: String)

    fun saveIconTitleOnStack()

    fun restoreIconTitleFromStack()

    fun clearScreen()

    fun setCursorVisible(visible: Boolean)

    fun useAlternateBuffer(enabled: Boolean)

    fun getCodeForKey(key: Int, modifiers: Int): ByteArray?

    fun setApplicationArrowKeys(enabled: Boolean)

    fun setApplicationKeypad(enabled: Boolean)

    fun setAutoNewLine(enabled: Boolean)

    val styleState: StyleState?

    fun insertLines(count: Int)

    fun deleteLines(count: Int)

    fun eraseCharacters(count: Int)

    fun insertBlankCharacters(count: Int)

    fun clearTabStopAtCursor()

    fun clearAllTabStops()

    fun setTabStopAtCursor()

    fun writeUnwrappedString(string: String?)

    fun setTerminalOutput(terminalOutput: TerminalOutputStream?)

    fun setMouseMode(mode: MouseMode)

    fun setMouseFormat(mouseFormat: MouseFormat?)

    fun setAltSendsEscape(enabled: Boolean)

    fun deviceStatusReport(str: String?)

    fun deviceAttributes(response: ByteArray?)

    fun setLinkUriStarted(uri: String)

    fun setLinkUriFinished()

    fun setBracketedPasteMode(enabled: Boolean)

    val windowForeground: Color?

    val windowBackground: Color?

    var cursorColor: Color?

    // ===== Dynamic Colors (OSC 4, 10-19, 104, 110-119) =====

    /**
     * Set the default foreground color (OSC 10).
     */
    fun setWindowForeground(color: Color) {}

    /**
     * Set the default background color (OSC 11).
     */
    fun setWindowBackground(color: Color) {}

    /**
     * Reset the default foreground color to initial value (OSC 110).
     */
    fun resetWindowForeground() {}

    /**
     * Reset the default background color to initial value (OSC 111).
     */
    fun resetWindowBackground() {}

    /**
     * Reset the cursor color to initial value (OSC 112).
     */
    fun resetCursorColor() {}

    /**
     * Get an ANSI palette color (0-255) (OSC 4 query).
     */
    fun getIndexedColor(index: Int): Color? { return null }

    /**
     * Set an ANSI palette color (0-255) (OSC 4 set).
     */
    fun setIndexedColor(index: Int, color: Color) {}

    /**
     * Reset a specific ANSI palette color to default (OSC 104).
     * If index is null, reset all palette colors.
     */
    fun resetIndexedColor(index: Int?) {}

    /**
     * Add listener for color changes (OSC 4, 10-12, 104, 110-112).
     */
    fun addColorChangeListener(listener: TerminalColorChangeListener) {}

    /**
     * Remove color change listener.
     */
    fun removeColorChangeListener(listener: TerminalColorChangeListener) {}

    fun addApplicationTitleListener(listener: TerminalApplicationTitleListener) {}

    fun removeApplicationTitleListener(listener: TerminalApplicationTitleListener) {}

    fun addResizeListener(listener: TerminalResizeListener) {}

    fun removeResizeListener(listener: TerminalResizeListener) {}

    fun addCustomCommandListener(listener: TerminalCustomCommandListener) {}

    fun removeCustomCommandListener(listener: TerminalCustomCommandListener) {}

    fun processCustomCommand(args: MutableList<String?>) {}

    // ===== Shell Integration (OSC 133) =====

    fun addCommandStateListener(listener: CommandStateListener) {}

    fun removeCommandStateListener(listener: CommandStateListener) {}

    /**
     * Called when shell integration sequence is received (OSC 133).
     * @param type The sequence type: A (prompt), B (command start), C (output end), D (finished)
     * @param args Additional arguments (e.g., exit code for type D)
     */
    fun processShellIntegration(type: Char, args: List<String>) {}

    // ===== Clipboard (OSC 52) =====

    fun addClipboardListener(listener: TerminalClipboardListener) {}

    fun removeClipboardListener(listener: TerminalClipboardListener) {}

    /**
     * Called when OSC 52 clipboard sequence is received.
     * @param selection The clipboard selection ('c' = clipboard, 'p' = primary, 's' = select)
     * @param data The base64-encoded data to set, "?" to query, or empty to clear
     */
    fun processClipboard(selection: Char, data: String) {}
}

/**
 * Listener for OSC 52 clipboard operations.
 */
interface TerminalClipboardListener {
    /**
     * Called when terminal requests to set clipboard content.
     * @param selection The clipboard selection ('c', 'p', 's', or '0'-'7')
     * @param content The decoded text content to set
     */
    fun onClipboardSet(selection: Char, content: String)

    /**
     * Called when terminal requests to read clipboard content.
     * @param selection The clipboard selection
     * @return The current clipboard content, or null if reading is disabled
     */
    fun onClipboardGet(selection: Char): String?

    /**
     * Called when terminal requests to clear clipboard.
     * @param selection The clipboard selection
     */
    fun onClipboardClear(selection: Char)
}

/**
 * Listener for OSC color change operations (OSC 4, 10-12, 104, 110-112).
 */
interface TerminalColorChangeListener {
    /**
     * Called when foreground color is changed (OSC 10).
     */
    fun onForegroundColorChanged(color: Color)

    /**
     * Called when background color is changed (OSC 11).
     */
    fun onBackgroundColorChanged(color: Color)

    /**
     * Called when cursor color is changed (OSC 12).
     */
    fun onCursorColorChanged(color: Color?)

    /**
     * Called when an indexed palette color is changed (OSC 4).
     * @param index Color index (0-255)
     * @param color New color value
     */
    fun onIndexedColorChanged(index: Int, color: Color)

    /**
     * Called when foreground color is reset (OSC 110).
     */
    fun onForegroundColorReset()

    /**
     * Called when background color is reset (OSC 111).
     */
    fun onBackgroundColorReset()

    /**
     * Called when cursor color is reset (OSC 112).
     */
    fun onCursorColorReset()

    /**
     * Called when indexed palette color is reset (OSC 104).
     * @param index Color index to reset, or null to reset all
     */
    fun onIndexedColorReset(index: Int?)
}
