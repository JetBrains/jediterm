package com.jediterm.terminal

interface TerminalCustomCommandListener {
    fun process(args: MutableList<String?>)
}
