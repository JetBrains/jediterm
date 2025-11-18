package com.jediterm.terminal

import com.jediterm.terminal.util.CharUtils
import java.io.IOException

/**
 * Takes data from and sends it back to TTY input and output streams via [TtyConnector]
 */
class TtyBasedArrayDataStream : ArrayTerminalDataStream {
    private val myTtyConnector: TtyConnector
    private val myOnBeforeBlockingWait: Runnable?

    constructor(ttyConnector: TtyConnector, onBeforeBlockingWait: Runnable?) : super(CharArray(1024), 0, 0) {
        myTtyConnector = ttyConnector
        myOnBeforeBlockingWait = onBeforeBlockingWait
    }

    constructor(ttyConnector: TtyConnector) : super(CharArray(1024), 0, 0) {
        myTtyConnector = ttyConnector
        myOnBeforeBlockingWait = null
    }

    @Throws(IOException::class)
    private fun fillBuf() {
        myOffset = 0

        if (!myTtyConnector.ready() && myOnBeforeBlockingWait != null) {
            myOnBeforeBlockingWait.run()
        }
        myLength = myTtyConnector.read(myBuf, myOffset, myBuf.size)

        if (myLength <= 0) {
            myLength = 0
            throw TerminalDataStream.EOF()
        }
    }

    override val char: Char
        @Throws(IOException::class)
        get() {
            if (myLength == 0) {
                fillBuf()
            }
            return super.char
        }

    @Throws(IOException::class)
    override fun readNonControlCharacters(maxChars: Int): String? {
        if (myLength == 0) {
            fillBuf()
        }

        return super.readNonControlCharacters(maxChars)
    }

    override fun toString(): String {
        return CharUtils.toHumanReadableText(String(myBuf, myOffset, myLength))
    }
}
