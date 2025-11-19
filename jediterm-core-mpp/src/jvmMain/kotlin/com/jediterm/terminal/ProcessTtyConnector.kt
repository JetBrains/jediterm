package com.jediterm.terminal

import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.nio.charset.Charset
import java.util.*

/**
 * @author traff
 */
abstract class ProcessTtyConnector @JvmOverloads constructor(
    val process: Process,
    protected val myCharset: Charset,
    private val myCommandLine: MutableList<String?>? = null
) : TtyConnector {
    protected val myInputStream: InputStream
    protected val myOutputStream: OutputStream
    protected val myReader: InputStreamReader

    init {
        myOutputStream = process.getOutputStream()
        myInputStream = process.getInputStream()
        myReader = InputStreamReader(myInputStream, myCharset)
    }

    abstract override val name: String?

    val commandLine: MutableList<String?>?
        get() = if (myCommandLine != null) Collections.unmodifiableList<String?>(myCommandLine) else null

    @Throws(IOException::class)
    override fun read(buf: CharArray?, offset: Int, length: Int): Int {
        return myReader.read(buf, offset, length)
    }

    @Throws(IOException::class)
    override fun write(bytes: ByteArray?) {
        bytes?.let {
            myOutputStream.write(it)
            myOutputStream.flush()
        }
    }

    override val isConnected: Boolean
        get() = process.isAlive()

    @Throws(IOException::class)
    override fun write(string: String?) {
        string?.let { write(it.toByteArray(myCharset)) }
    }

    override fun close() {
        process.destroy()
        try {
            myOutputStream.close()
        } catch (ignored: IOException) {
        }
        try {
            myInputStream.close()
        } catch (ignored: IOException) {
        }
    }

    @Throws(InterruptedException::class)
    override fun waitFor(): Int {
        return process.waitFor()
    }

    @Throws(IOException::class)
    override fun ready(): Boolean {
        return myReader.ready()
    }
}
