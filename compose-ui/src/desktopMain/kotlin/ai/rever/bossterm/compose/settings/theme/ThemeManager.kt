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
 * Container for serializing custom themes to JSON.
 */
@Serializable
private data class CustomThemesContainer(
    val themes: List<Theme> = emptyList()
)

/**
 * Manager for terminal color themes.
 *
 * Handles:
 * - Built-in themes (Dracula, Nord, etc.)
 * - Custom themes (persisted to ~/.bossterm/themes.json)
 * - Active theme tracking (stored in TerminalSettings.activeThemeId)
 */
class ThemeManager private constructor(
    private val settingsManager: SettingsManager
) {
    private val _customThemes = MutableStateFlow<List<Theme>>(emptyList())

    /**
     * Custom themes created by the user.
     */
    val customThemes: StateFlow<List<Theme>> = _customThemes.asStateFlow()

    private val _currentTheme = MutableStateFlow(BuiltinThemes.DEFAULT)

    /**
     * Currently active theme.
     */
    val currentTheme: StateFlow<Theme> = _currentTheme.asStateFlow()

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    private val themesDir: File by lazy {
        File(System.getProperty("user.home"), ".bossterm").apply {
            if (!exists()) mkdirs()
        }
    }

    private val themesFile: File by lazy {
        File(themesDir, "themes.json")
    }

    init {
        loadCustomThemes()
        loadActiveTheme()
    }

    /**
     * All available themes (built-in + custom).
     */
    fun getAllThemes(): List<Theme> {
        return BuiltinThemes.ALL + _customThemes.value
    }

    /**
     * Get a theme by ID (checks built-in and custom).
     */
    fun getThemeById(id: String): Theme? {
        return BuiltinThemes.getById(id) ?: _customThemes.value.find { it.id == id }
    }

    /**
     * Apply a theme (sets it as active).
     */
    fun applyTheme(theme: Theme) {
        _currentTheme.value = theme

        // Update settings with the new theme's colors
        settingsManager.updateSetting {
            copy(
                activeThemeId = theme.id,
                defaultForeground = theme.foreground,
                defaultBackground = theme.background,
                selectionColor = theme.selection,
                foundPatternColor = theme.searchMatch,
                hyperlinkColor = theme.hyperlink
            )
        }
    }

    /**
     * Apply a theme by ID.
     */
    fun applyThemeById(id: String) {
        val theme = getThemeById(id) ?: BuiltinThemes.DEFAULT
        applyTheme(theme)
    }

    /**
     * Create a new custom theme based on an existing theme.
     */
    fun createCustomTheme(basedOn: Theme, name: String): Theme {
        val newTheme = basedOn.copy(
            id = UUID.randomUUID().toString(),
            name = name,
            isBuiltin = false
        )
        val updatedThemes = _customThemes.value + newTheme
        _customThemes.value = updatedThemes
        saveCustomThemes()
        return newTheme
    }

    /**
     * Save changes to an existing custom theme.
     */
    fun updateCustomTheme(theme: Theme) {
        if (theme.isBuiltin) {
            throw IllegalArgumentException("Cannot modify a built-in theme")
        }
        val updatedThemes = _customThemes.value.map {
            if (it.id == theme.id) theme else it
        }
        _customThemes.value = updatedThemes
        saveCustomThemes()

        // If this is the current theme, update it
        if (_currentTheme.value.id == theme.id) {
            applyTheme(theme)
        }
    }

    /**
     * Delete a custom theme.
     */
    fun deleteCustomTheme(id: String) {
        val theme = _customThemes.value.find { it.id == id } ?: return

        if (theme.isBuiltin) {
            throw IllegalArgumentException("Cannot delete a built-in theme")
        }

        val updatedThemes = _customThemes.value.filter { it.id != id }
        _customThemes.value = updatedThemes
        saveCustomThemes()

        // If we deleted the current theme, switch to default
        if (_currentTheme.value.id == id) {
            applyTheme(BuiltinThemes.DEFAULT)
        }
    }

    /**
     * Export a theme to JSON string.
     */
    fun exportTheme(theme: Theme): String {
        return json.encodeToString(theme)
    }

    /**
     * Import a theme from JSON string.
     */
    fun importTheme(jsonString: String): Theme {
        val importedTheme = json.decodeFromString<Theme>(jsonString)
        // Assign a new ID and mark as not built-in
        val newTheme = importedTheme.copy(
            id = UUID.randomUUID().toString(),
            isBuiltin = false
        )
        val updatedThemes = _customThemes.value + newTheme
        _customThemes.value = updatedThemes
        saveCustomThemes()
        return newTheme
    }

    /**
     * Save custom themes to file.
     */
    private fun saveCustomThemes() {
        try {
            val container = CustomThemesContainer(themes = _customThemes.value)
            val jsonString = json.encodeToString(container)
            themesFile.writeText(jsonString)
        } catch (e: Exception) {
            System.err.println("Failed to save custom themes: ${e.message}")
        }
    }

    /**
     * Load custom themes from file.
     */
    private fun loadCustomThemes() {
        try {
            if (themesFile.exists()) {
                val jsonString = themesFile.readText()
                val container = json.decodeFromString<CustomThemesContainer>(jsonString)
                _customThemes.value = container.themes
            }
        } catch (e: Exception) {
            System.err.println("Failed to load custom themes: ${e.message}")
            _customThemes.value = emptyList()
        }
    }

    /**
     * Load the active theme based on settings.
     */
    private fun loadActiveTheme() {
        val activeThemeId = settingsManager.settings.value.activeThemeId
        val theme = getThemeById(activeThemeId) ?: BuiltinThemes.DEFAULT
        _currentTheme.value = theme
    }

    companion object {
        @Volatile
        private var INSTANCE: ThemeManager? = null

        /**
         * Get the singleton instance.
         */
        val instance: ThemeManager
            get() = INSTANCE ?: synchronized(this) {
                INSTANCE ?: ThemeManager(SettingsManager.instance).also { INSTANCE = it }
            }

        /**
         * Initialize with a custom SettingsManager (for testing).
         */
        fun initialize(settingsManager: SettingsManager): ThemeManager {
            return synchronized(this) {
                ThemeManager(settingsManager).also { INSTANCE = it }
            }
        }
    }
}
