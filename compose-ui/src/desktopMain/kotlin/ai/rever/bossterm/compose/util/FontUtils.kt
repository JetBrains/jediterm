package ai.rever.bossterm.compose.util

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Typeface
import org.jetbrains.skia.FontMgr
import org.jetbrains.skia.FontStyle

/**
 * Special value indicating the bundled MesloLGS Nerd Font should be used.
 */
const val BUNDLED_FONT_NAME = "MesloLGS Nerd Font (Bundled)"

/** Section name for bundled font */
const val FONT_SECTION_BUNDLED = "Bundled"
/** Section name for fixed pitch (monospace) fonts */
const val FONT_SECTION_FIXED_PITCH = "Fixed Pitch"
/** Section name for variable pitch (proportional) fonts */
const val FONT_SECTION_VARIABLE_PITCH = "Variable Pitch"

/**
 * Get fonts organized by category (iTerm2-style).
 * Returns a map with sections: "Bundled", "Fixed Pitch", "Variable Pitch"
 */
fun getCategorizedFonts(): Map<String, List<String>> {
    val fontMgr = FontMgr.default
    val familyCount = fontMgr.familiesCount

    val allFamilies = (0 until familyCount)
        .map { fontMgr.getFamilyName(it) }
        .filter { it.isNotEmpty() }

    val fixedPitch = mutableListOf<String>()
    val variablePitch = mutableListOf<String>()

    for (familyName in allFamilies) {
        try {
            val typeface = fontMgr.matchFamilyStyle(familyName, FontStyle.NORMAL)
            if (typeface != null) {
                // Check if font is monospace by comparing glyph widths
                val font = org.jetbrains.skia.Font(typeface, 12f)
                val widthW = font.measureTextWidth("W")
                val widthI = font.measureTextWidth("i")
                // Allow small tolerance for floating point comparison
                if (kotlin.math.abs(widthW - widthI) < 0.1f) {
                    fixedPitch.add(familyName)
                } else {
                    variablePitch.add(familyName)
                }
            }
        } catch (e: Exception) {
            // Skip fonts that fail to load
        }
    }

    return mapOf(
        FONT_SECTION_BUNDLED to listOf(BUNDLED_FONT_NAME),
        FONT_SECTION_FIXED_PITCH to fixedPitch.sorted(),
        FONT_SECTION_VARIABLE_PITCH to variablePitch.sorted()
    )
}

/**
 * Get list of available fonts on the system using Skia's FontMgr.
 * @param monospaceOnly If true, only returns monospace fonts. If false, returns all fonts.
 * Includes the bundled font as the first option.
 * @deprecated Use getCategorizedFonts() for sectioned display
 */
fun getAvailableFonts(monospaceOnly: Boolean = true): List<String> {
    val categorized = getCategorizedFonts()
    return if (monospaceOnly) {
        categorized[FONT_SECTION_BUNDLED]!! + categorized[FONT_SECTION_FIXED_PITCH]!!
    } else {
        categorized[FONT_SECTION_BUNDLED]!! +
            categorized[FONT_SECTION_FIXED_PITCH]!! +
            categorized[FONT_SECTION_VARIABLE_PITCH]!!
    }
}

/**
 * Get list of available monospace fonts on the system.
 * Includes the bundled font as the first option.
 * @deprecated Use getCategorizedFonts() for sectioned display
 */
fun getAvailableMonospaceFonts(): List<String> = getAvailableFonts(monospaceOnly = true)

/**
 * Load terminal font by name.
 * @param fontName Font name from system fonts, or null/empty/BUNDLED_FONT_NAME for bundled font.
 * @return FontFamily for terminal rendering
 */
fun loadTerminalFont(fontName: String? = null): FontFamily {
    // Use bundled font if no name specified or if it's the bundled font marker
    if (fontName.isNullOrEmpty() || fontName == BUNDLED_FONT_NAME) {
        return loadBundledFont()
    }

    // Try to load system font by name using Skia FontMgr
    return try {
        // Use Skia's FontMgr to load system font by family name
        val skiaTypeface = FontMgr.default.matchFamilyStyle(fontName, FontStyle.NORMAL)
        if (skiaTypeface != null) {
            FontFamily(Typeface(skiaTypeface))
        } else {
            System.err.println("Font '$fontName' not found, falling back to bundled font")
            loadBundledFont()
        }
    } catch (e: Exception) {
        System.err.println("Failed to load font '$fontName': ${e.message}")
        loadBundledFont()
    }
}

/**
 * Lazily loaded bundled symbol font (Noto Sans Symbols 2).
 * Used as fallback for symbols like ⏵ ★ ⚡ when system font lacks coverage.
 */
val bundledSymbolFont: FontFamily by lazy {
    loadBundledSymbolFont()
}

/**
 * Load the bundled Noto Sans Symbols 2 font for symbol fallback.
 */
private fun loadBundledSymbolFont(): FontFamily {
    return try {
        val fontStream = object {}.javaClass.classLoader
            ?.getResourceAsStream("fonts/NotoSansSymbols2-Regular.ttf")
            ?: return FontFamily.Default

        val tempFile = java.io.File.createTempFile("NotoSansSymbols2", ".ttf")
        tempFile.deleteOnExit()
        fontStream.use { input ->
            tempFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }

        FontFamily(
            androidx.compose.ui.text.platform.Font(
                file = tempFile,
                weight = FontWeight.Normal
            )
        )
    } catch (e: Exception) {
        System.err.println("Failed to load bundled symbol font: ${e.message}")
        FontFamily.Default
    }
}

/**
 * Load the bundled MesloLGS Nerd Font.
 */
private fun loadBundledFont(): FontFamily {
    return try {
        val fontStream = object {}.javaClass.classLoader
            ?.getResourceAsStream("fonts/MesloLGSNF-Regular.ttf")
            ?: return FontFamily.Monospace

        val tempFile = java.io.File.createTempFile("MesloLGSNF", ".ttf")
        tempFile.deleteOnExit()
        fontStream.use { input ->
            tempFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }

        FontFamily(
            androidx.compose.ui.text.platform.Font(
                file = tempFile,
                weight = FontWeight.Normal
            )
        )
    } catch (e: Exception) {
        System.err.println("Failed to load bundled terminal font: ${e.message}")
        FontFamily.Monospace
    }
}
