package ai.rever.bossterm.terminal.model

import ai.rever.bossterm.terminal.TextStyle
import java.util.*
import kotlin.concurrent.Volatile

class StyleState {
    @Volatile
    var current: TextStyle = TextStyle.Companion.EMPTY

    @Volatile
    private var myDefaultStyle: TextStyle = TextStyle.Companion.EMPTY

    fun reset() {
        this.current = myDefaultStyle
    }

    fun setDefaultStyle(defaultStyle: TextStyle) {
        myDefaultStyle = defaultStyle
    }

    val defaultBackground: ai.rever.bossterm.terminal.TerminalColor
        get() = Objects.requireNonNull<ai.rever.bossterm.terminal.TerminalColor?>(myDefaultStyle.background)

    val defaultForeground: ai.rever.bossterm.terminal.TerminalColor
        get() = Objects.requireNonNull<ai.rever.bossterm.terminal.TerminalColor?>(myDefaultStyle.foreground)
}
