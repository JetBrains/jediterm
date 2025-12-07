package ai.rever.bossterm.core.compatibility

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

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Point) return false
        return x == other.x && y == other.y
    }

    override fun hashCode(): Int {
        return Objects.hash(x, y)
    }

    override fun toString(): String {
        return "[x=$x,y=$y]"
    }
}
