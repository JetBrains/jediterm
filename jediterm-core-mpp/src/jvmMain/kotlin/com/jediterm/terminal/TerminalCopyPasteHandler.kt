package com.jediterm.terminal

interface TerminalCopyPasteHandler {
    fun copy()
    fun paste()
    fun selectAll()
}
