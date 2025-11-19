package com.jediterm.terminal

import com.jediterm.core.Color
import com.jediterm.core.util.TermSize
import com.jediterm.terminal.emulator.mouse.MouseFormat
import com.jediterm.terminal.emulator.mouse.MouseMode
import com.jediterm.terminal.model.TerminalSelection

interface TerminalDisplay {
    fun setCursor(x: Int, y: Int)

    /**
     * Sets cursor shape, null means default.
     */
    fun setCursorShape(cursorShape: CursorShape?)

    fun beep()

    fun onResize(newTermSize: TermSize, origin: RequestOrigin) {}

    fun scrollArea(scrollRegionTop: Int, scrollRegionSize: Int, dy: Int)

    fun setCursorVisible(isCursorVisible: Boolean)

    fun useAlternateScreenBuffer(useAlternateScreenBuffer: Boolean)

    var windowTitle: String?

    val selection: TerminalSelection?

    fun terminalMouseModeSet(mouseMode: MouseMode)

    fun setMouseFormat(mouseFormat: MouseFormat)

    fun ambiguousCharsAreDoubleWidth(): Boolean

    fun setBracketedPasteMode(bracketedPasteModeEnabled: Boolean) {}

    val windowForeground: Color?
        get() = null

    val windowBackground: Color?
        get() = null

    var cursorColor: Color?
        get() = null
        set(color) {}
}
