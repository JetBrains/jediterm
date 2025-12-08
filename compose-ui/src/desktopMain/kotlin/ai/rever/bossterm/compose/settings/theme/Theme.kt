package ai.rever.bossterm.compose.settings.theme

import androidx.compose.ui.graphics.Color
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/**
 * Represents a complete terminal color theme.
 *
 * Contains:
 * - Terminal colors (foreground, background, cursor, selection, etc.)
 * - Full 16-color ANSI palette (colors 0-15)
 * - Metadata (id, name, builtin flag)
 *
 * All colors are stored as ARGB hex strings (e.g., "0xFFFFFFFF").
 */
@Serializable
data class Theme(
    val id: String,
    val name: String,

    // Terminal colors
    val foreground: String,
    val background: String,
    val cursor: String,
    val cursorText: String,
    val selection: String,
    val selectionText: String,
    val searchMatch: String,
    val hyperlink: String,

    // ANSI 16-color palette (colors 0-15)
    val black: String,           // Color 0
    val red: String,             // Color 1
    val green: String,           // Color 2
    val yellow: String,          // Color 3
    val blue: String,            // Color 4
    val magenta: String,         // Color 5
    val cyan: String,            // Color 6
    val white: String,           // Color 7
    val brightBlack: String,     // Color 8
    val brightRed: String,       // Color 9
    val brightGreen: String,     // Color 10
    val brightYellow: String,    // Color 11
    val brightBlue: String,      // Color 12
    val brightMagenta: String,   // Color 13
    val brightCyan: String,      // Color 14
    val brightWhite: String,     // Color 15

    val isBuiltin: Boolean = false
) {
    // Computed Color properties for direct use
    @Transient val foregroundColor: Color = parseColor(foreground)
    @Transient val backgroundColorValue: Color = parseColor(background)
    @Transient val cursorColor: Color = parseColor(cursor)
    @Transient val cursorTextColor: Color = parseColor(cursorText)
    @Transient val selectionColor: Color = parseColor(selection)
    @Transient val selectionTextColor: Color = parseColor(selectionText)
    @Transient val searchMatchColor: Color = parseColor(searchMatch)
    @Transient val hyperlinkColor: Color = parseColor(hyperlink)

    /**
     * Get ANSI color by index (0-15).
     */
    fun getAnsiColor(index: Int): Color {
        return when (index) {
            0 -> parseColor(black)
            1 -> parseColor(red)
            2 -> parseColor(green)
            3 -> parseColor(yellow)
            4 -> parseColor(blue)
            5 -> parseColor(magenta)
            6 -> parseColor(cyan)
            7 -> parseColor(white)
            8 -> parseColor(brightBlack)
            9 -> parseColor(brightRed)
            10 -> parseColor(brightGreen)
            11 -> parseColor(brightYellow)
            12 -> parseColor(brightBlue)
            13 -> parseColor(brightMagenta)
            14 -> parseColor(brightCyan)
            15 -> parseColor(brightWhite)
            else -> Color.White
        }
    }

    /**
     * Get all 16 ANSI colors as an array.
     */
    fun getAnsiPalette(): Array<Color> {
        return Array(16) { getAnsiColor(it) }
    }

    /**
     * Get ANSI color hex string by index (0-15).
     */
    fun getAnsiColorHex(index: Int): String {
        return when (index) {
            0 -> black
            1 -> red
            2 -> green
            3 -> yellow
            4 -> blue
            5 -> magenta
            6 -> cyan
            7 -> white
            8 -> brightBlack
            9 -> brightRed
            10 -> brightGreen
            11 -> brightYellow
            12 -> brightBlue
            13 -> brightMagenta
            14 -> brightCyan
            15 -> brightWhite
            else -> "0xFFFFFFFF"
        }
    }

    /**
     * Create a copy with a specific ANSI color changed.
     */
    fun withAnsiColor(index: Int, colorHex: String): Theme {
        return when (index) {
            0 -> copy(black = colorHex)
            1 -> copy(red = colorHex)
            2 -> copy(green = colorHex)
            3 -> copy(yellow = colorHex)
            4 -> copy(blue = colorHex)
            5 -> copy(magenta = colorHex)
            6 -> copy(cyan = colorHex)
            7 -> copy(white = colorHex)
            8 -> copy(brightBlack = colorHex)
            9 -> copy(brightRed = colorHex)
            10 -> copy(brightGreen = colorHex)
            11 -> copy(brightYellow = colorHex)
            12 -> copy(brightBlue = colorHex)
            13 -> copy(brightMagenta = colorHex)
            14 -> copy(brightCyan = colorHex)
            15 -> copy(brightWhite = colorHex)
            else -> this
        }
    }

    companion object {
        /**
         * Parse a hex color string to Compose Color.
         * Supports formats: "0xAARRGGBB", "0xRRGGBB", "#AARRGGBB", "#RRGGBB"
         */
        fun parseColor(hex: String): Color {
            return try {
                val cleanHex = hex.removePrefix("0x").removePrefix("#")
                val longValue = when (cleanHex.length) {
                    6 -> "FF$cleanHex".toLong(16)  // Add full alpha
                    8 -> cleanHex.toLong(16)
                    else -> 0xFFFFFFFF
                }
                Color(longValue)
            } catch (e: Exception) {
                Color.White
            }
        }

        /**
         * Convert Color to hex string.
         */
        fun colorToHex(color: Color): String {
            val alpha = (color.alpha * 255).toInt()
            val red = (color.red * 255).toInt()
            val green = (color.green * 255).toInt()
            val blue = (color.blue * 255).toInt()
            return "0x%02X%02X%02X%02X".format(alpha, red, green, blue)
        }

        /**
         * ANSI color names for display.
         */
        val ANSI_COLOR_NAMES = listOf(
            "Black", "Red", "Green", "Yellow", "Blue", "Magenta", "Cyan", "White",
            "Bright Black", "Bright Red", "Bright Green", "Bright Yellow",
            "Bright Blue", "Bright Magenta", "Bright Cyan", "Bright White"
        )
    }
}
