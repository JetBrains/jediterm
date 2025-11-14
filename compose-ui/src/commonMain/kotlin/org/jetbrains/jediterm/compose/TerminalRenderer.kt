package org.jetbrains.jediterm.compose

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight

/**
 * Interface for rendering terminal content using Compose Canvas API.
 * This abstracts the rendering logic from the UI framework.
 */
interface TerminalRenderer {
    /**
     * Font configuration for rendering
     */
    data class FontConfig(
        val family: FontFamily,
        val size: Float,
        val lineSpacing: Float = 1.0f
    )

    /**
     * Character style information
     */
    data class CharacterStyle(
        val foreground: Color,
        val background: Color,
        val isBold: Boolean = false,
        val isItalic: Boolean = false,
        val isUnderline: Boolean = false,
        val isStrikethrough: Boolean = false,
        val isInverse: Boolean = false
    )

    /**
     * Dimensions of a single character cell
     */
    data class CellDimensions(
        val width: Float,
        val height: Float
    )

    /**
     * Initialize the renderer with font configuration
     */
    fun initialize(fontConfig: FontConfig)

    /**
     * Get the dimensions of a single character cell
     */
    fun getCellDimensions(): CellDimensions

    /**
     * Calculate grid dimensions based on available space
     */
    fun calculateGridDimensions(availableWidth: Float, availableHeight: Float): Pair<Int, Int>

    /**
     * Render a character at the specified grid position
     */
    fun renderCharacter(
        char: Char,
        col: Int,
        row: Int,
        style: CharacterStyle,
        isDoubleWidth: Boolean = false
    )

    /**
     * Render a range of characters with the same style (optimization)
     */
    fun renderTextRun(
        text: String,
        startCol: Int,
        row: Int,
        style: CharacterStyle
    )

    /**
     * Clear a rectangular region
     */
    fun clearRegion(
        startCol: Int,
        startRow: Int,
        endCol: Int,
        endRow: Int,
        backgroundColor: Color
    )

    /**
     * Prepare for a new frame
     */
    fun beginFrame()

    /**
     * Complete the current frame
     */
    fun endFrame()
}

/**
 * Interface for cursor rendering
 */
interface CursorRenderer {
    enum class CursorStyle {
        BLOCK,
        UNDERLINE,
        VERTICAL_BAR
    }

    data class CursorState(
        val col: Int,
        val row: Int,
        val style: CursorStyle,
        val color: Color,
        val isVisible: Boolean,
        val isBlinking: Boolean
    )

    /**
     * Render the cursor at the specified state
     */
    fun renderCursor(state: CursorState, cellDimensions: TerminalRenderer.CellDimensions)
}

/**
 * Interface for selection overlay rendering
 */
interface SelectionRenderer {
    data class Selection(
        val startCol: Int,
        val startRow: Int,
        val endCol: Int,
        val endRow: Int
    )

    /**
     * Render selection overlay
     */
    fun renderSelection(selection: Selection, selectionColor: Color, cellDimensions: TerminalRenderer.CellDimensions)
}
