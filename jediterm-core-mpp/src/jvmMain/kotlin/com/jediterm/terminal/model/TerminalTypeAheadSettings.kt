package com.jediterm.terminal.model

import com.jediterm.terminal.TerminalColor
import com.jediterm.terminal.TextStyle
import java.util.concurrent.TimeUnit

class TerminalTypeAheadSettings(val isEnabled: Boolean, val latencyThreshold: Long, val typeAheadStyle: TextStyle?) {
    companion object {
        val DEFAULT: TerminalTypeAheadSettings = TerminalTypeAheadSettings(
            true,
            TimeUnit.MILLISECONDS.toNanos(100),
            TextStyle(TerminalColor(8), null)
        )
    }
}
