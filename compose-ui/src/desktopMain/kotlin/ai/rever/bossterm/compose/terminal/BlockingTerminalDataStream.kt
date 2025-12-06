package ai.rever.bossterm.compose.terminal

import ai.rever.bossterm.terminal.TerminalDataStream
import ai.rever.bossterm.terminal.util.GraphemeUtils
import ai.rever.bossterm.terminal.util.GraphemeBoundaryUtils
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
 *
 * Also handles incomplete grapheme clusters at chunk boundaries (e.g., surrogate
 * pairs, emoji ZWJ sequences) by buffering incomplete graphemes until the next chunk.
 */
class BlockingTerminalDataStream : TerminalDataStream {
    private val buffer = StringBuilder()
    private var position = 0
    private val dataQueue: BlockingQueue<String> = LinkedBlockingQueue()
    private var closed = false
    private val pushBackStack = mutableListOf<Char>()

    /**
     * Buffer for incomplete grapheme clusters at chunk boundaries.
     * When a chunk ends mid-grapheme (e.g., high surrogate without low surrogate,
     * emoji without variation selector), the incomplete part is stored here
     * and prepended to the next chunk.
     */
    private var incompleteGraphemeBuffer = ""

    /**
     * Optional debug callback invoked when data is appended.
     * Used by debug tools to capture I/O for visualization.
     */
    var debugCallback: ((String) -> Unit)? = null

    /**
     * Optional callback invoked when terminal state changes (data arrives from PTY).
     * Used by type-ahead system to validate/clear predictions.
     */
    var onTerminalStateChanged: (() -> Unit)? = null

    /**
     * Append a chunk of data to the stream.
     *
     * Handles incomplete grapheme clusters at chunk boundaries by:
     * 1. Prepending any buffered incomplete grapheme from the previous chunk
     * 2. Checking if this chunk ends with an incomplete grapheme
     * 3. Buffering the incomplete part for the next chunk
     * 4. Only queuing the complete grapheme portion
     *
     * This ensures surrogate pairs, emoji sequences, and combining characters
     * are never split across chunk boundaries.
     */
    fun append(data: String) {
        if (closed) return

        // Prepend any buffered incomplete grapheme from previous chunk
        val fullData = incompleteGraphemeBuffer + data
        incompleteGraphemeBuffer = ""

        // Check if the chunk ends with an incomplete grapheme
        val lastCompleteIndex = GraphemeBoundaryUtils.findLastCompleteGraphemeBoundary(fullData)

        if (lastCompleteIndex < fullData.length) {
            // Chunk ends mid-grapheme - buffer the incomplete part
            incompleteGraphemeBuffer = fullData.substring(lastCompleteIndex)
            val completeData = fullData.substring(0, lastCompleteIndex)

            if (completeData.isNotEmpty()) {
                dataQueue.offer(completeData)
                // Invoke debug callback only for complete data
                debugCallback?.invoke(completeData)
            }
        } else {
            // All graphemes are complete
            dataQueue.offer(fullData)
            debugCallback?.invoke(fullData)
        }
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
                // Notify type-ahead system before blocking wait
                // This allows the type-ahead manager to validate predictions
                // against the current terminal state before we wait for more data
                onTerminalStateChanged?.invoke()

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
