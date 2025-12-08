package ai.rever.bossterm.compose.settings.theme

/**
 * Built-in terminal color themes.
 *
 * Each theme provides:
 * - Terminal colors (foreground, background, cursor, selection, etc.)
 * - Full 16-color ANSI palette
 */
object BuiltinThemes {

    /**
     * Default XTerm-style dark theme.
     */
    val DEFAULT = Theme(
        id = "default",
        name = "Default",
        foreground = "0xFFFFFFFF",
        background = "0xFF000000",
        cursor = "0xFFFFFFFF",
        cursorText = "0xFF000000",
        selection = "0xFF4A90E2",
        selectionText = "0xFFFFFFFF",
        searchMatch = "0xFFFFFF00",
        hyperlink = "0xFF5C9FFF",
        // Standard ANSI colors
        black = "0xFF000000",
        red = "0xFFCD0000",
        green = "0xFF00CD00",
        yellow = "0xFFCDCD00",
        blue = "0xFF0000EE",
        magenta = "0xFFCD00CD",
        cyan = "0xFF00CDCD",
        white = "0xFFE5E5E5",
        brightBlack = "0xFF7F7F7F",
        brightRed = "0xFFFF0000",
        brightGreen = "0xFF00FF00",
        brightYellow = "0xFFFFFF00",
        brightBlue = "0xFF5C5CFF",
        brightMagenta = "0xFFFF00FF",
        brightCyan = "0xFF00FFFF",
        brightWhite = "0xFFFFFFFF",
        isBuiltin = true
    )

    /**
     * Dracula theme - Popular dark theme with purple accents.
     * https://draculatheme.com
     */
    val DRACULA = Theme(
        id = "dracula",
        name = "Dracula",
        foreground = "0xFFF8F8F2",
        background = "0xFF282A36",
        cursor = "0xFFF8F8F2",
        cursorText = "0xFF282A36",
        selection = "0xFF44475A",
        selectionText = "0xFFF8F8F2",
        searchMatch = "0xFFF1FA8C",
        hyperlink = "0xFF8BE9FD",
        black = "0xFF21222C",
        red = "0xFFFF5555",
        green = "0xFF50FA7B",
        yellow = "0xFFF1FA8C",
        blue = "0xFFBD93F9",
        magenta = "0xFFFF79C6",
        cyan = "0xFF8BE9FD",
        white = "0xFFF8F8F2",
        brightBlack = "0xFF6272A4",
        brightRed = "0xFFFF6E6E",
        brightGreen = "0xFF69FF94",
        brightYellow = "0xFFFFFFA5",
        brightBlue = "0xFFD6ACFF",
        brightMagenta = "0xFFFF92DF",
        brightCyan = "0xFFA4FFFF",
        brightWhite = "0xFFFFFFFF",
        isBuiltin = true
    )

    /**
     * Nord theme - Arctic blue aesthetic.
     * https://www.nordtheme.com
     */
    val NORD = Theme(
        id = "nord",
        name = "Nord",
        foreground = "0xFFD8DEE9",
        background = "0xFF2E3440",
        cursor = "0xFFD8DEE9",
        cursorText = "0xFF2E3440",
        selection = "0xFF434C5E",
        selectionText = "0xFFD8DEE9",
        searchMatch = "0xFFEBCB8B",
        hyperlink = "0xFF88C0D0",
        black = "0xFF3B4252",
        red = "0xFFBF616A",
        green = "0xFFA3BE8C",
        yellow = "0xFFEBCB8B",
        blue = "0xFF81A1C1",
        magenta = "0xFFB48EAD",
        cyan = "0xFF88C0D0",
        white = "0xFFE5E9F0",
        brightBlack = "0xFF4C566A",
        brightRed = "0xFFBF616A",
        brightGreen = "0xFFA3BE8C",
        brightYellow = "0xFFEBCB8B",
        brightBlue = "0xFF81A1C1",
        brightMagenta = "0xFFB48EAD",
        brightCyan = "0xFF8FBCBB",
        brightWhite = "0xFFECEFF4",
        isBuiltin = true
    )

