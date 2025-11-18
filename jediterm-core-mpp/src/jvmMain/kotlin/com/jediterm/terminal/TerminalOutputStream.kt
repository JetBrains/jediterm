package com.jediterm.terminal

/**
 * Sends a response from the terminal emulator.
 *
 * @author traff
 */
interface TerminalOutputStream {
    fun sendBytes(response: ByteArray, userInput: Boolean)

    fun sendString(string: String, userInput: Boolean)
}
