package ai.rever.bossterm.terminal.model

import org.jetbrains.annotations.Nls

interface TerminalApplicationTitleListener {
    fun onApplicationTitleChanged(newApplicationTitle: @Nls String)

    fun onApplicationIconTitleChanged(newIconTitle: @Nls String) {
        // Default empty implementation for backward compatibility
    }
}
