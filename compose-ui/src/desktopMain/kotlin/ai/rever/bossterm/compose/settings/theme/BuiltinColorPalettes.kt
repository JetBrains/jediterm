package ai.rever.bossterm.compose.settings.theme

/**
 * Built-in color palettes that can be applied independently of themes.
 *
 * These palettes contain only the 16 ANSI colors and can be mixed with any theme.
 * Popular palettes from various terminal emulators and color schemes.
 */
object BuiltinColorPalettes {

    /**
     * Tango palette - GNOME Terminal default, warm and readable.
     */
    val TANGO = ColorPalette(
        id = "tango",
        name = "Tango",
        black = "0xFF2E3436",
        red = "0xFFCC0000",
        green = "0xFF4E9A06",
        yellow = "0xFFC4A000",
        blue = "0xFF3465A4",
        magenta = "0xFF75507B",
        cyan = "0xFF06989A",
        white = "0xFFD3D7CF",
        brightBlack = "0xFF555753",
        brightRed = "0xFFEF2929",
        brightGreen = "0xFF8AE234",
        brightYellow = "0xFFFCE94F",
        brightBlue = "0xFF729FCF",
        brightMagenta = "0xFFAD7FA8",
        brightCyan = "0xFF34E2E2",
        brightWhite = "0xFFEEEEEC",
        isBuiltin = true
    )

    /**
     * Pastel palette - Soft, easy on the eyes.
     */
    val PASTEL = ColorPalette(
        id = "pastel",
        name = "Pastel",
        black = "0xFF3F3F3F",
        red = "0xFFFF6B6B",
        green = "0xFF87D37C",
        yellow = "0xFFF9E79F",
        blue = "0xFF7FB3D5",
        magenta = "0xFFD7BDE2",
        cyan = "0xFF76D7C4",
        white = "0xFFECF0F1",
        brightBlack = "0xFF6C6C6C",
        brightRed = "0xFFFF8787",
        brightGreen = "0xFFA8E6CF",
        brightYellow = "0xFFFBE9B7",
        brightBlue = "0xFFA9CCE3",
        brightMagenta = "0xFFE8DAEF",
        brightCyan = "0xFFA3E4D7",
        brightWhite = "0xFFF8F9F9",
        isBuiltin = true
    )

    /**
     * Ubuntu palette - Classic Ubuntu terminal colors.
     */
    val UBUNTU = ColorPalette(
        id = "ubuntu",
        name = "Ubuntu",
        black = "0xFF2E3436",
        red = "0xFFCC0000",
        green = "0xFF4E9A06",
        yellow = "0xFFC4A000",
        blue = "0xFF3465A4",
        magenta = "0xFF75507B",
        cyan = "0xFF06989A",
        white = "0xFFD3D7CF",
        brightBlack = "0xFF555753",
        brightRed = "0xFFEF2929",
        brightGreen = "0xFF8AE234",
        brightYellow = "0xFFFCE94F",
        brightBlue = "0xFF729FCF",
        brightMagenta = "0xFFAD7FA8",
        brightCyan = "0xFF34E2E2",
        brightWhite = "0xFFEEEEEC",
        isBuiltin = true
    )

    /**
     * macOS Terminal palette - Apple's Terminal.app defaults.
     */
    val MACOS = ColorPalette(
        id = "macos",
        name = "macOS",
        black = "0xFF000000",
        red = "0xFF990000",
        green = "0xFF00A600",
        yellow = "0xFF999900",
        blue = "0xFF0000B2",
        magenta = "0xFFB200B2",
        cyan = "0xFF00A6B2",
        white = "0xFFBFBFBF",
        brightBlack = "0xFF666666",
        brightRed = "0xFFE50000",
        brightGreen = "0xFF00D900",
        brightYellow = "0xFFE5E500",
        brightBlue = "0xFF0000FF",
        brightMagenta = "0xFFE500E5",
        brightCyan = "0xFF00E5E5",
        brightWhite = "0xFFE5E5E5",
        isBuiltin = true
    )

