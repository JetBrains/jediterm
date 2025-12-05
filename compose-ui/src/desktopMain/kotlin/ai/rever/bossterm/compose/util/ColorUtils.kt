package ai.rever.bossterm.compose.util

import androidx.compose.ui.graphics.Color
import ai.rever.bossterm.terminal.TerminalColor
import ai.rever.bossterm.terminal.emulator.ColorPaletteImpl

/**
 * Utility functions for color conversion in terminal rendering.
 */
object ColorUtils {
    // Use XTerm color palette for consistency with original BossTerm
    private val colorPalette = ColorPaletteImpl.XTERM_PALETTE

    /**
     * Convert BossTerm TerminalColor to Compose Color using the official ColorPalette.
     * Uses ColorPalette for colors 0-15 to support themes, otherwise uses toColor().
     */
    fun convertTerminalColor(terminalColor: TerminalColor?): Color {
        if (terminalColor == null) return Color.Black

        // Use ColorPalette for colors 0-15 to support themes, otherwise use toColor()
        val bossColor = if (terminalColor.isIndexed && terminalColor.colorIndex < 16) {
            colorPalette.getForeground(terminalColor)
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
