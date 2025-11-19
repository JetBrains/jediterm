package org.jetbrains.jediterm.compose.scrollbar

import androidx.compose.foundation.ScrollbarAdapter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import kotlin.math.max

/**
 * Custom ScrollbarAdapter that bridges terminal's scroll state with Compose's scrollbar component.
 *
 * Coordinate System Mapping:
 * - Terminal scrollOffset: 0 = at bottom (current screen), positive = scrolled up into history
 * - Scrollbar position: 0 = at top (maximum scroll), maxScrollOffset = at bottom (no scroll)
 *
 * The adapter converts between these two coordinate systems and calculates thumb size
 * based on the ratio of visible lines to total lines (screen + history).
 * All values are converted to pixels for proper scrollbar thumb sizing.
 *
 * @param terminalScrollOffset Current scroll offset in terminal (0 = bottom, positive = scrolled up)
 * @param historySize Number of lines in scrollback buffer
 * @param screenHeight Number of visible rows in terminal
 * @param cellHeight Height of a single character cell in pixels
 * @param onScroll Callback to update scroll offset when user drags scrollbar
 */
@Composable
fun rememberTerminalScrollbarAdapter(
    terminalScrollOffset: State<Int>,
    historySize: () -> Int,
    screenHeight: () -> Int,
    cellHeight: () -> Float,
    onScroll: (Int) -> Unit
): ScrollbarAdapter {
    return remember(terminalScrollOffset, historySize, screenHeight, cellHeight, onScroll) {
        TerminalScrollbarAdapter(
            terminalScrollOffset = terminalScrollOffset,
            historySize = historySize,
            screenHeight = screenHeight,
            cellHeight = cellHeight,
            onScroll = onScroll
        )
    }
}

/**
 * Internal implementation of ScrollbarAdapter for terminal scrolling.
 * Converts line-based terminal coordinates to pixel-based scrollbar coordinates.
 */
private class TerminalScrollbarAdapter(
    private val terminalScrollOffset: State<Int>,
    private val historySize: () -> Int,
    private val screenHeight: () -> Int,
    private val cellHeight: () -> Float,
    private val onScroll: (Int) -> Unit
) : ScrollbarAdapter {

    // Store the last container size for proportional scaling calculations
    private var lastContainerSize: Int = 1000  // Default fallback

    /**
     * Current scroll position in pixels (0.0 = top/max scroll, maxScrollOffset = bottom/no scroll).
     *
     * Converts terminal's scrollOffset (in lines) to scrollbar position (in pixels):
     * - terminalScrollOffset = 0 (at bottom) → scrollOffset = maxScrollOffset
     * - terminalScrollOffset = historySize (at top) → scrollOffset = 0.0
     *
     * When maxScrollOffset is capped for minimum thumb size, this proportionally scales
     * the scroll position to match the capped range.
     */
    override val scrollOffset: Float
        get() {
            val offset = terminalScrollOffset.value  // in lines
            val history = max(0, historySize())      // in lines

            if (history == 0) {
                // No scrollback history, always at bottom
                return 0f
            }

            val cellH = cellHeight()

            // Calculate raw scroll offset in pixels
            val rawScrollOffset = ((history - offset) * cellH)

            // Get the capped maxScrollOffset using stored container size
            val minThumbRatio = 0.05f
            val maxAllowedScroll = lastContainerSize * ((1 / minThumbRatio) - 1)
            val rawMaxScroll = history * cellH
            val cappedMaxScroll = minOf(rawMaxScroll, maxAllowedScroll)

            // Scale the scroll offset proportionally if capping was applied
            val result = if (rawMaxScroll > cappedMaxScroll) {
                // Capping is active, scale proportionally
                (rawScrollOffset / rawMaxScroll) * cappedMaxScroll
            } else {
                // No capping needed
                rawScrollOffset
            }

            return result
        }

    /**
     * Maximum scroll offset in pixels (equals history size in lines × cell height).
     * This represents the scrollbar position when at the bottom of the content.
     *
     * Enforces minimum thumb size by capping maxScrollOffset to ensure thumb is at least 5% of container.
     * Thumb size = containerSize / (maxScrollOffset + containerSize)
     * For 5% minimum: containerSize / (maxScrollOffset + containerSize) >= 0.05
     * Solving: maxScrollOffset <= 19 * containerSize
     */
    override fun maxScrollOffset(containerSize: Int): Float {
        // Store container size for use in scrollOffset calculations
        lastContainerSize = containerSize

        val history = max(0, historySize())  // in lines
        val cellH = cellHeight()
        val rawMaxScroll = (history * cellH)

        // Enforce minimum thumb size of 5% by capping maxScrollOffset
        val minThumbRatio = 0.05f  // 5% minimum thumb size
        val maxAllowedScroll = containerSize * ((1 / minThumbRatio) - 1)
        val result = minOf(rawMaxScroll, maxAllowedScroll)

        return result
    }

    /**
     * Update scroll offset when user drags scrollbar thumb.
     *
     * @param containerSize The size of the scrollbar container in pixels
     * @param scrollOffset New scroll position from scrollbar in pixels (0.0 = top, maxScrollOffset = bottom)
     */
    override suspend fun scrollTo(containerSize: Int, scrollOffset: Float) {
        val history = max(0, historySize())  // in lines

        if (history == 0) {
            onScroll(0)
            return
        }

        val cellH = cellHeight()
        // Guard against division by zero (can happen during initialization or window minimize)
        if (cellH <= 0f) {
            onScroll(0)
            return
        }
        val rawMaxScroll = history * cellH  // Raw maximum scroll in pixels

        // Get the capped maxScrollOffset
        val minThumbRatio = 0.05f
        val maxAllowedScroll = containerSize * ((1 / minThumbRatio) - 1)
        val cappedMaxScroll = minOf(rawMaxScroll, maxAllowedScroll)

        // If capping was applied, we need to scale the scroll offset back to the raw range
        val actualScrollOffset = if (rawMaxScroll > cappedMaxScroll) {
            // Capping is active, scale back to raw range
            (scrollOffset / cappedMaxScroll) * rawMaxScroll
        } else {
            // No capping needed
            scrollOffset
        }

        // Convert pixels to lines:
        // actualScrollOffset is distance from top in pixels
        // Divide by cellHeight to get distance from top in lines
        val linesFromTop = (actualScrollOffset / cellH).toInt()

        // terminalOffset is distance FROM BOTTOM, so invert:
        // When actualScrollOffset = 0 (top) → terminalOffset = history (max scroll up)
        // When actualScrollOffset = rawMaxScroll (bottom) → terminalOffset = 0 (no scroll)
        val newTerminalOffset = history - linesFromTop

        // Constrain to valid range
        val constrainedOffset = newTerminalOffset.coerceIn(0, history)
        onScroll(constrainedOffset)
    }
}
