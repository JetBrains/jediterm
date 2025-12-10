package ai.rever.bossterm.terminal.model

import ai.rever.bossterm.core.compatibility.Point
import ai.rever.bossterm.terminal.model.image.ImageCell
import ai.rever.bossterm.terminal.util.GraphemeBoundaryUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*
import java.util.function.Consumer
import kotlin.math.max
import kotlin.math.min

internal class ChangeWidthOperation(
    private val myTextBuffer: TerminalTextBuffer,
    private val myNewWidth: Int, private val myNewHeight: Int
) {
    private val myTrackingPoints: MutableMap<TrackingPoint, Point?> = HashMap<TrackingPoint, Point?>()
    private val myAllLines: MutableList<TerminalLine?> = ArrayList<TerminalLine?>()
    private var myCurrentLine: TerminalLine? = null
    private var myCurrentLineLength = 0

    // Track image anchor rows for remapping after reflow (legacy - for TerminalImageStorage)
    private val myAnchorRows: MutableSet<Int> = HashSet()
    private val myAnchorMapping: MutableMap<Int, Int> = HashMap()

    fun addPointToTrack(point: Point, isForceVisible: Boolean) {
        if (isForceVisible && (point.y < 0 || point.y >= myTextBuffer.height)) {
            LOG.warn("Registered visible point {} is outside screen: [0, {}]", point, myTextBuffer.height - 1)
            point.y = min(max(point.y, 0), myTextBuffer.height - 1)
        }
        myTrackingPoints.put(TrackingPoint(point, isForceVisible), null)
    }

    /**
     * Add an image anchor row to track through the reflow.
     * The anchor row is screen-relative (0-indexed).
     */
    fun addAnchorRowToTrack(row: Int) {
        myAnchorRows.add(row)
        // Track as a point at column 0
        addPointToTrack(Point(0, row), false)
    }

    /**
     * Get the mapping of old anchor rows to new anchor rows after reflow.
     * Must be called after run().
     */
    fun getAnchorMapping(): Map<Int, Int> = myAnchorMapping.toMap()

    fun getTrackedPoint(original: Point): Point {
        var result = myTrackingPoints.get(TrackingPoint(original, false))
        if (result != null) {
            return result
        }
        result = myTrackingPoints.get(TrackingPoint(original, true))
        if (result != null) {
            return result
        }
        LOG.warn("Not tracked point: {}", original)
        return original
    }

    fun run() {
        val historyLinesStorage = myTextBuffer.getHistoryLinesStorageOrBackup()

        for (i in 0..<historyLinesStorage.size) {
            val line = historyLinesStorage.get(i)
            addLine(line)
        }
        // CRITICAL FIX: Reset current line after processing history.
        // History and screen are separate storage - the last history line should NOT
        // merge with the first screen line, even if the last history line was wrapped.
        // Without this reset, wrapped history lines would incorrectly absorb screen content.
        myCurrentLine = null
        myCurrentLineLength = 0

        var screenStartInd = myAllLines.size - 1
        if (myCurrentLine == null || myCurrentLineLength == myNewWidth) {
            screenStartInd++
        }
        if (screenStartInd < 0) {
            throw IndexOutOfBoundsException("screenStartInd < 0: " + screenStartInd)
        }
        val screenLinesStorage = myTextBuffer.getScreenLinesStorageOrBackup()

        if (screenLinesStorage.size > myTextBuffer.height) {
            LOG.warn("Terminal height < screen buffer line count: {} < {}", myTextBuffer.height, screenLinesStorage.size)
        }
        val oldScreenLineCount = min(screenLinesStorage.size, myTextBuffer.height)

        for (i in 0..<oldScreenLineCount) {
            val points = findPointsAtY(i)
            for (point in points) {
                val newX = (myCurrentLineLength + point.x) % myNewWidth
                var newY = myAllLines.size + (myCurrentLineLength + point.x) / myNewWidth
                if (myCurrentLine != null) {
                    newY--
                }
                myTrackingPoints.put(point, Point(newX, newY))
            }
            addLine(screenLinesStorage.get(i))
        }

        for (i in oldScreenLineCount..<myTextBuffer.height) {
            val points = findPointsAtY(i)
            for (point in points) {
                val newX = point.x % myNewWidth
                val newY = (i - oldScreenLineCount) + myAllLines.size + point.x / myNewWidth
                myTrackingPoints.put(point, Point(newX, newY))
            }
        }

        val emptyBottomLineCount = this.emptyBottomLineCount
        var bottomMostPointY = 0
        for (entry in myTrackingPoints.entries) {
            if (entry.key.forceVisible) {
                val resultPoint = Objects.requireNonNull<Point>(entry.value)
                bottomMostPointY = max(bottomMostPointY, resultPoint.y)
            }
        }

        // CRITICAL: Keep cursor in view by positioning screen window around cursor
        // Screen shows [screenStartInd, screenStartInd + myNewHeight), history gets [0, screenStartInd)

        // Find cursor's new Y position after reflow
        var cursorNewY = 0
        for (entry in myTrackingPoints.entries) {
            if (entry.key.forceVisible && entry.value != null) {
                cursorNewY = max(cursorNewY, entry.value!!.y)
            }
        }

        // Position screen so cursor is visible
        // Screen should start at max(0, cursorNewY - myNewHeight + 1) to keep cursor at bottom
        // But also can't start beyond myAllLines.size - myNewHeight (would lose lines at end)
        val minScreenStart = max(0, cursorNewY - myNewHeight + 1)
        val maxScreenStart = max(0, myAllLines.size - myNewHeight)
        screenStartInd = min(minScreenStart, maxScreenStart)

        val screenEndInd = min(screenStartInd + myNewHeight, myAllLines.size)

        // History gets all lines before screen [0, screenStartInd)
        val historySublist = myAllLines.subList(0, screenStartInd).filterNotNull()
        historyLinesStorage.clear()
        historyLinesStorage.addAllToBottom(historySublist)

        // Screen gets lines [screenStartInd, screenEndInd)
        val screenSublist = myAllLines.subList(screenStartInd, screenEndInd).filterNotNull()
        screenLinesStorage.clear()
        screenLinesStorage.addAllToBottom(screenSublist)

        for (entry in myTrackingPoints.entries) {
            var p = entry.value
            if (p != null) {
                p.y -= screenStartInd
            } else {
                val key = entry.key
                p = Point(key.x, key.y)
                entry.setValue(p)
            }
            p.x = min(myNewWidth, max(0, p.x))
            p.y = min(myNewHeight, max(0, p.y))
        }

        // Build anchor mapping for image repositioning (legacy - for TerminalImageStorage)
        for (oldRow in myAnchorRows) {
            val trackedPoint = myTrackingPoints[TrackingPoint(0, oldRow, false)]
            if (trackedPoint != null) {
                myAnchorMapping[oldRow] = trackedPoint.y
            }
        }
    }

    private val emptyBottomLineCount: Int
        get() {
            var ind = myAllLines.size - 1
            while (ind >= 0) {
                val line = myAllLines.get(ind)
                if (line == null || !line.isNulOrEmpty) break
                ind--
            }
            return myAllLines.size - 1 - ind
        }

    private fun findPointsAtY(y: Int): MutableList<TrackingPoint> {
        val result: MutableList<TrackingPoint> = ArrayList<TrackingPoint>()
        for (key in myTrackingPoints.keys) {
            if (key.y == y) {
                result.add(key)
            }
        }
        return result
    }

    private fun addLine(line: TerminalLine) {
        // Handle empty lines (no text, no images)
        if (line.isNul && !line.hasImageCells()) {
            if (myCurrentLine != null) {
                myCurrentLine = null
                myCurrentLineLength = 0
            }
            myAllLines.add(TerminalLine.Companion.createEmpty())
            return
        }

        // Get image cells sorted by column
        val imageCells = if (line.hasImageCells()) {
            line.getAllImageCells().toSortedMap()
        } else {
            sortedMapOf()
        }
        val placedImageCols = mutableSetOf<Int>()

        // Track current input column position
        var inputCol = 0
        var addedContent = false

        // Process text entries, interleaving image cells at their original positions
        line.forEachEntry(Consumer { entry: TerminalLine.TextEntry? ->
            if (entry?.isNul != false) {
                return@Consumer
            }
            var entryProcessedLength = 0
            while (entryProcessedLength < entry.length) {
                // Place any image cells that come before current text position
                val currentInputPos = inputCol + entryProcessedLength
                for ((imgCol, imgCell) in imageCells) {
                    if (imgCol !in placedImageCols && imgCol <= currentInputPos) {
                        placeImageCell(imgCell)
                        placedImageCols.add(imgCol)
                        addedContent = true
                    }
                }

                // Ensure we have a current line
                ensureCurrentLine()

                // Calculate maximum length we can take
                val maxLen = min(myNewWidth - myCurrentLineLength, entry.length - entryProcessedLength)

                // Get the text that we're about to split to analyze grapheme boundaries
                val textToSplit = entry.text.subBuffer(entryProcessedLength, maxLen).toString()

                // Find safe grapheme boundary
                val safeLen = GraphemeBoundaryUtils.findLastCompleteGraphemeBoundary(textToSplit)
                val len = if (safeLen > 0) safeLen else 1

                val newEntry: TerminalLine.TextEntry = Companion.subEntry(entry, entryProcessedLength, len)
                myCurrentLine?.appendEntry(newEntry)
                myCurrentLineLength += len
                entryProcessedLength += len
                addedContent = true

                // Wrap if line is full
                if (myCurrentLineLength == myNewWidth) {
                    myCurrentLine?.isWrapped = true
                    myCurrentLine = null
                    myCurrentLineLength = 0
                }
            }
            inputCol += entry.length
        })

        // Place any remaining image cells
        for ((imgCol, imgCell) in imageCells) {
            if (imgCol !in placedImageCols) {
                placeImageCell(imgCell)
                placedImageCols.add(imgCol)
                addedContent = true
            }
        }

        // Handle empty lines that aren't truly null
        if (!addedContent && myCurrentLine == null) {
            myAllLines.add(TerminalLine.Companion.createEmpty())
        }

        if (!line.isWrapped) {
            myCurrentLine = null
            myCurrentLineLength = 0
        }
    }

    /**
     * Ensure current line exists. Creates new line if needed.
     */
    private fun ensureCurrentLine() {
        if (myCurrentLine == null) {
            val newLine = TerminalLine()
            myCurrentLine = newLine
            myCurrentLineLength = 0
            myAllLines.add(newLine)
        }
    }

    /**
     * Place a single image cell at current position.
     * Image cells are treated like text - one cell per column position, wrap when line full.
     */
    private fun placeImageCell(cell: ImageCell) {
        // Wrap if line is full
        if (myCurrentLine != null && myCurrentLineLength == myNewWidth) {
            myCurrentLine?.isWrapped = true
            myCurrentLine = null
            myCurrentLineLength = 0
        }
        ensureCurrentLine()

        myCurrentLine?.setImageCell(myCurrentLineLength, cell)
        myCurrentLineLength++
    }

    class TrackingPoint(val x: Int, val y: Int, val forceVisible: Boolean) {
        constructor(p: Point, forceVisible: Boolean) : this(p.x, p.y, forceVisible)

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is TrackingPoint) return false
            return this.x == other.x && this.y == other.y && this.forceVisible == other.forceVisible
        }

        override fun hashCode(): Int {
            return Objects.hash(this.x, this.y, this.forceVisible)
        }
    }

    companion object {
        private val LOG: Logger = LoggerFactory.getLogger(TerminalTextBuffer::class.java)

        private fun subEntry(entry: TerminalLine.TextEntry, startInd: Int, count: Int): TerminalLine.TextEntry {
            if (startInd == 0 && count == entry.length) {
                return entry
            }
            return TerminalLine.TextEntry(entry.style, entry.text.subBuffer(startInd, count))
        }
    }
}
