package org.jetbrains.jediterm.compose.demo

import com.jediterm.terminal.TerminalDataStream
import com.jediterm.terminal.util.GraphemeUtils
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
        val lastCompleteIndex = findLastCompleteGraphemeBoundary(fullData)

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

    /**
     * Finds the index of the last complete grapheme boundary in the text.
     *
     * Returns the position after the last complete grapheme. If the text ends
     * with an incomplete grapheme (e.g., high surrogate, emoji without variation
     * selector, ZWJ sequence), returns the start of that incomplete grapheme.
     *
     * Examples:
     * - "abc" -> 3 (all complete)
     * - "ab\uD835" (high surrogate at end) -> 2 (incomplete surrogate)
     * - "ab👨\u200D" (ZWJ at end) -> 2 (incomplete ZWJ sequence)
     *
     * @param text The text to analyze
     * @return Index after the last complete grapheme
     */
    private fun findLastCompleteGraphemeBoundary(text: String): Int {
        if (text.isEmpty()) return 0

        // Fast path: if last char is ASCII and not a grapheme extender, it's complete
        val lastChar = text.last()
        if (lastChar.code < 128 && !needsGraphemeAnalysis(lastChar)) {
            return text.length
        }

        // Check the last few characters for incomplete graphemes
        // Max grapheme is ~20 chars for ZWJ sequences, check up to 30 to be safe
        val checkLength = minOf(text.length, 30)
        val startIndex = text.length - checkLength
        val tail = text.substring(startIndex)

        // Get all grapheme boundaries in the tail
        val boundaries = GraphemeUtils.findGraphemeBoundaries(tail)

        if (boundaries.isEmpty()) {
            // No boundaries found - entire tail is one incomplete grapheme
            return startIndex
        }

        // The boundaries list includes 0 as the first boundary
        // Find the last boundary that's not at the end
        val lastBoundary = boundaries.last()

        if (lastBoundary == tail.length) {
            // Last boundary is at the end - all complete
            return text.length
        } else {
            // Last boundary is before the end - incomplete grapheme from lastBoundary to end
            return startIndex + lastBoundary
        }
    }

    /**
     * Checks if a character needs grapheme analysis.
     *
     * Fast heuristic to avoid expensive grapheme segmentation for simple ASCII.
     * Returns true for:
     * - Surrogate pairs (high/low surrogates)
     * - Zero-Width Joiner (ZWJ)
     * - Variation selectors (U+FE0E, U+FE0F)
     * - Combining diacritics
     * - Other grapheme extenders
     *
     * @param c The character to check
     * @return True if this character might be part of a complex grapheme
     */
    private fun needsGraphemeAnalysis(c: Char): Boolean {
        return c.isHighSurrogate() ||
               c.isLowSurrogate() ||
               c.code == 0x200D || // ZWJ
               c.code == 0xFE0E || c.code == 0xFE0F || // Variation selectors
               c.code in 0x0300..0x036F || // Combining diacritics
               GraphemeUtils.isGraphemeExtender(c)
    }
}
