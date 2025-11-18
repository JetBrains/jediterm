package com.jediterm.terminal.model

import com.jediterm.core.compatibility.Point
import com.jediterm.terminal.util.CharUtils
import kotlin.math.max
import kotlin.math.min

/**
 * @author traff
 */
object SelectionUtil {
    private val SEPARATORS: MutableList<Char?> = ArrayList<Char?>()

    init {
        SEPARATORS.add(' ')
        SEPARATORS.add('\u00A0') // NO-BREAK SPACE
        SEPARATORS.add('\t')
        SEPARATORS.add('\'')
        SEPARATORS.add('"')
        SEPARATORS.add('$')
        SEPARATORS.add('(')
        SEPARATORS.add(')')
        SEPARATORS.add('[')
        SEPARATORS.add(']')
        SEPARATORS.add('{')
        SEPARATORS.add('}')
        SEPARATORS.add('<')
        SEPARATORS.add('>')
    }

    fun sortPoints(a: Point, b: Point): Pair<Point, Point> {
        if (a.y == b.y) { /* same line */
            return Pair<Point, Point>(if (a.x <= b.x) a else b, if (a.x > b.x) a else b)
        } else {
            return Pair<Point, Point>(if (a.y < b.y) a else b, if (a.y > b.y) a else b)
        }
    }

    fun getSelectionText(selection: TerminalSelection, terminalTextBuffer: TerminalTextBuffer): String {
        return getSelectionText(selection.start, selection.end ?: selection.start, terminalTextBuffer)
    }

    fun getSelectionText(
        selectionStart: Point,
        selectionEnd: Point,
        terminalTextBuffer: TerminalTextBuffer
    ): String {
        var pair = sortPoints(selectionStart, selectionEnd)
        pair.first.y = max(pair.first.y, -terminalTextBuffer.historyLinesCount)
        pair = sortPoints(pair.first, pair.second) // previous line may have changed the order

        val top = pair.first
        val bottom = pair.second

        val selectionText = StringBuilder()

        for (i in top.y..bottom.y) {
            val line = terminalTextBuffer.getLine(i)
            val text = line.text
            if (i == top.y) {
                if (i == bottom.y) {
                    selectionText.append(
                        processForSelection(
                            text.substring(
                                min(text.length, top.x),
                                min(text.length, bottom.x)
                            )
                        )
                    )
                } else {
                    selectionText.append(processForSelection(text.substring(min(text.length, top.x))))
                }
            } else if (i == bottom.y) {
                selectionText.append(processForSelection(text.substring(0, min(text.length, bottom.x))))
            } else {
                selectionText.append(processForSelection(line.text))
            }
            if ((!line.isWrapped && i < bottom.y) || bottom.x > text.length) {
                selectionText.append("\n")
            }
        }

        return selectionText.toString()
    }

    private fun processForSelection(text: String): String {
        if (text.indexOf(CharUtils.DWC) != 0) {
            // remove dwc second chars
            val sb = StringBuilder()
            for (c in text.toCharArray()) {
                if (c != CharUtils.DWC) {
                    sb.append(c)
                }
            }
            return sb.toString()
        } else {
            return text
        }
    }

    fun getPreviousSeparator(charCoords: Point, terminalTextBuffer: TerminalTextBuffer): Point {
        return getPreviousSeparator(charCoords, terminalTextBuffer, SEPARATORS)
    }

    fun getPreviousSeparator(
        charCoords: Point,
        terminalTextBuffer: TerminalTextBuffer,
        separators: MutableList<Char?>
    ): Point {
        var x = charCoords.x
        var y = charCoords.y
        val terminalWidth = terminalTextBuffer.width

        if (separators.contains(terminalTextBuffer.getBuffersCharAt(x, y))) {
            return Point(x, y)
        }

        var line = terminalTextBuffer.getLine(y).text
        while (x < line.length && !separators.contains(line.get(x))) {
            x--
            if (x < 0) {
                if (y <= -terminalTextBuffer.historyLinesCount) {
                    return Point(0, y)
                }
                y--
                x = terminalWidth - 1

                line = terminalTextBuffer.getLine(y).text
            }
        }

        x++
        if (x >= terminalWidth) {
            y++
            x = 0
        }

        return Point(x, y)
    }

    fun getNextSeparator(charCoords: Point, terminalTextBuffer: TerminalTextBuffer): Point {
        return getNextSeparator(charCoords, terminalTextBuffer, SEPARATORS)
    }

    fun getNextSeparator(
        charCoords: Point,
        terminalTextBuffer: TerminalTextBuffer,
        separators: MutableList<Char?>
    ): Point {
        var x = charCoords.x
        var y = charCoords.y
        val terminalWidth = terminalTextBuffer.width
        val terminalHeight = terminalTextBuffer.height

        if (separators.contains(terminalTextBuffer.getBuffersCharAt(x, y))) {
            return Point(x, y)
        }

        var line = terminalTextBuffer.getLine(y).text
        while (x < line.length && !separators.contains(line.get(x))) {
            x++
            if (x >= terminalWidth) {
                if (y >= terminalHeight - 1) {
                    return Point(terminalWidth - 1, terminalHeight - 1)
                }
                y++
                x = 0

                line = terminalTextBuffer.getLine(y).text
            }
        }

        x--
        if (x < 0) {
            y--
            x = terminalWidth - 1
        }

        return Point(x, y)
    }
}
