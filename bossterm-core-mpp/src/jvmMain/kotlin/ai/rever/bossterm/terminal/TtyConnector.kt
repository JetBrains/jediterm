package ai.rever.bossterm.terminal

import ai.rever.bossterm.core.util.TermSize
import java.io.IOException

interface TtyConnector {
    @Throws(IOException::class)
    fun read(buf: CharArray?, offset: Int, length: Int): Int

    @Throws(IOException::class)
    fun write(bytes: ByteArray?)

    @Throws(IOException::class)
    fun write(string: String?)

    val isConnected: Boolean

    /**
     * Resize the terminal to the given dimensions.
     * Implementations should override this method to handle resize events.
     */
    fun resize(termSize: TermSize) {
        // Default no-op implementation for connectors that don't support resize
    }

    @Throws(InterruptedException::class)
    fun waitFor(): Int

    @Throws(IOException::class)
    fun ready(): Boolean

    val name: String?

    fun close()

    /**
     * Initialize the connector with optional user prompting capability.
     * @param q Questioner for interactive authentication (optional)
     * @return true if initialization succeeded
     */
    fun init(q: Questioner?): Boolean {
        return true
    }
}