    /**
     * Solarized Dark theme - Ethan Schoonover's classic.
     * https://ethanschoonover.com/solarized
     */
    val SOLARIZED_DARK = Theme(
        id = "solarized-dark",
        name = "Solarized Dark",
        foreground = "0xFF839496",
        background = "0xFF002B36",
        cursor = "0xFF839496",
        cursorText = "0xFF002B36",
        selection = "0xFF073642",
        selectionText = "0xFF93A1A1",
        searchMatch = "0xFFB58900",
        hyperlink = "0xFF268BD2",
        black = "0xFF073642",
        red = "0xFFDC322F",
        green = "0xFF859900",
        yellow = "0xFFB58900",
        blue = "0xFF268BD2",
        magenta = "0xFFD33682",
        cyan = "0xFF2AA198",
        white = "0xFFEEE8D5",
        brightBlack = "0xFF002B36",
        brightRed = "0xFFCB4B16",
        brightGreen = "0xFF586E75",
        brightYellow = "0xFF657B83",
        brightBlue = "0xFF839496",
        brightMagenta = "0xFF6C71C4",
        brightCyan = "0xFF93A1A1",
        brightWhite = "0xFFFDF6E3",
        isBuiltin = true
    )

    /**
     * Solarized Light theme.
     */
    val SOLARIZED_LIGHT = Theme(
        id = "solarized-light",
        name = "Solarized Light",
        foreground = "0xFF657B83",
        background = "0xFFFDF6E3",
        cursor = "0xFF657B83",
        cursorText = "0xFFFDF6E3",
        selection = "0xFFEEE8D5",
        selectionText = "0xFF586E75",
        searchMatch = "0xFFB58900",
        hyperlink = "0xFF268BD2",
        black = "0xFFEEE8D5",
        red = "0xFFDC322F",
        green = "0xFF859900",
        yellow = "0xFFB58900",
        blue = "0xFF268BD2",
        magenta = "0xFFD33682",
        cyan = "0xFF2AA198",
        white = "0xFF073642",
        brightBlack = "0xFFFDF6E3",
        brightRed = "0xFFCB4B16",
        brightGreen = "0xFF93A1A1",
        brightYellow = "0xFF839496",
        brightBlue = "0xFF657B83",
        brightMagenta = "0xFF6C71C4",
        brightCyan = "0xFF586E75",
        brightWhite = "0xFF002B36",
        isBuiltin = true
    )

    /**
     * Gruvbox Dark theme - Retro warm colors.
     * https://github.com/morhetz/gruvbox
     */
    val GRUVBOX_DARK = Theme(
        id = "gruvbox-dark",
        name = "Gruvbox Dark",
        foreground = "0xFFEBDBB2",
        background = "0xFF282828",
        cursor = "0xFFEBDBB2",
        cursorText = "0xFF282828",
        selection = "0xFF504945",
        selectionText = "0xFFEBDBB2",
        searchMatch = "0xFFFABD2F",
        hyperlink = "0xFF83A598",
        black = "0xFF282828",
        red = "0xFFCC241D",
        green = "0xFF98971A",
        yellow = "0xFFD79921",
        blue = "0xFF458588",
        magenta = "0xFFB16286",
        cyan = "0xFF689D6A",
        white = "0xFFA89984",
        brightBlack = "0xFF928374",
        brightRed = "0xFFFB4934",
        brightGreen = "0xFFB8BB26",
        brightYellow = "0xFFFABD2F",
        brightBlue = "0xFF83A598",
        brightMagenta = "0xFFD3869B",
        brightCyan = "0xFF8EC07C",
        brightWhite = "0xFFEBDBB2",
        isBuiltin = true
    )

    /**
     * One Dark theme - Atom's default dark theme.
     * https://github.com/atom/atom/tree/master/packages/one-dark-syntax
     */
    val ONE_DARK = Theme(
        id = "one-dark",
        name = "One Dark",
        foreground = "0xFFABB2BF",
        background = "0xFF282C34",
        cursor = "0xFF528BFF",
        cursorText = "0xFF282C34",
        selection = "0xFF3E4451",
        selectionText = "0xFFABB2BF",
        searchMatch = "0xFFE5C07B",
        hyperlink = "0xFF61AFEF",
        black = "0xFF282C34",
        red = "0xFFE06C75",
        green = "0xFF98C379",
        yellow = "0xFFE5C07B",
        blue = "0xFF61AFEF",
        magenta = "0xFFC678DD",
        cyan = "0xFF56B6C2",
        white = "0xFFABB2BF",
        brightBlack = "0xFF5C6370",
        brightRed = "0xFFE06C75",
        brightGreen = "0xFF98C379",
        brightYellow = "0xFFE5C07B",
        brightBlue = "0xFF61AFEF",
        brightMagenta = "0xFFC678DD",
        brightCyan = "0xFF56B6C2",
        brightWhite = "0xFFFFFFFF",
        isBuiltin = true
    )

