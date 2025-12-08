package ai.rever.bossterm.compose.settings

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
     * Disable line spacing when in alternate screen buffer.
     * Full-screen apps (vim, htop, less) may look better without extra spacing.
     */
    val disableLineSpacingInAlternateBuffer: Boolean = false,

    /**
     * Fill character background color through line spacing area.
     * When true, background colors extend into line spacing.
     * When false, line spacing shows terminal background only.
     */
    val fillBackgroundInLineSpacing: Boolean = true,

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

    /**
     * Active theme ID.
     * References a theme from BuiltinThemes or a custom theme.
     * When a theme is applied, the color settings above are updated to match.
     */
    val activeThemeId: String = "default",

    /**
     * Active color palette ID.
     * When set to "use-theme", uses the theme's built-in ANSI colors.
     * Otherwise, references a palette from BuiltinColorPalettes or a custom palette.
     * This allows mixing themes (for terminal colors) with different ANSI palettes.
     */
    val colorPaletteId: String = "use-theme",

    /**
     * Terminal background opacity (0.0 = fully transparent, 1.0 = fully opaque).
     * When less than 1.0, the window becomes transparent and the desktop shows through.
     * Note: On macOS, this enables native transparency. On other platforms, results may vary.
     */
    val backgroundOpacity: Float = 1.0f,

    /**
     * Enable blur effect behind transparent terminal (macOS only).
     * Creates a frosted glass effect when backgroundOpacity < 1.0.
     * Has no effect when backgroundOpacity is 1.0.
     */
    val windowBlur: Boolean = false,

    /**
     * Blur radius for transparent mode (1-100).
     * Higher values create more blur. Only applies when windowBlur is enabled.
     */
    val blurRadius: Float = 30f,

    /**
     * Path to background image file (PNG, JPG).
     * When set, displays an image behind the terminal content.
     * Empty string means no background image.
     */
    val backgroundImagePath: String = "",

    /**
     * Background image opacity (0.0 = invisible, 1.0 = fully visible).
     * Controls how visible the background image is.
     */
    val backgroundImageOpacity: Float = 0.3f,

    /**
     * Use native window decorations (title bar with traffic lights).
     * When true: Native macOS title bar, proper fullscreen, but no transparency.
     * When false: Custom title bar, transparency works, but no true fullscreen.
     * Changing this requires app restart to take effect.
     */
    val useNativeTitleBar: Boolean = true,

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
    val audibleBell: Boolean = true,

    /**
     * Flash screen on bell (visual bell)
     */
    val visualBell: Boolean = true,

    // ===== Progress Bar Settings =====

    /**
     * Enable progress bar indicator (OSC 1337;SetProgress / OSC 9;4)
     */
    val progressBarEnabled: Boolean = true,

    /**
     * Progress bar position: "top" or "bottom" of terminal
     */
    val progressBarPosition: String = "bottom",

    /**
     * Progress bar height in dp (1-10)
     */
    val progressBarHeight: Float = 6f,

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
    val scrollbarWidth: Float = 10f,

    /**
     * Scrollbar track color (serialized as ARGB hex)
     */
    val scrollbarColor: String = "0x40FFFFFF",

    /**
     * Scrollbar thumb color (serialized as ARGB hex)
     */
    val scrollbarThumbColor: String = "0xFFAAAAAA",

    /**
     * Show search match markers in scrollbar
     */
    val showSearchMarkersInScrollbar: Boolean = true,

    /**
     * Search marker color for regular matches (serialized as ARGB hex)
     */
    val searchMarkerColor: String = "0xFFFFFF00",

    /**
     * Search marker color for current match (serialized as ARGB hex)
     */
    val currentSearchMarkerColor: String = "0xFFFF6600",

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
     * Master toggle to enable/disable all text blinking (accessibility feature)
     */
    val enableTextBlinking: Boolean = true,

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

    // ===== Type-Ahead Settings =====

    /**
     * Enable type-ahead prediction for reduced perceived latency on SSH connections.
     * Predictions are invisible on local terminals but latency statistics are collected.
     */
    val typeAheadEnabled: Boolean = true,

    /**
     * Latency threshold in nanoseconds to activate visible predictions.
     * Predictions become visible when median round-trip latency exceeds this threshold.
     * Default: 100ms (100_000_000 nanos)
     */
    val typeAheadLatencyThresholdNanos: Long = 100_000_000L,

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
    val debugColorCodeSequences: Boolean = true,

    // ===== File Logging Settings =====

    /**
     * Enable automatic file logging on terminal start.
     * When enabled, all terminal I/O is written to a log file.
     */
    val fileLoggingEnabled: Boolean = false,

    /**
     * Directory for log files. If empty, uses ~/.bossterm/logs/
     */
    val fileLoggingDirectory: String = "",

    /**
     * Log file name pattern. Supports placeholders:
     * {timestamp} - ISO 8601 timestamp
     * {tab} - Tab ID (first 8 chars)
     * {pid} - Process ID
     */
    val fileLoggingPattern: String = "bossterm_{timestamp}_{tab}.log",

    // ===== Notification Settings =====

    /**
     * Enable command completion notifications when window is not focused.
     * Requires shell integration (OSC 133) to detect command completion.
     */
    val notifyOnCommandComplete: Boolean = true,

    /**
     * Minimum command duration in seconds to trigger notification.
     * Commands finishing faster than this threshold won't trigger notifications.
     * Default: 5 seconds (similar to iTerm2)
     */
    val notifyMinDurationSeconds: Int = 5,

    /**
     * Include command exit code in notification.
     * When true, shows "Command finished (exit 0)" vs just "Command finished"
     */
    val notifyShowExitCode: Boolean = true,

    /**
     * Play notification sound.
     * When true, uses system default notification sound.
     */
    val notifyWithSound: Boolean = true,

    /**
     * Whether notification permission has been requested.
     * On first launch, a welcome notification is sent to trigger macOS permission dialog.
     */
    val notificationPermissionRequested: Boolean = false,

    // ===== Split Pane Settings =====

    /**
     * Default ratio for new splits (0.0 to 1.0).
     * 0.5 means equal 50/50 split, 0.6 means 60/40, etc.
     */
    val splitDefaultRatio: Float = 0.5f,

    /**
     * Minimum pane size when resizing (0.0 to 0.5).
     * Prevents panes from being resized too small.
     * Default: 0.1 (10% minimum)
     */
    val splitMinimumSize: Float = 0.1f,

    /**
     * Show border on focused pane when splits exist.
     * Helps identify which pane has keyboard focus.
     */
    val splitFocusBorderEnabled: Boolean = true,

    /**
     * Color of the focus border (serialized as ARGB hex).
     * Only visible when splitFocusBorderEnabled is true.
     */
    val splitFocusBorderColor: String = "0xFF4A90E2",

    /**
     * New split panes inherit working directory from parent.
     * When true, new splits start in the same directory as the focused pane.
     * When false, new splits start in the user's home directory.
     */
    val splitInheritWorkingDirectory: Boolean = true
) {
    // Non-serialized computed properties

    @Transient
    val defaultForegroundColor: Color = Color(defaultForeground.removePrefix("0x").toULong(16).toLong())

    @Transient
    val defaultBackgroundColor: Color = Color(defaultBackground.removePrefix("0x").toULong(16).toLong())

    /**
     * Background color with opacity applied.
     * Use this for terminal background when transparency is enabled.
     */
    @Transient
    val defaultBackgroundColorWithOpacity: Color = defaultBackgroundColor.copy(alpha = backgroundOpacity)

    /**
     * Whether transparency is enabled (opacity < 1.0).
     */
    @Transient
    val isTransparencyEnabled: Boolean = backgroundOpacity < 1.0f

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

    @Transient
    val searchMarkerColorValue: Color = Color(searchMarkerColor.removePrefix("0x").toULong(16).toLong())

    @Transient
    val currentSearchMarkerColorValue: Color = Color(currentSearchMarkerColor.removePrefix("0x").toULong(16).toLong())

    @Transient
    val splitFocusBorderColorValue: Color = Color(splitFocusBorderColor.removePrefix("0x").toULong(16).toLong())

    companion object {
        /**
         * Default settings instance
         */
        val DEFAULT = TerminalSettings()

        /**
         * Convert Color to hex string for serialization (0xAARRGGBB format)
         */
        fun colorToHex(color: Color): String {
            val argb = (color.alpha * 255).toInt().shl(24) or
                       (color.red * 255).toInt().shl(16) or
                       (color.green * 255).toInt().shl(8) or
                       (color.blue * 255).toInt()
            return "0x${argb.toUInt().toString(16).uppercase().padStart(8, '0')}"
        }
    }
}

/**
 * Extension function to convert Color to settings hex string (0xAARRGGBB format).
 * This is a top-level extension so it can be properly imported and used.
 */
fun Color.toSettingsHex(): String = TerminalSettings.colorToHex(this)
