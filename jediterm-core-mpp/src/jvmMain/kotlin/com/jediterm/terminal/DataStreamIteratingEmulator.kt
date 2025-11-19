package com.jediterm.terminal

import com.jediterm.terminal.emulator.Emulator
import java.io.IOException

/**
 * @author traff
 */
abstract class DataStreamIteratingEmulator(
    protected val myDataStream: TerminalDataStream,
    protected val myTerminal: Terminal?
) : Emulator {
    private var myEof = false

    override fun hasNext(): Boolean {
        return !myEof
    }

    override fun resetEof() {
        myEof = false
    }

    @Throws(IOException::class)
    override fun next() {
        try {
            val b = myDataStream.char
            processChar(b, myTerminal)
        } catch (e: TerminalDataStream.EOF) {
            myEof = true
        }
    }

    @Throws(IOException::class)
    protected abstract fun processChar(ch: Char, terminal: Terminal?)
}
