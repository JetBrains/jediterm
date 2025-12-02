package org.jetbrains.jediterm.compose

/**
 * Selection mode for terminal text selection.
 *
 * @property NORMAL Standard line-based selection (default)
 *   - Selection flows from start point to end point
 *   - Full lines selected between first and last row
 *
 * @property BLOCK Rectangular/column selection (Alt+Drag)
 *   - Selection is a rectangle defined by start and end corners
 *   - Each row has the same column bounds
 *   - Useful for selecting columns from tabular output
 */
enum class SelectionMode {
    /**
     * Standard line-based selection (default).
     *
     * Selection flows from start point to end point:
     * - Single line: from start column to end column
     * - Multi-line: first row from start col to end, middle rows full, last row from 0 to end col
     */
    NORMAL,

    /**
     * Rectangular/column selection (Alt+Drag).
     *
     * Selection is a rectangle defined by start and end corners:
     * - Each row has the same column bounds (min col to max col)
     * - Does not flow across lines like normal selection
     * - Useful for selecting columns from tabular output
     */
    BLOCK
}
