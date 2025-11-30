package org.jetbrains.jediterm.compose.settings

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/**
 * Terminal settings data class with all user-configurable options.
 * Based on legacy SettingsProvider interface from ui module.
 */
@Serializable
data class TerminalSettings(
    // ===== Visual Settings =====

    /**
     * Font size in SP (scalable pixels)
     */
    val fontSize: Float = 14f,

    /**
     * Line spacing multiplier (1.0 = normal, 1.5 = 1.5x spacing)
     */
    val lineSpacing: Float = 1.0f,

    /**
     * Use antialiasing for text rendering
     */
    val useAntialiasing: Boolean = true,

    /**
     * Default foreground color (serialized as ARGB hex)
     */
    val defaultForeground: String = "0xFFFFFFFF",

    /**
     * Default background color (serialized as ARGB hex)
     */
    val defaultBackground: String = "0xFF000000",

    /**
     * Selection highlight color (serialized as ARGB hex)
     */
    val selectionColor: String = "0xFF4A90E2",

    /**
     * Search result highlight color (serialized as ARGB hex)
     */
    val foundPatternColor: String = "0xFFFFFF00",

    /**
     * Hyperlink color (serialized as ARGB hex)
     */
    val hyperlinkColor: String = "0xFF5C9FFF",

    // ===== Behavior Settings =====

    /**
     * Automatically copy selected text to clipboard
     */
    val copyOnSelect: Boolean = false,

    /**
     * Paste clipboard on middle mouse button click
     */
    val pasteOnMiddleClick: Boolean = true,

    /**
     * Emulate X11-style separate selection clipboard
     */
    val emulateX11CopyPaste: Boolean = false,

    /**
     * Scroll to bottom when typing
     */
    val scrollToBottomOnTyping: Boolean = true,

    /**
     * Alt key sends Escape prefix
     */
    val altSendsEscape: Boolean = true,

    /**
     * Enable mouse reporting to terminal application
     */
    val enableMouseReporting: Boolean = true,

    /**
     * Force actions even when mouse reporting is active
     */
    val forceActionOnMouseReporting: Boolean = false,

    /**
     * Mouse scroll sensitivity threshold (filters out tiny scroll events)
     * Higher values = less sensitive, lower values = more sensitive
     * Range: 0.0 (all events) to 2.0 (very insensitive)
     * Default: 0.5 works well for most trackpads and mice
     */
    val mouseScrollThreshold: Float = 0.5f,

    /**
     * Play audible bell sound
     */
    val audibleBell: Boolean = false,

    /**
     * Use inverse selection color (swap fg/bg)
     */
    val useInverseSelectionColor: Boolean = false,

    // ===== Scrollbar Settings =====

    /**
     * Show visual scrollbar on the right side
     */
    val showScrollbar: Boolean = true,

    /**
     * Always show scrollbar (vs. auto-hide on inactivity)
     */
    val scrollbarAlwaysVisible: Boolean = false,

    /**
     * Scrollbar width in pixels
     */
    val scrollbarWidth: Float = 12f,

    /**
     * Scrollbar track color (serialized as ARGB hex)
     */
    val scrollbarColor: String = "0x40FFFFFF",

    /**
     * Scrollbar thumb color (serialized as ARGB hex)
     */
    val scrollbarThumbColor: String = "0xFFAAAAAA",

    // ===== Performance Settings =====

    /**
     * Maximum refresh rate in FPS (0 = unlimited)
     */
    val maxRefreshRate: Int = 60,

    /**
     * Maximum lines in scrollback buffer
     */
    val bufferMaxLines: Int = 10000,

    /**
     * Cursor blink rate in milliseconds (0 = no blink)
     */
    val caretBlinkMs: Int = 500,

    /**
     * Slow text blink rate in milliseconds
     */
    val slowTextBlinkMs: Int = 1000,

    /**
     * Rapid text blink rate in milliseconds
     */
    val rapidTextBlinkMs: Int = 500,

    // ===== Terminal Emulation Settings =====

    /**
     * DEC compatibility mode
     */
    val decCompatibilityMode: Boolean = true,

    /**
     * Treat ambiguous-width characters as double-width
     */
    val ambiguousCharsAreDoubleWidth: Boolean = false,

    /**
     * Character encoding mode: "UTF-8" or "ISO-8859-1"
     * UTF-8: GR range (160-255) passes through for multi-byte sequences (default, safe)
     * ISO-8859-1: GR range maps through character sets (enables Latin-1 supplemental)
     *
     * Note: Auto-detected from locale (LANG/LC_ALL/LC_CTYPE) on PTY initialization.
     * Can be manually overridden via settings.
     */
    val characterEncoding: String = "UTF-8",

    /**
     * Simulate mouse scroll with arrow keys in alternate screen
     */
    val simulateMouseScrollInAlternateScreen: Boolean = true,

    // ===== Search Settings =====

    /**
     * Search is case-sensitive by default
     */
    val searchCaseSensitive: Boolean = false,

    /**
     * Enable regex search by default
     */
    val searchUseRegex: Boolean = false,

    // ===== Hyperlink Settings =====

    /**
     * Show hyperlink underline on hover
     */
    val hyperlinkUnderlineOnHover: Boolean = true,

    /**
     * Hyperlink click requires Ctrl/Cmd modifier
     */
    val hyperlinkRequireModifier: Boolean = true,

    // ===== Debug Settings =====

    /**
     * Enable debug mode (captures I/O for debug panel)
     */
    val debugModeEnabled: Boolean = false,

    /**
     * Maximum number of chunks to store in debug buffer (circular)
     */
    val debugMaxChunks: Int = 1000,

    /**
     * Maximum number of state snapshots to store
     */
    val debugMaxSnapshots: Int = 100,

    /**
     * Auto-capture terminal state snapshots (ms interval)
     */
    val debugCaptureInterval: Long = 100L,

    /**
     * Show chunk IDs in control sequence visualization
     */
    val debugShowChunkIds: Boolean = true,

    /**
     * Show invisible characters in debug view
     */
    val debugShowInvisibleChars: Boolean = true,

    /**
     * Wrap long lines in debug sequence view
     */
    val debugWrapLines: Boolean = true,

    /**
     * Color-code escape sequences in debug view
     */
    val debugColorCodeSequences: Boolean = true
) {
    // Non-serialized computed properties

    @Transient
    val defaultForegroundColor: Color = Color(defaultForeground.removePrefix("0x").toULong(16).toLong())

    @Transient
    val defaultBackgroundColor: Color = Color(defaultBackground.removePrefix("0x").toULong(16).toLong())

    @Transient
    val selectionColorValue: Color = Color(selectionColor.removePrefix("0x").toULong(16).toLong())

    @Transient
    val foundPatternColorValue: Color = Color(foundPatternColor.removePrefix("0x").toULong(16).toLong())

    @Transient
    val hyperlinkColorValue: Color = Color(hyperlinkColor.removePrefix("0x").toULong(16).toLong())

    @Transient
    val scrollbarColorValue: Color = Color(scrollbarColor.removePrefix("0x").toULong(16).toLong())

    @Transient
    val scrollbarThumbColorValue: Color = Color(scrollbarThumbColor.removePrefix("0x").toULong(16).toLong())

    companion object {
        /**
         * Default settings instance
         */
        val DEFAULT = TerminalSettings()

        /**
         * Convert Color to hex string for serialization
         */
        fun Color.toHexString(): String {
            val argb = (this.alpha * 255).toInt().shl(24) or
                       (this.red * 255).toInt().shl(16) or
                       (this.green * 255).toInt().shl(8) or
                       (this.blue * 255).toInt()
            return "0x${argb.toUInt().toString(16).uppercase().padStart(8, '0')}"
        }
    }
}
