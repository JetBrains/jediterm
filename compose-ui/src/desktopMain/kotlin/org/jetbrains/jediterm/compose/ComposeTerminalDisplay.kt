package org.jetbrains.jediterm.compose

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.State
import com.jediterm.core.util.TermSize
import com.jediterm.terminal.CursorShape
import com.jediterm.terminal.TerminalDisplay
import com.jediterm.terminal.emulator.mouse.MouseFormat
import com.jediterm.terminal.emulator.mouse.MouseMode
import com.jediterm.terminal.model.TerminalSelection

/**
 * Compose implementation of TerminalDisplay interface.
 * This class receives callbacks from JediTerminal and updates Compose state.
 */
class ComposeTerminalDisplay : TerminalDisplay {
    private val _cursorX = mutableStateOf(0)
    private val _cursorY = mutableStateOf(0)
    private val _cursorVisible = mutableStateOf(true)
    private val _cursorShape = mutableStateOf<CursorShape?>(null)
    private val _bracketedPasteMode = mutableStateOf(false)
    private val _termSize = mutableStateOf(TermSize(80, 24))
    private var _windowTitle = ""

    // Compose state properties
    val cursorX: State<Int> = _cursorX
    val cursorY: State<Int> = _cursorY
    val cursorVisible: State<Boolean> = _cursorVisible
    val cursorShape: State<CursorShape?> = _cursorShape
    val bracketedPasteMode: State<Boolean> = _bracketedPasteMode
    val termSize: State<TermSize> = _termSize

    // Trigger for redraw - increment this to force redraw
    private val _redrawTrigger = mutableStateOf(0)
    val redrawTrigger: State<Int> = _redrawTrigger

    override fun setCursor(x: Int, y: Int) {
        _cursorX.value = x
        _cursorY.value = y
    }

    override fun setCursorShape(cursorShape: CursorShape?) {
        _cursorShape.value = cursorShape
    }

    override fun setCursorVisible(visible: Boolean) {
        _cursorVisible.value = visible
    }

    override fun beep() {
        // No-op for now - could play a system beep sound
    }

    override fun scrollArea(scrollRegionTop: Int, scrollRegionBottom: Int, dy: Int) {
        // Trigger redraw when scrolling happens
        _redrawTrigger.value += 1
    }

    override fun useAlternateScreenBuffer(useAlternateScreenBuffer: Boolean) {
        // No-op for now - alternate screen buffer handling could be added later
    }

    override fun getWindowTitle(): String {
        return _windowTitle
    }

    override fun setWindowTitle(windowTitle: String) {
        _windowTitle = windowTitle
    }

    override fun getSelection(): TerminalSelection? {
        // No selection support yet
        return null
    }

    override fun terminalMouseModeSet(mouseMode: MouseMode) {
        // No-op for now - mouse mode handling could be added later
    }

    override fun setMouseFormat(mouseFormat: MouseFormat) {
        // No-op for now - mouse format handling could be added later
    }

    override fun ambiguousCharsAreDoubleWidth(): Boolean {
        // Default to false
        return false
    }

    override fun setBracketedPasteMode(enabled: Boolean) {
        _bracketedPasteMode.value = enabled
    }

    /**
     * Trigger a redraw of the terminal
     */
    fun requestRedraw() {
        _redrawTrigger.value += 1
    }
}
