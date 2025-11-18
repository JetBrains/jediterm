package com.jediterm.terminal.emulator

import com.jediterm.core.Color
import com.jediterm.terminal.TerminalColor

/**
 * @author traff
 */
abstract class ColorPalette {
    fun getForeground(color: TerminalColor): Color {
        if (color.isIndexed) {
            val colorIndex = color.colorIndex
            assertColorIndexIsLessThan16(colorIndex)
            return getForegroundByColorIndex(colorIndex)
        }
        return color.toColor()
    }

    protected abstract fun getForegroundByColorIndex(colorIndex: Int): Color

    fun getBackground(color: TerminalColor): Color {
        if (color.isIndexed) {
            val colorIndex = color.colorIndex
            assertColorIndexIsLessThan16(colorIndex)
            return getBackgroundByColorIndex(colorIndex)
        }
        return color.toColor()
    }

    protected abstract fun getBackgroundByColorIndex(colorIndex: Int): Color

    private fun assertColorIndexIsLessThan16(colorIndex: Int) {
        if (colorIndex < 0 || colorIndex >= 16) {
            throw AssertionError("Color index is out of bounds [0,15]: " + colorIndex)
        }
    }

    companion object {
        fun getIndexedTerminalColor(colorIndex: Int): TerminalColor? {
            return if (colorIndex < 16) TerminalColor.Companion.index(colorIndex) else getXTerm256(colorIndex)
        }

        private fun getXTerm256(colorIndex: Int): TerminalColor? {
            return if (colorIndex < 256) COL_RES_256[colorIndex - 16] else null
        }

        //The code below is translation of xterm's 256colres.pl
        private val COL_RES_256 = arrayOfNulls<TerminalColor>(240)

        init {
            // colors 16-231 are a 6x6x6 color cube
            for (red in 0..5) {
                for (green in 0..5) {
                    for (blue in 0..5) {
                        COL_RES_256[36 * red + 6 * green + blue] = TerminalColor(
                            getCubeColorValue(red),
                            getCubeColorValue(green),
                            getCubeColorValue(blue)
                        )
                    }
                }
            }

            // colors 232-255 are a grayscale ramp, intentionally leaving out
            // black and white
            for (gray in 0..23) {
                val level = 10 * gray + 8
                COL_RES_256[216 + gray] = TerminalColor(level, level, level)
            }
        }

        private fun getCubeColorValue(value: Int): Int {
            return if (value == 0) 0 else (40 * value + 55)
        }
    }
}
