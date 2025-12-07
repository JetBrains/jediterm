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
}
