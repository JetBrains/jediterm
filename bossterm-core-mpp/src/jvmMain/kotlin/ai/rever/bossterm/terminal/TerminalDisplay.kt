package ai.rever.bossterm.terminal

import ai.rever.bossterm.core.Color
import ai.rever.bossterm.core.util.TermSize
import ai.rever.bossterm.terminal.emulator.mouse.MouseFormat
import ai.rever.bossterm.terminal.emulator.mouse.MouseMode
import ai.rever.bossterm.terminal.model.TerminalSelection

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

    var iconTitle: String?

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
