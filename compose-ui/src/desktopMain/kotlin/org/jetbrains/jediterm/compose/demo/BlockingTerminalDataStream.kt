package org.jetbrains.jediterm.compose.demo

import com.jediterm.terminal.TerminalDataStream
import java.io.IOException
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * A blocking TerminalDataStream implementation that allows appending data chunks
 * and blocks on getChar() instead of throwing EOF at chunk boundaries.
 *
 * This solves the issue where CSI sequences spanning multiple output chunks
 * were being truncated and displayed as visible text.
 */
class BlockingTerminalDataStream : TerminalDataStream {
    private val buffer = StringBuilder()
    private var position = 0
    private val dataQueue: BlockingQueue<String> = LinkedBlockingQueue()
    private var closed = false
    private val pushBackStack = mutableListOf<Char>()

    /**
     * Append a chunk of data to the stream
     */
    fun append(data: String) {
        if (closed) return
        dataQueue.offer(data)
    }

    /**
     * Signal that no more data will be appended
     */
    fun close() {
        closed = true
    }

    override val char: Char
        @Throws(IOException::class)
        get() {
            // First check pushback stack
            if (pushBackStack.isNotEmpty()) {
                return pushBackStack.removeAt(pushBackStack.size - 1)
            }

            // If we have data in the buffer, return it
            while (position >= buffer.length) {
                // Need more data - block until available
                val chunk = if (closed) {
                    dataQueue.poll() // Non-blocking if closed
                } else {
                    dataQueue.poll(100, TimeUnit.MILLISECONDS) // Wait for data
                }

                if (chunk != null) {
                    buffer.append(chunk)
                } else if (closed && dataQueue.isEmpty()) {
                    // Stream is closed and no more data
                    throw TerminalDataStream.EOF()
                }
                // If chunk is null and not closed, loop again to wait
            }

            return buffer[position++]
        }

    override fun pushChar(c: Char) {
        pushBackStack.add(c)
    }

    override fun readNonControlCharacters(maxChars: Int): String {
        val result = StringBuilder()
        var count = 0

        while (count < maxChars) {
            // Check pushback first
            if (pushBackStack.isNotEmpty()) {
                val c = pushBackStack.removeAt(pushBackStack.size - 1)
                if (c < ' ' || c == 0x7F.toChar()) {
                    pushChar(c)
                    break
                }
                result.append(c)
                count++
                continue
            }

            // Check if we need more data
            if (position >= buffer.length) {
                val chunk = dataQueue.poll(10, TimeUnit.MILLISECONDS)
                if (chunk != null) {
                    buffer.append(chunk)
                } else {
                    break // No data available
                }
            }

            if (position < buffer.length) {
                val c = buffer[position]
                if (c < ' ' || c == 0x7F.toChar()) {
                    break // Stop at control character
                }
                result.append(c)
                position++
                count++
            } else {
                break
            }
        }

        return result.toString()
    }

    override fun pushBackBuffer(bytes: CharArray?, length: Int) {
        if (bytes == null) return
        // Push back in reverse order so they come out in correct order
        for (i in length - 1 downTo 0) {
            pushBackStack.add(bytes[i])
        }
    }

    override val isEmpty: Boolean
        get() = pushBackStack.isEmpty() &&
               position >= buffer.length &&
               (closed || dataQueue.isEmpty())
}
