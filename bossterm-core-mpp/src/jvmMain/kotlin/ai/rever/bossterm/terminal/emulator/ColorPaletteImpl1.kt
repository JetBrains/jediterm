package ai.rever.bossterm.terminal.emulator

import ai.rever.bossterm.core.Color

class ColorPaletteImpl private constructor(private val myColors: Array<Color>) : ColorPalette() {
    public override fun getForegroundByColorIndex(colorIndex: Int): Color {
        return myColors[colorIndex]
    }

    override fun getBackgroundByColorIndex(colorIndex: Int): Color {
        return myColors[colorIndex]
    }

    /**
     * Create a new palette with a specific ANSI color replaced.
     */
    fun withColor(index: Int, color: Color): ColorPaletteImpl {
        require(index in 0..15) { "Color index must be 0-15, got $index" }
        val newColors = myColors.copyOf()
        newColors[index] = color
        return ColorPaletteImpl(newColors)
    }

    companion object {
        /**
         * Create a ColorPalette from an array of 16 ANSI colors.
         * Colors should be in order: Black, Red, Green, Yellow, Blue, Magenta, Cyan, White,
         * then Bright versions of each.
         */
        fun fromColors(colors: Array<Color>): ColorPalette {
            require(colors.size == 16) { "Palette must have exactly 16 colors, got ${colors.size}" }
            return ColorPaletteImpl(colors)
        }

        /**
         * Create a ColorPalette from RGB integer values (0xRRGGBB format).
         */
        fun fromRgbInts(colors: IntArray): ColorPalette {
            require(colors.size == 16) { "Palette must have exactly 16 colors, got ${colors.size}" }
            return ColorPaletteImpl(colors.map { Color(it) }.toTypedArray())
        }

        private val XTERM_COLORS = arrayOf<Color>(
            Color(0x000000),  //Black
            Color(0xcd0000),  //Red 
            Color(0x00cd00),  //Green
            Color(0xcdcd00),  //Yellow
            Color(0x1e90ff),  //Blue 
            Color(0xcd00cd),  //Magenta
            Color(0x00cdcd),  //Cyan
            Color(0xe5e5e5),  //White
            //Bright versions of the ISO colors
            Color(0x4c4c4c),  //Black 
            Color(0xff0000),  //Red
            Color(0x00ff00),  //Green
            Color(0xffff00),  //Yellow
            Color(0x4682b4),  //Blue
            Color(0xff00ff),  //Magenta
            Color(0x00ffff),  //Cyan
            Color(0xffffff),  //White
        )

        val XTERM_PALETTE: ColorPalette = ColorPaletteImpl(XTERM_COLORS)

        private val WINDOWS_COLORS = arrayOf<Color>(
            Color(0x000000),  //Black
            Color(0x800000),  //Red 
            Color(0x008000),  //Green
            Color(0x808000),  //Yellow
            Color(0x000080),  //Blue 
            Color(0x800080),  //Magenta
            Color(0x008080),  //Cyan
            Color(0xc0c0c0),  //White
            //Bright versions of the ISO colors
            Color(0x808080),  //Black 
            Color(0xff0000),  //Red
            Color(0x00ff00),  //Green
            Color(0xffff00),  //Yellow
            Color(0x4682b4),  //Blue
            Color(0xff00ff),  //Magenta
            Color(0x00ffff),  //Cyan
            Color(0xffffff),  //White
        )

        val WINDOWS_PALETTE: ColorPalette = ColorPaletteImpl(WINDOWS_COLORS)
    }
}
