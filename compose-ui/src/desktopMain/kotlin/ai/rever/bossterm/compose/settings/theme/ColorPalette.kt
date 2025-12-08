package ai.rever.bossterm.compose.settings.theme

import androidx.compose.ui.graphics.Color
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/**
 * Represents a 16-color ANSI palette that can be applied independently of a theme.
 *
 * This allows users to mix and match:
 * - Theme: Controls terminal colors (foreground, background, cursor, selection, etc.)
 * - Color Palette: Controls the 16 ANSI colors used by terminal applications
 *
 * All colors are stored as ARGB hex strings (e.g., "0xFFFFFFFF").
 */
@Serializable
data class ColorPalette(
    val id: String,
    val name: String,

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
    /**
     * Get ANSI color by index (0-15).
     */
    fun getAnsiColor(index: Int): Color {
        return when (index) {
            0 -> Theme.parseColor(black)
            1 -> Theme.parseColor(red)
            2 -> Theme.parseColor(green)
            3 -> Theme.parseColor(yellow)
            4 -> Theme.parseColor(blue)
            5 -> Theme.parseColor(magenta)
            6 -> Theme.parseColor(cyan)
            7 -> Theme.parseColor(white)
            8 -> Theme.parseColor(brightBlack)
            9 -> Theme.parseColor(brightRed)
            10 -> Theme.parseColor(brightGreen)
            11 -> Theme.parseColor(brightYellow)
            12 -> Theme.parseColor(brightBlue)
            13 -> Theme.parseColor(brightMagenta)
            14 -> Theme.parseColor(brightCyan)
            15 -> Theme.parseColor(brightWhite)
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
    fun withAnsiColor(index: Int, colorHex: String): ColorPalette {
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
         * Special ID indicating "use theme's palette" (no override).
         */
        const val USE_THEME_PALETTE_ID = "use-theme"

        /**
         * ANSI color names for display.
         */
        val ANSI_COLOR_NAMES = listOf(
            "Black", "Red", "Green", "Yellow", "Blue", "Magenta", "Cyan", "White",
            "Bright Black", "Bright Red", "Bright Green", "Bright Yellow",
            "Bright Blue", "Bright Magenta", "Bright Cyan", "Bright White"
        )

        /**
         * Extract a ColorPalette from a Theme.
         */
        fun fromTheme(theme: Theme): ColorPalette {
            return ColorPalette(
                id = "theme-${theme.id}",
                name = "${theme.name} (Theme)",
                black = theme.black,
                red = theme.red,
                green = theme.green,
                yellow = theme.yellow,
                blue = theme.blue,
                magenta = theme.magenta,
                cyan = theme.cyan,
                white = theme.white,
                brightBlack = theme.brightBlack,
                brightRed = theme.brightRed,
                brightGreen = theme.brightGreen,
                brightYellow = theme.brightYellow,
                brightBlue = theme.brightBlue,
                brightMagenta = theme.brightMagenta,
                brightCyan = theme.brightCyan,
                brightWhite = theme.brightWhite,
                isBuiltin = true
            )
        }
    }
}
