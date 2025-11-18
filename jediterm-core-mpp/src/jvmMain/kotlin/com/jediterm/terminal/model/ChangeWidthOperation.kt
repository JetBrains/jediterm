package com.jediterm.terminal.model

import com.jediterm.core.compatibility.Point
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

    fun addPointToTrack(point: Point, isForceVisible: Boolean) {
        if (isForceVisible && (point.y < 0 || point.y >= myTextBuffer.height)) {
            LOG.warn("Registered visible point " + point + " is outside screen: [0, " + (myTextBuffer.height - 1) + "]")
            point.y = min(max(point.y, 0), myTextBuffer.height - 1)
        }
        myTrackingPoints.put(TrackingPoint(point, isForceVisible), null)
    }

    fun getTrackedPoint(original: Point): Point {
        var result = myTrackingPoints.get(TrackingPoint(original, false))
        if (result != null) {
            return result
        }
        result = myTrackingPoints.get(TrackingPoint(original, true))
        if (result != null) {
            return result
        }
        LOG.warn("Not tracked point: " + original)
        return original
    }

    fun run() {
        val historyLinesStorage = myTextBuffer.getHistoryLinesStorageOrBackup()
        for (i in 0..<historyLinesStorage.size) {
            val line = historyLinesStorage.get(i)
            addLine(line)
        }
        var screenStartInd = myAllLines.size - 1
        if (myCurrentLine == null || myCurrentLineLength == myNewWidth) {
            screenStartInd++
        }
        if (screenStartInd < 0) {
            throw IndexOutOfBoundsException("screenStartInd < 0: " + screenStartInd)
        }
        val screenLinesStorage = myTextBuffer.getScreenLinesStorageOrBackup()
        if (screenLinesStorage.size > myTextBuffer.height) {
            LOG.warn("Terminal height < screen buffer line count: " + myTextBuffer.height + " < " + screenLinesStorage.size)
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

        screenStartInd = max(screenStartInd, myAllLines.size - min(myAllLines.size, myNewHeight) - emptyBottomLineCount)
        screenStartInd = min(screenStartInd, myAllLines.size - min(myAllLines.size, myNewHeight))
        screenStartInd = max(screenStartInd, bottomMostPointY - myNewHeight + 1)
        historyLinesStorage.clear()
        historyLinesStorage.addAllToBottom(myAllLines.subList(0, screenStartInd).filterNotNull())
        screenLinesStorage.clear()
        screenLinesStorage.addAllToBottom(
            myAllLines.subList(
                screenStartInd,
                min(screenStartInd + myNewHeight, myAllLines.size)
            ).filterNotNull()
        )
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
        if (line.isNul) {
            if (myCurrentLine != null) {
                myCurrentLine = null
                myCurrentLineLength = 0
            }
            myAllLines.add(TerminalLine.Companion.createEmpty())
            return
        }
        line.forEachEntry(Consumer { entry: TerminalLine.TextEntry? ->
            if (entry?.isNul != false) {
                return@Consumer
            }
            var entryProcessedLength = 0
            while (entryProcessedLength < entry.length) {
                val currentLine = myCurrentLine
                if (currentLine != null && myCurrentLineLength == myNewWidth) {
                    currentLine.isWrapped = true
                    myCurrentLine = null
                    myCurrentLineLength = 0
                }
                if (myCurrentLine == null) {
                    val newLine = TerminalLine()
                    myCurrentLine = newLine
                    myCurrentLineLength = 0
                    myAllLines.add(newLine)
                }
                val len = min(myNewWidth - myCurrentLineLength, entry.length - entryProcessedLength)
                val newEntry: TerminalLine.TextEntry = Companion.subEntry(entry, entryProcessedLength, len)
                myCurrentLine?.appendEntry(newEntry)
                myCurrentLineLength += len
                entryProcessedLength += len
            }
        })
        if (!line.isWrapped) {
            myCurrentLine = null
            myCurrentLineLength = 0
        }
    }

    class TrackingPoint(val x: Int, val y: Int, val forceVisible: Boolean) {
        constructor(p: Point, forceVisible: Boolean) : this(p.x, p.y, forceVisible)

        override fun equals(o: Any?): Boolean {
            if (this === o) return true
            if (o !is TrackingPoint) return false
            val that = o
            return this.x == that.x && this.y == that.y && this.forceVisible == that.forceVisible
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
