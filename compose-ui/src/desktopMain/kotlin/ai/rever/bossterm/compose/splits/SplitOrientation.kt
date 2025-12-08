package ai.rever.bossterm.compose.splits

/**
 * Orientation for creating a new split.
 */
enum class SplitOrientation {
    /**
     * Split horizontally - creates top and bottom panes.
     * The divider is horizontal (runs left to right).
     */
    HORIZONTAL,

    /**
     * Split vertically - creates left and right panes.
     * The divider is vertical (runs top to bottom).
     */
    VERTICAL
}
