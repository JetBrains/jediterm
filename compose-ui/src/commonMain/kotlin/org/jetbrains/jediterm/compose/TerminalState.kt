package org.jetbrains.jediterm.compose

import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.flow.StateFlow

/**
 * Terminal state holder that manages the terminal's UI state.
 * This is the main state object exposed to Compose.
 */
@Stable
interface TerminalState {
    /**
     * Terminal theme configuration
     */
    data class TerminalTheme(
        val defaultForeground: Color = Color.White,
        val defaultBackground: Color = Color.Black,
        val cursorColor: Color = Color.White,
        val selectionBackground: Color = Color(0x80FFFFFF), // Semi-transparent white
        val colors: List<Color> = defaultAnsiColors()
    ) {
        companion object {
            fun defaultAnsiColors(): List<Color> = listOf(
                // Normal colors (0-7)
                Color(0xFF000000), // Black
                Color(0xFFCD0000), // Red
                Color(0xFF00CD00), // Green
                Color(0xFFCDCD00), // Yellow
                Color(0xFF0000EE), // Blue
                Color(0xFFCD00CD), // Magenta
                Color(0xFF00CDCD), // Cyan
                Color(0xFFE5E5E5), // White
                // Bright colors (8-15)
                Color(0xFF7F7F7F), // Bright Black
                Color(0xFFFF0000), // Bright Red
                Color(0xFF00FF00), // Bright Green
                Color(0xFFFFFF00), // Bright Yellow
                Color(0xFF5C5CFF), // Bright Blue
                Color(0xFFFF00FF), // Bright Magenta
                Color(0xFF00FFFF), // Bright Cyan
                Color(0xFFFFFFFF), // Bright White
            )

            /**
             * Dark theme
             */
            fun dark() = TerminalTheme(
                defaultForeground = Color(0xFFE0E0E0),
                defaultBackground = Color(0xFF1E1E1E),
                cursorColor = Color(0xFFE0E0E0),
                selectionBackground = Color(0x80444444)
            )

            /**
             * Light theme
             */
            fun light() = TerminalTheme(
                defaultForeground = Color(0xFF000000),
                defaultBackground = Color(0xFFFAFAFA),
                cursorColor = Color(0xFF000000),
                selectionBackground = Color(0x80CCCCCC)
            )
        }
    }

    /**
     * Terminal configuration
     */
    data class TerminalConfig(
        val columns: Int = 80,
        val rows: Int = 24,
        val scrollbackLines: Int = 10000,
        val cursorBlinkRate: Long = 500, // milliseconds
        val theme: TerminalTheme = TerminalTheme.dark()
    )

    /**
     * Current terminal configuration
     */
    val config: StateFlow<TerminalConfig>

    /**
     * Current theme
     */
    val theme: StateFlow<TerminalTheme>

    /**
     * Terminal dimensions (columns x rows)
     */
    val dimensions: StateFlow<Pair<Int, Int>>

    /**
     * Is terminal focused
     */
    val isFocused: StateFlow<Boolean>

    /**
     * Current scroll position (0 = bottom, positive = scrolled up)
     */
    val scrollPosition: StateFlow<Int>

    /**
     * Maximum scroll position
     */
    val maxScrollPosition: StateFlow<Int>

    /**
     * Has active selection
     */
    val hasSelection: StateFlow<Boolean>

    /**
     * Is terminal connected to a process
     */
    val isConnected: StateFlow<Boolean>

    /**
     * Terminal title (if set by application)
     */
    val title: StateFlow<String?>

    /**
     * Update configuration
     */
    fun updateConfig(config: TerminalConfig)

    /**
     * Update theme
     */
    fun updateTheme(theme: TerminalTheme)

    /**
     * Set terminal dimensions
     */
    fun setDimensions(columns: Int, rows: Int)

    /**
     * Set focus state
     */
    fun setFocused(focused: Boolean)

    /**
     * Scroll to position
     */
    fun scrollTo(position: Int)

    /**
     * Scroll by delta
     */
    fun scrollBy(delta: Int)

    /**
     * Scroll to bottom
     */
    fun scrollToBottom()
}

/**
 * Controller for terminal operations
 */
interface TerminalController {
    /**
     * Get the terminal state
     */
    val state: TerminalState

    /**
     * Connect to a process
     */
    suspend fun connect(command: String, arguments: List<String> = emptyList(), environment: Map<String, String> = emptyMap())

    /**
     * Disconnect from current process
     */
    suspend fun disconnect()

    /**
     * Send text to terminal input
     */
    suspend fun sendText(text: String)

    /**
     * Send raw bytes to terminal input
     */
    suspend fun sendBytes(bytes: ByteArray)

    /**
     * Copy selected text to clipboard
     */
    suspend fun copySelection(): String?

    /**
     * Paste text from clipboard
     */
    suspend fun paste()

    /**
     * Select all text
     */
    fun selectAll()

    /**
     * Clear selection
     */
    fun clearSelection()

    /**
     * Clear terminal screen
     */
    fun clearScreen()

    /**
     * Reset terminal
     */
    fun reset()

    /**
     * Dispose resources
     */
    fun dispose()
}

/**
 * Create a terminal state instance
 */
expect fun rememberTerminalState(config: TerminalState.TerminalConfig = TerminalState.TerminalConfig()): TerminalState

/**
 * Create a terminal controller instance
 */
expect fun rememberTerminalController(state: TerminalState): TerminalController
