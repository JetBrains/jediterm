package com.jediterm.terminal.model

import com.jediterm.terminal.StyledTextConsumer
import com.jediterm.terminal.TextStyle
import com.jediterm.terminal.util.CharUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Consumer
import java.util.stream.Collectors
import kotlin.math.max
import kotlin.math.min

/**
 * @author traff
 */
class TerminalLine {
    private var myTextEntries = TextEntries()
    var isWrapped: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                incrementSnapshotVersion()
            }
        }
    private val myCustomHighlightings: MutableList<TerminalLineIntervalHighlighting?> =
        CopyOnWriteArrayList<TerminalLineIntervalHighlighting?>()
    private val myModificationCount = AtomicInteger(0)
    var myTypeAheadLine: TerminalLine? = null

    /**
     * Version counter for copy-on-write snapshot optimization.
     * Incremented on every mutation to enable version-based change detection.
     * Used by IncrementalSnapshotBuilder to skip copying unchanged lines.
     */
    @Volatile
    private var snapshotVersion: Long = 0L

    /**
     * Get the current snapshot version for copy-on-write optimization.
     * @return current version number (increments on every mutation)
     */
    fun getSnapshotVersion(): Long = snapshotVersion

    /**
     * Increment the snapshot version after any mutation.
     * Must be called at the END of every mutating method.
     */
    private fun incrementSnapshotVersion() {
        snapshotVersion++
    }

    constructor()

    constructor(entry: TextEntry) {
        myTextEntries.add(entry)
    }

    val text: String
        get() {
            val result = StringBuilder(myTextEntries.length())
            for (textEntry in myTextEntries) {
                // NUL can only be at the end
                if (textEntry.text.isNul) {
                    break
                }
                result.append(textEntry.text)
            }
            return result.toString()
        }

    fun copy(): TerminalLine {
        val result = TerminalLine()
        for (entry in myTextEntries) {
            // Deep copy: Create new TextEntry which clones CharBuffer in constructor
            // This ensures true immutability for snapshot-based rendering
            result.myTextEntries.add(TextEntry(entry.style, entry.text))
        }
        result.isWrapped = this.isWrapped
        return result
    }

    fun charAt(x: Int): Char {
        val typeAheadLine = myTypeAheadLine
        if (typeAheadLine != null) {
            return typeAheadLine.charAt(x)
        }
        val text = this.text
        return if (x < text.length) text.get(x) else CharUtils.EMPTY_CHAR
    }

    /**
     * @return total length of text entries.
     */
    fun length(): Int {
        return myTextEntries.length()
    }

    fun clear(filler: TextEntry) {
        myTextEntries.clear()
        myTextEntries.add(filler)
        incrementSnapshotVersion()
    }

    fun writeString(x: Int, str: CharBuffer, style: TextStyle) {
        writeCharacters(x, style, str)
    }

    fun insertString(x: Int, str: CharBuffer, style: TextStyle) {
        insertCharacters(x, style, str)
    }

    private fun writeCharacters(x: Int, style: TextStyle, characters: CharBuffer) {
        var len = myTextEntries.length()

        if (x >= len) {
            // fill the gap
            if (x - len > 0) {
                myTextEntries.add(TextEntry(TextStyle.Companion.EMPTY, CharBuffer(CharUtils.NUL_CHAR, x - len)))
            }
            myTextEntries.add(TextEntry(style, characters))
        } else {
            len = max(len, x + characters.length)
            myTextEntries = merge(x, characters, style, myTextEntries, len)
        }
        incrementSnapshotVersion()
    }

    private fun insertCharacters(x: Int, style: TextStyle, characters: CharBuffer) {
        val length = myTextEntries.length()
        if (x > length) {
            writeCharacters(x, style, characters)  // Already calls incrementSnapshotVersion()
            return
        }

        val pair = toBuf(myTextEntries, length + characters.length)

        for (i in length - 1 downTo x) {
            pair.first[i + characters.length] = pair.first[i]
            pair.second[i + characters.length] = pair.second[i]
        }
        for (i in 0..<characters.length) {
            pair.first[i + x] = characters.get(i)
            pair.second[i + x] = style
        }
        myTextEntries = Companion.collectFromBuffer(pair.first, pair.second)
        incrementSnapshotVersion()
    }

    @JvmOverloads
    fun deleteCharacters(x: Int, style: TextStyle = TextStyle.Companion.EMPTY) {
        deleteCharacters(x, myTextEntries.length() - x, style)
    }

    fun deleteCharacters(x: Int, count: Int, style: TextStyle) {
        var p = 0
        val newEntries = TextEntries()

        var remaining = count

        for (entry in myTextEntries) {
            if (remaining == 0) {
                newEntries.add(entry)
                continue
            }
            val len = entry.length
            if (p + len <= x) {
                p += len
                newEntries.add(entry)
                continue
            }
            val dx = x - p //>=0
            if (dx > 0) {
                //part of entry before x
                newEntries.add(TextEntry(entry.style, entry.text.subBuffer(0, dx)))
                p = x
            }
            if (dx + remaining < len) {
                //part that left after deleting count 
                newEntries.add(TextEntry(entry.style, entry.text.subBuffer(dx + remaining, len - (dx + remaining))))
                remaining = 0
            } else {
                remaining -= (len - dx)
                p = x
            }
        }
        if (count > 0 && style !== TextStyle.Companion.EMPTY) { // apply style to the end of the line
            newEntries.add(TextEntry(style, CharBuffer(CharUtils.NUL_CHAR, count)))
        }

        myTextEntries = newEntries
        incrementSnapshotVersion()
    }

    fun insertBlankCharacters(x: Int, count: Int, maxLen: Int, style: TextStyle) {
        var len = myTextEntries.length()
        len = min(len + count, maxLen)

        val buf = CharArray(len)
        val styles: Array<TextStyle?> = arrayOfNulls<TextStyle>(len)

        var p = 0
        for (entry in myTextEntries) {
            var i = 0
            while (i < entry.length && p < len) {
                if (p == x) {
                    var j = 0
                    while (j < count && p < len) {
                        buf[p] = CharUtils.EMPTY_CHAR
                        styles[p] = style
                        p++
                        j++
                    }
                }
                if (p < len) {
                    buf[p] = entry.text.get(i)
                    styles[p] = entry.style
                    p++
                }
                i++
            }
            if (p >= len) {
                break
            }
        }

        // if not inserted yet (ie. x > len)
        while (p < x && p < len) {
            buf[p] = CharUtils.EMPTY_CHAR
            styles[p] = TextStyle.Companion.EMPTY
            p++
            p++
        }
        while (p < x + count && p < len) {
            buf[p] = CharUtils.EMPTY_CHAR
            styles[p] = style
            p++
            p++
        }

        myTextEntries = collectFromBuffer(buf, styles)
        incrementSnapshotVersion()
    }

    fun clearArea(leftX: Int, rightX: Int, style: TextStyle) {
        var rightX = rightX
        if (rightX == -1) {
            rightX = max(myTextEntries.length(), leftX)
        }
        writeCharacters(
            leftX, style, CharBuffer(
                if (rightX >= myTextEntries.length()) CharUtils.NUL_CHAR else CharUtils.EMPTY_CHAR,
                rightX - leftX
            )
        )
    }

    /**
     * Selectively clears an area, preserving protected characters.
     * Used by DECSEL (Selective Erase in Line).
     */
    fun selectiveClearArea(leftX: Int, rightX: Int, style: TextStyle) {
        var rightX = rightX
        if (rightX == -1) {
            rightX = max(myTextEntries.length(), leftX)
        }

        // Iterate through existing entries and preserve protected characters
        var currentX = 0
        val newEntries = mutableListOf<TextEntry>()

        for (entry in myTextEntries) {
            val entryStart = currentX
            val entryEnd = currentX + entry.length

            if (entryEnd <= leftX || entryStart >= rightX) {
                // Outside erase region - keep as-is
                newEntries.add(entry)
            } else {
                // Overlaps with erase region
                val isProtected = entry.style.hasOption(TextStyle.Option.PROTECTED)

                if (isProtected) {
                    // Protected - keep as-is
                    newEntries.add(entry)
                } else {
                    // Unprotected - split and erase
                    // Before erase region
                    if (entryStart < leftX) {
                        val beforeLen = leftX - entryStart
                        newEntries.add(TextEntry(
                            entry.style,
                            entry.text.subBuffer(0, beforeLen)
                        ))
                    }

                    // Erased portion
                    val eraseStart = max(entryStart, leftX)
                    val eraseEnd = min(entryEnd, rightX)
                    val eraseLen = eraseEnd - eraseStart
                    newEntries.add(TextEntry(
                        style,
                        CharBuffer(CharUtils.EMPTY_CHAR, eraseLen)
                    ))

                    // After erase region
                    if (entryEnd > rightX) {
                        val afterStart = rightX - entryStart
                        val afterLen = entryEnd - rightX
                        newEntries.add(TextEntry(
                            entry.style,
                            entry.text.subBuffer(afterStart, afterLen)
                        ))
                    }
                }
            }

            currentX = entryEnd
        }

        // Rebuild text entries
        myTextEntries.clear()
        for (entry in newEntries) {
            myTextEntries.add(entry)
        }
        incrementSnapshotVersion()
    }

    fun getStyleAt(x: Int): TextStyle? {
        var i = 0

        for (te in myTextEntries) {
            if (x >= i && x < i + te.length) {
                return te.style
            }
            i += te.length
        }

        return null
    }

    fun process(y: Int, consumer: StyledTextConsumer, startRow: Int) {
        var x = 0
        var nulIndex = -1
        val highlighting = myCustomHighlightings.stream().findFirst().orElse(null)
        val typeAheadLine = myTypeAheadLine
        val textEntries = if (typeAheadLine != null) typeAheadLine.myTextEntries else myTextEntries
        for (te in textEntries) {
            if (te.text.isNul) {
                if (nulIndex < 0) {
                    nulIndex = x
                }
                consumer.consumeNul(x, y, nulIndex, te.style, te.text, startRow)
            } else {
                if (highlighting != null && te.length > 0 && highlighting.intersectsWith(x, x + te.length)) {
                    processIntersection(x, y, te, consumer, startRow, highlighting)
                } else {
                    consumer.consume(x, y, te.style, te.text, startRow)
                }
            }
            x += te.length
        }
        consumer.consumeQueue(x, y, if (nulIndex < 0) x else nulIndex, startRow)
    }

    private fun processIntersection(
        startTextOffset: Int, y: Int, te: TextEntry, consumer: StyledTextConsumer,
        startRow: Int, highlighting: TerminalLineIntervalHighlighting
    ) {
        val text = te.text
        val endTextOffset = startTextOffset + text.length
        val offsets =
            intArrayOf(startTextOffset, endTextOffset, highlighting.startOffset, highlighting.endOffset)
        Arrays.sort(offsets)
        val startTextOffsetInd = Arrays.binarySearch(offsets, startTextOffset)
        val endTextOffsetInd = Arrays.binarySearch(offsets, endTextOffset)
        if (startTextOffsetInd < 0 || endTextOffsetInd < 0) {
            LOG.error(
                ("Cannot find " + intArrayOf(
                    startTextOffset,
                    endTextOffset
                ).contentToString() + " in " + offsets.contentToString() + ": " + intArrayOf(
                    startTextOffsetInd,
                    endTextOffsetInd
                ).contentToString())
            )
            consumer.consume(startTextOffset, y, te.style, text, startRow)
            return
        }
        for (i in startTextOffsetInd..<endTextOffsetInd) {
            val length = offsets[i + 1] - offsets[i]
            if (length == 0) continue
            val subText: CharBuffer = SubCharBuffer(text, offsets[i] - startTextOffset, length)
            if (highlighting.intersectsWith(offsets[i], offsets[i + 1])) {
                consumer.consume(offsets[i], y, highlighting.mergeWith(te.style), subText, startRow)
            } else {
                consumer.consume(offsets[i], y, te.style, subText, startRow)
            }
        }
    }

    val isNul: Boolean
        get() {
            for (e in myTextEntries) {
                if (!e.isNul) {
                    return false
                }
            }

            return true
        }

    val isEmpty: Boolean
        get() {
            for (e in myTextEntries) {
                if (!e.isNul && e.length > 0) {
                    return false
                }
            }
            return true
        }

    val isNulOrEmpty: Boolean
        get() = this.isNul || this.isEmpty

    fun forEachEntry(action: Consumer<TextEntry?>) {
        myTextEntries.forEach(action)
    }

    val entries: MutableList<TextEntry?>
        get() = Collections.unmodifiableList<TextEntry?>(
            myTextEntries.entries()
        )

    fun appendEntry(entry: TextEntry) {
        myTextEntries.add(entry)
        incrementSnapshotVersion()
    }

    val modificationCount: Int
        get() = myModificationCount.get()

    fun incrementAndGetModificationCount() {
        myModificationCount.incrementAndGet()
    }

    @Suppress("unused") // used by IntelliJ
    fun addCustomHighlighting(startOffset: Int, length: Int, textStyle: TextStyle): TerminalLineIntervalHighlighting {
        val highlighting: TerminalLineIntervalHighlighting =
            object : TerminalLineIntervalHighlighting(this, startOffset, length, textStyle) {
                override fun doDispose() {
                    myCustomHighlightings.remove(this)
                }
            }
        myCustomHighlightings.add(highlighting)
        return highlighting
    }

    override fun toString(): String {
        return myTextEntries.length().toString() + " chars, " +
                (if (this.isWrapped) "wrapped, " else "") +
                myTextEntries.entries().size + " entries: " +
                myTextEntries.entries().stream()
                    .map<String> { entry: TextEntry -> entry.text.toString() }
                    .collect(Collectors.joining("|"))
    }

    class TextEntry(val style: TextStyle, text: CharBuffer) {
        val text: CharBuffer

        init {
            this.text = text.clone()
        }

        val length: Int
            get() = text.length

        val isNul: Boolean
            get() = text.isNul

        override fun toString(): String {
            return text.length.toString() + " chars, style: " + this.style + ", text: " + this.text
        }
    }

    private class TextEntries : Iterable<TextEntry> {
        private val myTextEntries: MutableList<TextEntry> = ArrayList<TextEntry>()

        private var myLength = 0

        fun add(entry: TextEntry) {
            // NUL can only be at the end of the line
            if (!entry.text.isNul) {
                for (t in myTextEntries) {
                    if (t.text.isNul) {
                        t.text.unNullify()
                    }
                }
            }
            myTextEntries.add(entry)
            myLength += entry.length
        }

        fun entries(): MutableList<TextEntry> {
            return myTextEntries
        }

        override fun iterator(): MutableIterator<TextEntry> {
            return myTextEntries.iterator()
        }

        fun length(): Int {
            return myLength
        }

        fun clear() {
            myTextEntries.clear()
            myLength = 0
        }
    }

    companion object {
        private val LOG: Logger = LoggerFactory.getLogger(TerminalLine::class.java)

        fun createEmpty(): TerminalLine {
            return TerminalLine()
        }

        private fun merge(
            x: Int,
            str: CharBuffer,
            style: TextStyle,
            entries: TextEntries,
            lineLength: Int
        ): TextEntries {
            val pair = toBuf(entries, lineLength)

            for (i in 0..<str.length) {
                pair.first[i + x] = str.get(i)
                pair.second[i + x] = style
            }

            return Companion.collectFromBuffer(pair.first, pair.second)
        }

        private fun toBuf(entries: TextEntries, lineLength: Int): Pair<CharArray, Array<TextStyle?>> {
            val pair = Pair(CharArray(lineLength), arrayOfNulls<TextStyle>(lineLength))

            var p = 0
            for (entry in entries) {
                for (i in 0..<entry.length) {
                    pair.first[p + i] = entry.text.get(i)
                    pair.second[p + i] = entry.style
                }
                p += entry.length
            }
            return pair
        }

        private fun collectFromBuffer(buf: CharArray, styles: Array<TextStyle?>): TextEntries {
            val result = TextEntries()

            var curStyle = styles[0] ?: TextStyle.EMPTY
            var start = 0

            for (i in 1..<buf.size) {
                if (styles[i] !== curStyle) {
                    result.add(TextEntry(curStyle, CharBuffer(buf, start, i - start)))
                    curStyle = styles[i] ?: TextStyle.EMPTY
                    start = i
                }
            }

            result.add(TextEntry(curStyle, CharBuffer(buf, start, buf.size - start)))

            return result
        }
    }
}
