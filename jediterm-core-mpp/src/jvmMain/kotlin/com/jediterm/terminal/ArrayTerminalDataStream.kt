package com.jediterm.terminal

import com.jediterm.terminal.util.CharUtils
import java.io.IOException

/**
 * Takes data from underlying char array.
 *
 * @author traff
 */
open class ArrayTerminalDataStream @JvmOverloads constructor(
    protected var myBuf: CharArray,
    protected var myOffset: Int = 0,
    protected var myLength: Int = myBuf.size - myOffset
) : TerminalDataStream {
    @get:Throws(IOException::class)
    override val char: Char
        get() {
            if (myLength == 0) {
                throw TerminalDataStream.EOF()
            }

            myLength--

            return myBuf[myOffset++]
        }

    @Throws(TerminalDataStream.EOF::class)
    override fun pushChar(c: Char) {
        if (myOffset == 0) {
            // Pushed back too many... shift it up to the end.

            val newBuf: CharArray
            if (myBuf.size - myLength == 0) {
                newBuf = CharArray(myBuf.size + 1)
            } else {
                newBuf = myBuf
            }
            myOffset = newBuf.size - myLength
            System.arraycopy(myBuf, 0, newBuf, myOffset, myLength)
            myBuf = newBuf
        }

        myLength++
        myBuf[--myOffset] = c
    }

    @Throws(IOException::class)
    override fun readNonControlCharacters(maxChars: Int): String? {
        val nonControlCharacters = CharUtils.getNonControlCharacters(maxChars, myBuf, myOffset, myLength)

        myOffset += nonControlCharacters.length
        myLength -= nonControlCharacters.length

        return nonControlCharacters
    }

    @Throws(TerminalDataStream.EOF::class)
    override fun pushBackBuffer(bytes: CharArray?, length: Int) {
        bytes?.let {
            for (i in length - 1 downTo 0) {
                pushChar(it[i])
            }
        }
    }

    override val isEmpty: Boolean
        get() = myLength == 0
}
