package com.jediterm.terminal

import com.jediterm.core.util.TermSize
import java.awt.Dimension
import java.io.IOException

interface TtyConnector {
    @Throws(IOException::class)
    fun read(buf: CharArray?, offset: Int, length: Int): Int

    @Throws(IOException::class)
    fun write(bytes: ByteArray?)

    @Throws(IOException::class)
    fun write(string: String?)

    val isConnected: Boolean

    fun resize(termSize: TermSize) {
        // support old implementations not overriding this method
        resize(Dimension(termSize.columns, termSize.rows))
    }

    @Throws(InterruptedException::class)
    fun waitFor(): Int

    @Throws(IOException::class)
    fun ready(): Boolean

    val name: String?

    fun close()

    @Deprecated("use {@link #resize(TermSize)} instead")
    fun resize(termWinSize: Dimension) {
        // support old implementations overriding neither `resize(Dimension)` nor this method
        resize(termWinSize, Dimension(0, 0))
    }

    @Suppress("unused")
    @Deprecated("use {@link #resize(TermSize)} instead")
    fun resize(termWinSize: Dimension?, pixelSize: Dimension?) {
        throw IllegalStateException(
            "This method shouldn't be called. " +
                    javaClass + " should override TtyConnector.resize(com.jediterm.core.util.TermSize)"
        )
    }

    @Deprecated("Collect extra information when creating {@link TtyConnector}")
    fun init(q: Questioner?): Boolean {
        return true
    }
}
