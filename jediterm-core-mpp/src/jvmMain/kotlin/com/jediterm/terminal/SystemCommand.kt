package com.jediterm.terminal.emulator

interface SystemCommand {
    fun process(code: Int, value: String)
}
