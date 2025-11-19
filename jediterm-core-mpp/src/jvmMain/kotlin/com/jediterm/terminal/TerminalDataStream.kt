package com.jediterm.terminal

import java.io.IOException

/**
 * Represents data communication interface for terminal.
 * It allows to [.getChar] by one and [.pushChar] back as well as requesting a chunk of plain ASCII
 * characters ([.readNonControlCharacters] - for faster processing from buffer in the size <=**maxChars**).
 *
 *
 * @author traff
 */
interface TerminalDataStream {
    @get:Throws(IOException::class)
    val char: Char

    @Throws(IOException::class)
    fun pushChar(c: Char)

    @Throws(IOException::class)
    fun readNonControlCharacters(maxChars: Int): String?

    @Throws(IOException::class)
    fun pushBackBuffer(bytes: CharArray?, length: Int)

    val isEmpty: Boolean

    class EOF : IOException("EOF: There is no more data or connection is lost")
}
