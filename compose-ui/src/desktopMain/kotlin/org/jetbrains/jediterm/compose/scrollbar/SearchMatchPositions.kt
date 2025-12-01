package org.jetbrains.jediterm.compose.scrollbar

/**
 * Utility for converting search match coordinates to normalized scrollbar positions.
 */

/**
 * Convert search match row coordinates to normalized scrollbar positions [0, 1].
 *
 * @param matches List of (column, row) pairs where row is buffer-relative
 *                (negative = history, 0+ = screen)
 * @param historyLinesCount Number of lines in history buffer
 * @param screenHeight Number of visible lines on screen
 * @return List of normalized positions [0, 1] corresponding to each match
 */
fun computeMatchPositions(
    matches: List<Pair<Int, Int>>,
    historyLinesCount: Int,
    screenHeight: Int
): List<Float> {
    if (matches.isEmpty()) return emptyList()

    val totalLines = historyLinesCount + screenHeight
    if (totalLines <= 0) return emptyList()

    return matches.map { (_, row) ->
        // Buffer-relative row: negative = history, 0+ = screen
        // Convert to absolute position from top of buffer
        // For row -N (history): absoluteRow = historyLinesCount + (-N) = historyLinesCount - N
        // For row +N (screen): absoluteRow = historyLinesCount + N
        val absoluteRow = historyLinesCount + row
        (absoluteRow.toFloat() / totalLines).coerceIn(0f, 1f)
    }
}
