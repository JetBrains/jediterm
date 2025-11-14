package com.jediterm.terminal.model

import java.awt.Color

class TerminalColor(val color: Color) {
    companion object {
        val BLACK = TerminalColor(Color.BLACK)
        val WHITE = TerminalColor(Color.WHITE)
    }
}
