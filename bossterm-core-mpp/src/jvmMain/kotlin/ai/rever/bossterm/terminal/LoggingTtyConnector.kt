package ai.rever.bossterm.terminal

/**
 * Interface for terminal connectors that support logging of I/O data.
 *
 * This interface allows querying captured chunks of terminal data and
 * periodic state snapshots, useful for debugging terminal issues.
 *
 * Implementations typically wrap another TtyConnector and intercept
 * all read/write operations to record them.
 *
 * @see TtyConnector
 */
interface LoggingTtyConnector {
    /**
     * Get all captured data chunks.
     *
     * Each chunk represents a single read operation from the PTY,
     * preserving the exact byte boundaries as received.
     *
     * @return List of character arrays in chronological order
     */
    fun getChunks(): List<CharArray>

    /**
     * Get all captured terminal state snapshots.
     *
     * Snapshots are taken periodically (e.g., every 100ms) and capture
     * the complete terminal state at that moment for time-travel debugging.
     *
     * @return List of terminal states in chronological order
     */
    fun getStates(): List<TerminalState>

    /**
     * Get the starting index of the log buffer.
     *
     * When the buffer wraps (circular buffer), this indicates which
     * chunk index was the earliest still available.
     *
     * @return The index of the first chunk in the buffer
     */
    fun getLogStart(): Int

    /**
     * Represents a snapshot of terminal state at a specific point in time.
     *
     * This class captures the complete visual state of the terminal,
     * allowing reconstruction of what was displayed at any moment.
     */
    data class TerminalState(
        /**
         * Content of the visible screen buffer (newline-separated lines).
         */
        val screenLines: String,

        /**
         * Style attributes for each character position (encoded format).
         * Format varies by implementation but typically includes color,
         * bold, italic, underline, etc.
         */
        val styleLines: String,

        /**
         * Content of the scrollback history buffer (newline-separated lines).
         */
        val historyLines: String,

        /**
         * Timestamp when this snapshot was captured (milliseconds since epoch).
         */
        val timestamp: Long = System.currentTimeMillis(),

        /**
         * Cursor X position (column, 0-based).
         */
        val cursorX: Int = 0,

        /**
         * Cursor Y position (row, 0-based).
         */
        val cursorY: Int = 0,

        /**
         * Whether the alternate screen buffer was active.
         */
        val alternateBufferActive: Boolean = false
    )
}
