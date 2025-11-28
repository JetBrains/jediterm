package com.jediterm.terminal.util

/**
 * Metadata tracking grapheme cluster boundaries within a string.
 * Uses sparse storage - only tracks boundaries for multi-code-point graphemes.
 *
 * For simple ASCII-only strings, no metadata is needed.
 * For strings with complex graphemes (emoji, surrogates, combining chars), this tracks:
 * - Where each grapheme starts and ends
 * - Visual width of each grapheme
 *
 * This enables efficient random access without re-scanning the entire string.
 */
class GraphemeMetadata private constructor(
    private val boundaries: IntArray,  // Sorted array of boundary positions
    private val widths: IntArray       // Visual widths for each grapheme
) {
    /**
     * Number of grapheme clusters in the string.
     */
    val boundaryCount: Int
        get() = boundaries.size

    /**
     * Gets the grapheme boundary at the given visual column.
     * Returns a pair of (startIndex, endIndex) in the string.
     *
     * @param visualColumn The visual column (0-based)
     * @return Pair of string indices, or null if column is out of bounds
     */
    fun findBoundaryAtColumn(visualColumn: Int): Pair<Int, Int>? {
        if (boundaries.isEmpty()) return null

        var currentColumn = 0
        for (i in boundaries.indices) {
            if (currentColumn == visualColumn) {
                val start = boundaries[i]
                val end = if (i + 1 < boundaries.size) boundaries[i + 1] else -1
                return Pair(start, end)
            }
            currentColumn += widths[i]
        }
        return null
    }

    /**
     * Gets all grapheme boundaries as a list of start positions.
     */
    fun getGraphemeBoundaries(): List<Int> {
        return boundaries.toList()
    }

    /**
     * Finds the boundary at or before the given position.
     * Used for cursor positioning and selection.
     *
     * @param position String index
     * @return Start of the grapheme containing this position
     */
    fun findBoundaryAtOrBefore(position: Int): Int {
        if (boundaries.isEmpty()) return 0

        // Binary search for the boundary at or before position
        var left = 0
        var right = boundaries.size - 1
        var result = 0

        while (left <= right) {
            val mid = (left + right) / 2
            if (boundaries[mid] <= position) {
                result = boundaries[mid]
                left = mid + 1
            } else {
                right = mid - 1
            }
        }

        return result
    }

    /**
     * Finds the boundary at or after the given position.
     * Used for cursor positioning and selection.
     *
     * @param position String index
     * @return Start of the grapheme containing or following this position
     */
    fun findBoundaryAtOrAfter(position: Int): Int {
        if (boundaries.isEmpty()) return position

        // Binary search for the boundary at or after position
        var left = 0
        var right = boundaries.size - 1

        while (left <= right) {
            val mid = (left + right) / 2
            if (boundaries[mid] < position) {
                left = mid + 1
            } else {
                return boundaries[mid]
            }
        }

        // Position is beyond last boundary
        return if (boundaries.isNotEmpty()) boundaries.last() else position
    }

    /**
     * Gets the visual width at the given visual column.
     */
    fun getWidthAtColumn(visualColumn: Int): Int {
        if (visualColumn < 0 || visualColumn >= widths.size) return 1
        return widths[visualColumn]
    }

    companion object {
        /**
         * Analyzes a string and creates metadata for its grapheme clusters.
         *
         * @param text The string to analyze
         * @return GraphemeMetadata, or null if the string contains only simple characters
         */
        fun analyze(text: String): GraphemeMetadata? {
            if (text.isEmpty()) return null

            // Check if the string needs complex grapheme analysis
            var needsMetadata = false
            for (i in text.indices) {
                val c = text[i]
                // Check for surrogates, emoji, combining characters
                if (c.isHighSurrogate() || c.isLowSurrogate() ||
                    c.code in 0x0300..0x036F || // Combining diacritics
                    c.code == 0x200D || // ZWJ
                    c.code == 0xFE0E || c.code == 0xFE0F // Variation selectors
                ) {
                    needsMetadata = true
                    break
                }
            }

            if (!needsMetadata) return null

            // Segment into graphemes and track boundaries
            val graphemes = GraphemeUtils.segmentIntoGraphemes(text)
            if (graphemes.isEmpty()) return null

            val boundariesList = mutableListOf<Int>()
            val widthsList = mutableListOf<Int>()
            var position = 0

            for (grapheme in graphemes) {
                boundariesList.add(position)
                widthsList.add(grapheme.visualWidth)
                position += grapheme.text.length
            }

            return GraphemeMetadata(
                boundaries = boundariesList.toIntArray(),
                widths = widthsList.toIntArray()
            )
        }

        /**
         * Creates metadata from pre-computed grapheme clusters.
         *
         * @param graphemes List of grapheme clusters
         * @return GraphemeMetadata
         */
        fun fromGraphemes(graphemes: List<GraphemeCluster>): GraphemeMetadata? {
            if (graphemes.isEmpty()) return null

            val boundariesList = mutableListOf<Int>()
            val widthsList = mutableListOf<Int>()
            var position = 0

            for (grapheme in graphemes) {
                boundariesList.add(position)
                widthsList.add(grapheme.visualWidth)
                position += grapheme.text.length
            }

            return GraphemeMetadata(
                boundaries = boundariesList.toIntArray(),
                widths = widthsList.toIntArray()
            )
        }
    }
}