    /**
     * Monokai theme - Sublime Text classic.
     */
    val MONOKAI = Theme(
        id = "monokai",
        name = "Monokai",
        foreground = "0xFFF8F8F2",
        background = "0xFF272822",
        cursor = "0xFFF8F8F2",
        cursorText = "0xFF272822",
        selection = "0xFF49483E",
        selectionText = "0xFFF8F8F2",
        searchMatch = "0xFFE6DB74",
        hyperlink = "0xFF66D9EF",
        black = "0xFF272822",
        red = "0xFFF92672",
        green = "0xFFA6E22E",
        yellow = "0xFFF4BF75",
        blue = "0xFF66D9EF",
        magenta = "0xFFAE81FF",
        cyan = "0xFFA1EFE4",
        white = "0xFFF8F8F2",
        brightBlack = "0xFF75715E",
        brightRed = "0xFFF92672",
        brightGreen = "0xFFA6E22E",
        brightYellow = "0xFFF4BF75",
        brightBlue = "0xFF66D9EF",
        brightMagenta = "0xFFAE81FF",
        brightCyan = "0xFFA1EFE4",
        brightWhite = "0xFFF9F8F5",
        isBuiltin = true
    )

    /**
     * Tokyo Night theme - Modern Japanese aesthetic.
     * https://github.com/enkia/tokyo-night-vscode-theme
     */
    val TOKYO_NIGHT = Theme(
        id = "tokyo-night",
        name = "Tokyo Night",
        foreground = "0xFFA9B1D6",
        background = "0xFF1A1B26",
        cursor = "0xFFC0CAF5",
        cursorText = "0xFF1A1B26",
        selection = "0xFF33467C",
        selectionText = "0xFFA9B1D6",
        searchMatch = "0xFFE0AF68",
        hyperlink = "0xFF7AA2F7",
        black = "0xFF15161E",
        red = "0xFFF7768E",
        green = "0xFF9ECE6A",
        yellow = "0xFFE0AF68",
        blue = "0xFF7AA2F7",
        magenta = "0xFFBB9AF7",
        cyan = "0xFF7DCFFF",
        white = "0xFFA9B1D6",
        brightBlack = "0xFF414868",
        brightRed = "0xFFF7768E",
        brightGreen = "0xFF9ECE6A",
        brightYellow = "0xFFE0AF68",
        brightBlue = "0xFF7AA2F7",
        brightMagenta = "0xFFBB9AF7",
        brightCyan = "0xFF7DCFFF",
        brightWhite = "0xFFC0CAF5",
        isBuiltin = true
    )

    /**
     * Catppuccin Mocha theme - Soothing pastel colors.
     * https://github.com/catppuccin/catppuccin
     */
    val CATPPUCCIN_MOCHA = Theme(
        id = "catppuccin-mocha",
        name = "Catppuccin Mocha",
        foreground = "0xFFCDD6F4",
        background = "0xFF1E1E2E",
        cursor = "0xFFF5E0DC",
        cursorText = "0xFF1E1E2E",
        selection = "0xFF45475A",
        selectionText = "0xFFCDD6F4",
        searchMatch = "0xFFF9E2AF",
        hyperlink = "0xFF89B4FA",
        black = "0xFF45475A",
        red = "0xFFF38BA8",
        green = "0xFFA6E3A1",
        yellow = "0xFFF9E2AF",
        blue = "0xFF89B4FA",
        magenta = "0xFFF5C2E7",
        cyan = "0xFF94E2D5",
        white = "0xFFBAC2DE",
        brightBlack = "0xFF585B70",
        brightRed = "0xFFF38BA8",
        brightGreen = "0xFFA6E3A1",
        brightYellow = "0xFFF9E2AF",
        brightBlue = "0xFF89B4FA",
        brightMagenta = "0xFFF5C2E7",
        brightCyan = "0xFF94E2D5",
        brightWhite = "0xFFA6ADC8",
        isBuiltin = true
    )

    /**
     * All built-in themes.
     */
    val ALL = listOf(
        DEFAULT,
        DRACULA,
        NORD,
        SOLARIZED_DARK,
        SOLARIZED_LIGHT,
        GRUVBOX_DARK,
        ONE_DARK,
        MONOKAI,
        TOKYO_NIGHT,
        CATPPUCCIN_MOCHA
    )

    /**
     * Get a built-in theme by ID.
     */
    fun getById(id: String): Theme? = ALL.find { it.id == id }

    /**
     * Default theme ID.
     */
    const val DEFAULT_THEME_ID = "default"
}
