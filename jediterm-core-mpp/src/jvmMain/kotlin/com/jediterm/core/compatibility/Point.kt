package com.jediterm.core.compatibility

import java.util.*

class Point {
    @JvmField
    var x: Int = 0
    @JvmField
    var y: Int = 0

    @JvmOverloads
    constructor(x: Int = 0, y: Int = 0) {
        setLocation(x, y)
    }

    constructor(other: Point) {
        setLocation(other)
    }

    fun setLocation(x: Int, y: Int) {
        this.x = x
        this.y = y
    }

    fun setLocation(p: Point) {
        x = p.x
        y = p.y
    }

    override fun equals(o: Any?): Boolean {
        if (this === o) return true
        if (o !is Point) return false
        val point = o
        return x == point.x && y == point.y
    }

    override fun hashCode(): Int {
        return Objects.hash(x, y)
    }

    override fun toString(): String {
        return "[x=$x,y=$y]"
    }
}
