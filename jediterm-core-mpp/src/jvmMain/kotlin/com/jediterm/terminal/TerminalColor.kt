package com.jediterm.terminal

import com.jediterm.core.Color
import java.util.*
import java.util.function.Supplier

/**
 * @author traff
 */
class TerminalColor private constructor(colorIndex: Int, color: Color?, colorSupplier: Supplier<Color?>?) {
    val colorIndex: Int
    private val myColor: Color?
    private val myColorSupplier: Supplier<Color?>?

    constructor(colorIndex: Int) : this(colorIndex, null, null)

    constructor(r: Int, g: Int, b: Int) : this(-1, Color(r, g, b), null)

    constructor(colorSupplier: Supplier<Color?>) : this(-1, null, colorSupplier)

    init {
        if (colorIndex != -1) {
            assert(color == null)
            assert(colorSupplier == null)
        } else if (color != null) {
            assert(colorSupplier == null)
        } else {
            checkNotNull(colorSupplier)
        }
        this.colorIndex = colorIndex
        myColor = color
        myColorSupplier = colorSupplier
    }

    val isIndexed: Boolean
        get() = this.colorIndex != -1

    fun toColor(): Color {
        require(!this.isIndexed) { "Color is indexed color so a palette is needed" }

        return myColor ?: myColorSupplier?.get() ?: throw IllegalStateException("Color must be non-null")
    }

    override fun equals(o: Any?): Boolean {
        if (this === o) return true
        if (o == null || javaClass != o.javaClass) return false
        val that = o as TerminalColor
        return this.colorIndex == that.colorIndex && myColor == that.myColor
    }

    override fun hashCode(): Int {
        return Objects.hash(this.colorIndex, myColor)
    }

    companion object {
        val BLACK: TerminalColor = index(0)
        val WHITE: TerminalColor = index(15)

        fun index(colorIndex: Int): TerminalColor {
            return TerminalColor(colorIndex)
        }

        fun rgb(r: Int, g: Int, b: Int): TerminalColor {
            return TerminalColor(r, g, b)
        }

        fun fromColor(color: Color?): TerminalColor? {
            if (color == null) {
                return null
            }
            return rgb(color.red, color.green, color.blue)
        }
    }
}
