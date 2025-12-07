package ai.rever.bossterm.compose.scrollbar

import androidx.compose.foundation.v2.ScrollbarAdapter
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
 * Internal implementation of ScrollbarAdapter (v2) for terminal scrolling.
 * Converts line-based terminal coordinates to pixel-based scrollbar coordinates.
 *
 * V2 API differences:
 * - Uses Double instead of Float for precision
 * - contentSize and viewportSize are properties instead of method parameters
 * - scrollTo only takes scrollOffset (no containerSize parameter)
 */
private class TerminalScrollbarAdapter(
    private val terminalScrollOffset: State<Int>,
    private val historySize: () -> Int,
    private val screenHeight: () -> Int,
    private val cellHeight: () -> Float,
    private val onScroll: (Int) -> Unit
) : ScrollbarAdapter {

    /**
     * Total content size in pixels (history + screen height).
     * This represents the full scrollable content area.
     */
    override val contentSize: Double
        get() {
            val history = max(0, historySize())
            val screen = max(1, screenHeight())
            val cellH = cellHeight().toDouble()
            return (history + screen) * cellH
        }

    /**
     * Viewport size in pixels (visible screen area).
     * This is the height of the terminal's visible area.
     */
    override val viewportSize: Double
        get() {
            val screen = max(1, screenHeight())
            val cellH = cellHeight().toDouble()
            return screen * cellH
        }

    /**
     * Current scroll position in pixels (0.0 = top/max scroll, maxScrollOffset = bottom/no scroll).
     *
     * Converts terminal's scrollOffset (in lines) to scrollbar position (in pixels):
     * - terminalScrollOffset = historySize (at top) → scrollOffset = 0.0
     * - terminalScrollOffset = 0 (at bottom) → scrollOffset = maxScrollOffset
     *
     * maxScrollOffset = contentSize - viewportSize = history * cellHeight
     */
    override val scrollOffset: Double
        get() {
            val offset = terminalScrollOffset.value  // in lines
            val history = max(0, historySize())      // in lines

            if (history == 0) {
                // No scrollback history, always at bottom
                return 0.0
            }

            val cellH = cellHeight().toDouble()

            // Terminal offset is from bottom, scrollbar offset is from top
            // terminalScrollOffset = 0 means at bottom → scrollOffset = history * cellH (max)
            // terminalScrollOffset = history means at top → scrollOffset = 0
            return (history - offset) * cellH
        }

    /**
     * Update scroll offset when user drags scrollbar thumb.
     *
     * @param scrollOffset New scroll position from scrollbar in pixels (0.0 = top, maxScrollOffset = bottom)
     */
    override suspend fun scrollTo(scrollOffset: Double) {
        val history = max(0, historySize())  // in lines

        if (history == 0) {
            onScroll(0)
            return
        }

        val cellH = cellHeight().toDouble()
        // Guard against division by zero (can happen during initialization or window minimize)
        if (cellH <= 0.0) {
            onScroll(0)
            return
        }

        // Convert pixels to lines:
        // scrollOffset is distance from top in pixels
        // Divide by cellHeight to get distance from top in lines
        val linesFromTop = (scrollOffset / cellH).toInt()

        // terminalOffset is distance FROM BOTTOM, so invert:
        // When scrollOffset = 0 (top) → terminalOffset = history (max scroll up)
        // When scrollOffset = history * cellH (bottom) → terminalOffset = 0 (no scroll)
        val newTerminalOffset = history - linesFromTop

        // Constrain to valid range
        val constrainedOffset = newTerminalOffset.coerceIn(0, history)
        onScroll(constrainedOffset)
    }
}
