package com.jediterm.terminal.model

import com.jediterm.terminal.TextStyle
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

    val defaultBackground: com.jediterm.terminal.TerminalColor
        get() = Objects.requireNonNull<com.jediterm.terminal.TerminalColor?>(myDefaultStyle.background)

    val defaultForeground: com.jediterm.terminal.TerminalColor
        get() = Objects.requireNonNull<com.jediterm.terminal.TerminalColor?>(myDefaultStyle.foreground)
}
