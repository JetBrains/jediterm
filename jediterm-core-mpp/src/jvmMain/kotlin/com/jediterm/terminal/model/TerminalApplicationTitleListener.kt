package com.jediterm.terminal.model

import org.jetbrains.annotations.Nls

interface TerminalApplicationTitleListener {
    fun onApplicationTitleChanged(newApplicationTitle: @Nls String)
}