    /**
     * XTerm palette - Classic XTerm default colors.
     */
    val XTERM = ColorPalette(
        id = "xterm",
        name = "XTerm",
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
     * Solarized palette - Ethan Schoonover's iconic palette.
     */
    val SOLARIZED = ColorPalette(
        id = "solarized",
        name = "Solarized",
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
     * Dracula palette - Popular purple-tinted palette.
     */
    val DRACULA = ColorPalette(
        id = "dracula-palette",
        name = "Dracula",
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
     * Nord palette - Arctic blue colors.
     */
    val NORD = ColorPalette(
        id = "nord-palette",
        name = "Nord",
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
     * Gruvbox palette - Retro warm colors.
     */
    val GRUVBOX = ColorPalette(
        id = "gruvbox-palette",
        name = "Gruvbox",
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
     * One Half palette - Clean, modern colors.
     */
    val ONE_HALF = ColorPalette(
        id = "one-half",
        name = "One Half",
        black = "0xFF383A42",
        red = "0xFFE45649",
        green = "0xFF50A14F",
        yellow = "0xFFC18401",
        blue = "0xFF0184BC",
        magenta = "0xFFA626A4",
        cyan = "0xFF0997B3",
        white = "0xFFFAFAFA",
        brightBlack = "0xFF4F525D",
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
     * Monokai palette - Sublime Text inspired.
     */
    val MONOKAI = ColorPalette(
        id = "monokai-palette",
        name = "Monokai",
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
     * Tokyo Night palette - Japanese aesthetic.
     */
    val TOKYO_NIGHT = ColorPalette(
        id = "tokyo-night-palette",
        name = "Tokyo Night",
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
     * Catppuccin palette - Soothing pastels.
     */
    val CATPPUCCIN = ColorPalette(
        id = "catppuccin-palette",
        name = "Catppuccin",
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
     * Retro palette - Classic CRT monitor feel.
     */
    val RETRO = ColorPalette(
        id = "retro",
        name = "Retro",
        black = "0xFF000000",
        red = "0xFFFF0000",
        green = "0xFF00FF00",
        yellow = "0xFFFFFF00",
        blue = "0xFF0000FF",
        magenta = "0xFFFF00FF",
        cyan = "0xFF00FFFF",
        white = "0xFFFFFFFF",
        brightBlack = "0xFF808080",
        brightRed = "0xFFFF8080",
        brightGreen = "0xFF80FF80",
        brightYellow = "0xFFFFFF80",
        brightBlue = "0xFF8080FF",
        brightMagenta = "0xFFFF80FF",
        brightCyan = "0xFF80FFFF",
        brightWhite = "0xFFFFFFFF",
        isBuiltin = true
    )

    /**
     * Material palette - Google Material Design inspired.
     */
    val MATERIAL = ColorPalette(
        id = "material",
        name = "Material",
        black = "0xFF212121",
        red = "0xFFB71C1C",
        green = "0xFF1B5E20",
        yellow = "0xFFF57F17",
        blue = "0xFF0D47A1",
        magenta = "0xFF4A148C",
        cyan = "0xFF006064",
        white = "0xFFECEFF1",
        brightBlack = "0xFF616161",
        brightRed = "0xFFF44336",
        brightGreen = "0xFF4CAF50",
        brightYellow = "0xFFFFEB3B",
        brightBlue = "0xFF2196F3",
        brightMagenta = "0xFF9C27B0",
        brightCyan = "0xFF00BCD4",
        brightWhite = "0xFFFFFFFF",
        isBuiltin = true
    )

    /**
     * All built-in color palettes.
     */
    val ALL = listOf(
        XTERM,
        TANGO,
        UBUNTU,
        MACOS,
        SOLARIZED,
        DRACULA,
        NORD,
        GRUVBOX,
        MONOKAI,
        ONE_HALF,
        TOKYO_NIGHT,
        CATPPUCCIN,
        PASTEL,
        MATERIAL,
        RETRO
    )

    /**
     * Get a built-in palette by ID.
     */
    fun getById(id: String): ColorPalette? = ALL.find { it.id == id }

    /**
     * Default palette ID.
     */
    const val DEFAULT_PALETTE_ID = "xterm"
}
