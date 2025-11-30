package org.jetbrains.jediterm.compose.typeahead

import com.jediterm.core.typeahead.TypeAheadTerminalModel
import com.jediterm.core.typeahead.TypeAheadTerminalModel.LineWithCursorX
import com.jediterm.core.typeahead.TypeAheadTerminalModel.ShellType
import com.jediterm.terminal.TextStyle
import com.jediterm.terminal.TerminalColor
import com.jediterm.terminal.model.CharBuffer
import com.jediterm.terminal.model.JediTerminal
import com.jediterm.terminal.model.TerminalLine
import com.jediterm.terminal.model.TerminalModelListener
import com.jediterm.terminal.model.TerminalTextBuffer
import org.jetbrains.jediterm.compose.ComposeTerminalDisplay
import org.jetbrains.jediterm.compose.settings.TerminalSettings
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Compose-specific implementation of TypeAheadTerminalModel.
 *
 * Uses TerminalLine.myTypeAheadLine shadow copies for predictions (same pattern as Swing).
 * Predictions are rendered with dimmed gray style to distinguish from actual terminal output.
 */
class ComposeTypeAheadModel(
    private val terminal: JediTerminal,
    private val textBuffer: TerminalTextBuffer,
    private val display: ComposeTerminalDisplay,
    private val settings: TerminalSettings
) : TypeAheadTerminalModel {

    private var _shellType: ShellType = ShellType.Unknown
    private var isPredictionsApplied = false
    private val typeAheadListeners = CopyOnWriteArrayList<TerminalModelListener>()

    // Dimmed gray style for predicted text (ANSI color 8 = bright black/dark gray)
    private val typeAheadStyle = TextStyle(TerminalColor.index(8), null)

    override fun insertCharacter(ch: Char, index: Int) {
        isPredictionsApplied = true
        val typeAheadLine = getTypeAheadLine()
        typeAheadLine.insertString(index, CharBuffer(ch, 1), typeAheadStyle)
        setTypeAheadLine(typeAheadLine)
    }

    override fun removeCharacters(from: Int, count: Int) {
        isPredictionsApplied = true
        val typeAheadLine = getTypeAheadLine()
        typeAheadLine.deleteCharacters(from, count, TextStyle.EMPTY)
        setTypeAheadLine(typeAheadLine)
    }

    override fun moveCursor(index: Int) {
        // Cursor position is managed by cursorX property in TerminalTypeAheadManager
        // This is a no-op similar to Swing implementation
    }

    override fun forceRedraw() {
        fireTypeAheadModelChangeEvent()
    }

    override fun clearPredictions() {
        if (isPredictionsApplied) {
            textBuffer.clearTypeAheadPredictions()
        }
        isPredictionsApplied = false
    }

    override fun lock() {
        textBuffer.lock()
    }

    override fun unlock() {
        textBuffer.unlock()
    }

    // Kotlin property implementations for TypeAheadTerminalModel interface
    override val isUsingAlternateBuffer: Boolean
        get() = textBuffer.isUsingAlternateBuffer

    override val isTypeAheadEnabled: Boolean
        get() = settings.typeAheadEnabled

    override val latencyThreshold: Long
        get() = settings.typeAheadLatencyThresholdNanos

    override val shellType: ShellType?
        get() = _shellType

    fun setShellType(type: ShellType) {
        _shellType = type
    }

    override val currentLineWithCursor: LineWithCursorX
        get() {
            val line = textBuffer.getLine(terminal.cursorY - 1)
            return LineWithCursorX(StringBuffer(line.text), terminal.cursorX - 1)
        }

    override val terminalWidth: Int
        get() = terminal.terminalWidth

    /**
     * Add a listener to be notified when type-ahead predictions change.
     */
    fun addTypeAheadModelListener(listener: TerminalModelListener) {
        typeAheadListeners.add(listener)
    }

    /**
     * Remove a type-ahead model listener.
     */
    fun removeTypeAheadModelListener(listener: TerminalModelListener) {
        typeAheadListeners.remove(listener)
    }

    private fun getTypeAheadLine(): TerminalLine {
        var line = textBuffer.getLine(terminal.cursorY - 1)
        // If there's already a type-ahead line, use it as the base
        line.myTypeAheadLine?.let { line = it }
        return line.copy()
    }

    private fun setTypeAheadLine(typeAheadLine: TerminalLine) {
        val currentLine = textBuffer.getLine(terminal.cursorY - 1)
        currentLine.myTypeAheadLine = typeAheadLine
    }

    private fun fireTypeAheadModelChangeEvent() {
        for (listener in typeAheadListeners) {
            listener.modelChanged()
        }
        // Also trigger display redraw
        display.requestImmediateRedraw()
    }
}
