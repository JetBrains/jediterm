package com.jediterm.terminal.model

import com.jediterm.core.compatibility.Point
import kotlin.math.max
import kotlin.math.min

/**
 * @author traff
 */
class TerminalSelection {
    val start: Point

    private var myEnd: Point? = null

    constructor(start: Point) {
        this.start = start
    }

    constructor(start: Point, end: Point) {
        this.start = start
        myEnd = end
    }

    val end: Point?
        get() = myEnd

    fun updateEnd(end: Point) {
        myEnd = end
    }

    fun pointsForRun(width: Int): Pair<Point?, Point?> {
        val endPoint = myEnd ?: return Pair(Point(start), null)
        val p = SelectionUtil.sortPoints(
            Point(start),
            Point(endPoint)
        )
        p.second?.let { it.x = min(it.x + 1, width) }
        return p
    }

    fun contains(toTest: Point): Boolean {
        return intersects(toTest.x, toTest.y, 1)
    }

    fun shiftY(dy: Int) {
        start.y += dy
        myEnd?.let { it.y += dy }
    }

    fun intersects(x: Int, row: Int, length: Int): Boolean {
        return null != intersect(x, row, length)
    }

    fun intersect(x: Int, row: Int, length: Int): Pair<Int?, Int?>? {
        val endPoint = myEnd ?: return null
        var newX = x
        val newLength: Int

        val p = SelectionUtil.sortPoints(
            Point(start),
            Point(endPoint)
        )

        val first = p.first ?: return null
        val second = p.second ?: return null

        if (first.y == row) {
            newX = max(x, first.x)
        }

        if (second.y == row) {
            newLength = min(second.x, x + length - 1) - newX + 1
        } else {
            newLength = length - newX + x
        }

        if (newLength <= 0 || row < first.y || row > second.y) {
            return null
        } else return Pair<Int?, Int?>(newX, newLength)
    }

    override fun toString(): String {
        return myEnd?.let {
            "[x=" + start.x + ",y=" + start.y + "]" + " -> [x=" + it.x + ",y=" + it.y + "]"
        } ?: "[x=" + start.x + ",y=" + start.y + "] -> [no end]"
    }
}
