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

    /**
     * Set progress bar state for the terminal.
     * Used by OSC 1337;SetProgress (iTerm2) and OSC 9;4 (Windows Terminal).
     *
     * @param state Progress state: HIDDEN, NORMAL, ERROR, INDETERMINATE, WARNING
     * @param progress Progress percentage (0-100), or -1 for indeterminate
     */
    fun setProgress(state: ProgressState, progress: Int) {}

    /**
     * Progress bar states matching Windows Terminal/ConEmu conventions.
     */
    enum class ProgressState {
        HIDDEN,        // No progress bar (state 0 or "end")
        NORMAL,        // Normal progress (state 1 or "progress")
        ERROR,         // Error state - red (state 2)
        INDETERMINATE, // Indeterminate/pulsing (state 3)
        WARNING        // Warning state - yellow (state 4)
    }

    val windowForeground: Color?
        get() = null

    val windowBackground: Color?
        get() = null

    var cursorColor: Color?
        get() = null
        set(color) {}
}
