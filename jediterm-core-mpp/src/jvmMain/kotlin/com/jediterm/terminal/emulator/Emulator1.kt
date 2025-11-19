package com.jediterm.terminal.emulator

import java.io.IOException

/**
 * @author traff
 */
interface Emulator {
    fun hasNext(): Boolean

    @Throws(IOException::class)
    fun next()

    fun resetEof()
}
