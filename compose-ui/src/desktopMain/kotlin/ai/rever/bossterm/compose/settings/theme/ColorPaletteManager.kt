package ai.rever.bossterm.compose.settings.theme

import ai.rever.bossterm.compose.settings.SettingsManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

/**
 * Container for serializing custom palettes to JSON.
 */
@Serializable
private data class CustomPalettesContainer(
    val palettes: List<ColorPalette> = emptyList()
)

/**
 * Manager for terminal color palettes.
 *
 * Handles:
 * - Built-in palettes (Tango, Solarized, etc.)
 * - Custom palettes (persisted to ~/.bossterm/palettes.json)
 * - Active palette tracking (stored in TerminalSettings.colorPaletteId)
 *
 * Color palettes can be applied independently of themes, allowing users to
 * mix and match theme colors (fg/bg/cursor) with different ANSI palettes.
 */
class ColorPaletteManager private constructor(
    private val settingsManager: SettingsManager,
    private val themeManager: ThemeManager
) {
    private val _customPalettes = MutableStateFlow<List<ColorPalette>>(emptyList())

    /**
     * Custom palettes created by the user.
     */
    val customPalettes: StateFlow<List<ColorPalette>> = _customPalettes.asStateFlow()

    private val _currentPalette = MutableStateFlow<ColorPalette?>(null)

    /**
     * Currently active palette, or null if using theme's palette.
     */
    val currentPalette: StateFlow<ColorPalette?> = _currentPalette.asStateFlow()

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    private val palettesDir: File by lazy {
        File(System.getProperty("user.home"), ".bossterm").apply {
            if (!exists()) mkdirs()
        }
    }

    private val palettesFile: File by lazy {
        File(palettesDir, "palettes.json")
    }

    init {
        loadCustomPalettes()
        loadActivePalette()
    }

    /**
     * Whether the current setting is to use the theme's palette.
     */
    fun isUsingThemePalette(): Boolean {
        return settingsManager.settings.value.colorPaletteId == ColorPalette.USE_THEME_PALETTE_ID
    }

    /**
     * Get the effective palette (either custom or from current theme).
     */
    fun getEffectivePalette(): ColorPalette {
        return _currentPalette.value ?: ColorPalette.fromTheme(themeManager.currentTheme.value)
    }

    /**
     * All available palettes (built-in + custom).
     */
    fun getAllPalettes(): List<ColorPalette> {
        return BuiltinColorPalettes.ALL + _customPalettes.value
    }

    /**
     * Get a palette by ID (checks built-in and custom).
     */
    fun getPaletteById(id: String): ColorPalette? {
        return BuiltinColorPalettes.getById(id) ?: _customPalettes.value.find { it.id == id }
    }

    /**
     * Apply a palette (sets it as active).
     */
    fun applyPalette(palette: ColorPalette) {
        _currentPalette.value = palette
        settingsManager.updateSetting {
            copy(colorPaletteId = palette.id)
        }
    }

    /**
     * Reset to using the theme's palette.
     */
    fun useThemePalette() {
        _currentPalette.value = null
        settingsManager.updateSetting {
            copy(colorPaletteId = ColorPalette.USE_THEME_PALETTE_ID)
        }
    }

    /**
     * Apply a palette by ID.
     */
    fun applyPaletteById(id: String) {
        if (id == ColorPalette.USE_THEME_PALETTE_ID) {
            useThemePalette()
        } else {
            val palette = getPaletteById(id) ?: return
            applyPalette(palette)
        }
    }

    /**
     * Create a new custom palette based on an existing palette.
     */
    fun createCustomPalette(basedOn: ColorPalette, name: String): ColorPalette {
        val newPalette = basedOn.copy(
            id = UUID.randomUUID().toString(),
            name = name,
            isBuiltin = false
        )
        val updatedPalettes = _customPalettes.value + newPalette
        _customPalettes.value = updatedPalettes
        saveCustomPalettes()
        return newPalette
    }

    /**
     * Create a custom palette from the current theme's colors.
     */
    fun createCustomPaletteFromTheme(name: String): ColorPalette {
        val themePalette = ColorPalette.fromTheme(themeManager.currentTheme.value)
        return createCustomPalette(themePalette, name)
    }

    /**
     * Save changes to an existing custom palette.
     */
    fun updateCustomPalette(palette: ColorPalette) {
        if (palette.isBuiltin) {
            throw IllegalArgumentException("Cannot modify a built-in palette")
        }
        val updatedPalettes = _customPalettes.value.map {
            if (it.id == palette.id) palette else it
        }
        _customPalettes.value = updatedPalettes
        saveCustomPalettes()

        // If this is the current palette, update it
        if (_currentPalette.value?.id == palette.id) {
            _currentPalette.value = palette
        }
    }

    /**
     * Delete a custom palette.
     */
    fun deleteCustomPalette(id: String) {
        val palette = _customPalettes.value.find { it.id == id } ?: return

        if (palette.isBuiltin) {
            throw IllegalArgumentException("Cannot delete a built-in palette")
        }

        val updatedPalettes = _customPalettes.value.filter { it.id != id }
        _customPalettes.value = updatedPalettes
        saveCustomPalettes()

        // If we deleted the current palette, switch to theme palette
        if (_currentPalette.value?.id == id) {
            useThemePalette()
        }
    }

    /**
     * Export a palette to JSON string.
     */
    fun exportPalette(palette: ColorPalette): String {
        return json.encodeToString(palette)
    }

    /**
     * Import a palette from JSON string.
     */
    fun importPalette(jsonString: String): ColorPalette {
        val importedPalette = json.decodeFromString<ColorPalette>(jsonString)
        // Assign a new ID and mark as not built-in
        val newPalette = importedPalette.copy(
            id = UUID.randomUUID().toString(),
            isBuiltin = false
        )
        val updatedPalettes = _customPalettes.value + newPalette
        _customPalettes.value = updatedPalettes
        saveCustomPalettes()
        return newPalette
    }

    /**
     * Save custom palettes to file.
     */
    private fun saveCustomPalettes() {
        try {
            val container = CustomPalettesContainer(palettes = _customPalettes.value)
            val jsonString = json.encodeToString(container)
            palettesFile.writeText(jsonString)
        } catch (e: Exception) {
            System.err.println("Failed to save custom palettes: ${e.message}")
        }
    }

    /**
     * Load custom palettes from file.
     */
    private fun loadCustomPalettes() {
        try {
            if (palettesFile.exists()) {
                val jsonString = palettesFile.readText()
                val container = json.decodeFromString<CustomPalettesContainer>(jsonString)
                _customPalettes.value = container.palettes
            }
        } catch (e: Exception) {
            System.err.println("Failed to load custom palettes: ${e.message}")
            _customPalettes.value = emptyList()
        }
    }

    /**
     * Load the active palette based on settings.
     */
    private fun loadActivePalette() {
        val paletteId = settingsManager.settings.value.colorPaletteId
        if (paletteId == ColorPalette.USE_THEME_PALETTE_ID) {
            _currentPalette.value = null
        } else {
            _currentPalette.value = getPaletteById(paletteId)
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: ColorPaletteManager? = null

        /**
         * Get the singleton instance.
         */
        val instance: ColorPaletteManager
            get() = INSTANCE ?: synchronized(this) {
                INSTANCE ?: ColorPaletteManager(
                    SettingsManager.instance,
                    ThemeManager.instance
                ).also { INSTANCE = it }
            }

        /**
         * Initialize with custom managers (for testing).
         */
        fun initialize(settingsManager: SettingsManager, themeManager: ThemeManager): ColorPaletteManager {
            return synchronized(this) {
                ColorPaletteManager(settingsManager, themeManager).also { INSTANCE = it }
            }
        }
    }
}
