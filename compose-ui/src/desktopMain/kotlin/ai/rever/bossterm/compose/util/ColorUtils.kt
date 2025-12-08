package ai.rever.bossterm.compose.util

import androidx.compose.ui.graphics.Color
import ai.rever.bossterm.terminal.TerminalColor
import ai.rever.bossterm.terminal.emulator.ColorPalette
import ai.rever.bossterm.terminal.emulator.ColorPaletteImpl
import ai.rever.bossterm.compose.settings.theme.ColorPaletteManager
import ai.rever.bossterm.compose.settings.theme.Theme
import ai.rever.bossterm.compose.settings.theme.ThemeManager
import ai.rever.bossterm.compose.settings.theme.ColorPalette as SettingsColorPalette

/**
 * Utility functions for color conversion in terminal rendering.
 */
object ColorUtils {
    // Default XTerm color palette (fallback)
    private val defaultPalette = ColorPaletteImpl.XTERM_PALETTE

    /**
     * Get the current color palette based on the selected color palette (or theme's palette if none selected).
     * Falls back to XTERM_PALETTE if managers are not initialized.
     */
    private fun getCurrentPalette(): ColorPalette {
        return try {
            // First try to get the selected color palette from ColorPaletteManager
            val paletteManager = ColorPaletteManager.instance
            val selectedPalette = paletteManager.currentPalette.value

            if (selectedPalette != null) {
                // Use the explicitly selected color palette
                createPaletteFromSettingsPalette(selectedPalette)
            } else {
                // Fall back to the current theme's palette
                val theme = ThemeManager.instance.currentTheme.value
                createPaletteFromTheme(theme)
            }
        } catch (e: Exception) {
            defaultPalette
        }
    }

    /**
     * Create a ColorPalette from a settings ColorPalette's ANSI colors.
     */
    private fun createPaletteFromSettingsPalette(palette: SettingsColorPalette): ColorPalette {
        val colors = IntArray(16) { index ->
            parseHexColor(palette.getAnsiColorHex(index))
        }
        return ColorPaletteImpl.fromRgbInts(colors)
    }

    /**
     * Create a ColorPalette from a Theme's ANSI colors.
     */
    private fun createPaletteFromTheme(theme: Theme): ColorPalette {
        val colors = IntArray(16) { index ->
            parseHexColor(theme.getAnsiColorHex(index))
        }
        return ColorPaletteImpl.fromRgbInts(colors)
    }

    /**
     * Parse a hex color string (0xAARRGGBB or 0xRRGGBB) to RGB int.
     */
    private fun parseHexColor(hex: String): Int {
        val cleanHex = hex.removePrefix("0x").removePrefix("#")
        return when (cleanHex.length) {
            6 -> cleanHex.toLong(16).toInt()
            8 -> cleanHex.substring(2).toLong(16).toInt() // Skip alpha
            else -> 0xFFFFFF
        }
    }

    /**
     * Convert BossTerm TerminalColor to Compose Color using the active theme's palette.
     * Uses ColorPalette for colors 0-15 to support themes, otherwise uses toColor().
     */
    fun convertTerminalColor(terminalColor: TerminalColor?): Color {
        if (terminalColor == null) return Color.Black

        // Use theme-aware ColorPalette for colors 0-15, otherwise use toColor()
        val bossColor = if (terminalColor.isIndexed && terminalColor.colorIndex < 16) {
            getCurrentPalette().getForeground(terminalColor)
        } else {
            terminalColor.toColor()
        }

        return Color(
            red = bossColor.red / 255f,
            green = bossColor.green / 255f,
            blue = bossColor.blue / 255f
        )
    }

    /**
     * Convert BossTerm TerminalColor to Compose Color using a specific theme's palette.
     * This is useful for rendering with a specific theme regardless of the global setting.
     */
    fun convertTerminalColor(terminalColor: TerminalColor?, theme: Theme): Color {
        if (terminalColor == null) return Color.Black

        val palette = createPaletteFromTheme(theme)
        val bossColor = if (terminalColor.isIndexed && terminalColor.colorIndex < 16) {
            palette.getForeground(terminalColor)
        } else {
            terminalColor.toColor()
        }

        return Color(
            red = bossColor.red / 255f,
            green = bossColor.green / 255f,
            blue = bossColor.blue / 255f
        )
    }

    /**
     * Apply DIM attribute by reducing color brightness to 50%.
     */
    fun applyDimColor(color: Color): Color {
        return Color(
            red = color.red * 0.5f,
            green = color.green * 0.5f,
            blue = color.blue * 0.5f,
            alpha = color.alpha
        )
    }
}
