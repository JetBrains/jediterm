package ai.rever.bossterm.compose.settings

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import java.io.File

/**
 * Manager for terminal settings with persistence support.
 * Settings are saved to ~/.bossterm/settings.json by default,
 * or to a custom path if specified.
 *
 * @param customSettingsPath Optional custom path for settings file.
 *        If null, uses default ~/.bossterm/settings.json
 */
class SettingsManager(private val customSettingsPath: String? = null) {
    private val _settings = MutableStateFlow(TerminalSettings.DEFAULT)

    /**
     * Current settings as a StateFlow (reactive)
     */
    val settings: StateFlow<TerminalSettings> = _settings.asStateFlow()

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    private val settingsDir: File by lazy {
        if (customSettingsPath != null) {
            File(customSettingsPath).parentFile?.apply {
                if (!exists()) mkdirs()
            } ?: File(System.getProperty("user.home"), ".bossterm").apply {
                if (!exists()) mkdirs()
            }
        } else {
            File(System.getProperty("user.home"), ".bossterm").apply {
                if (!exists()) mkdirs()
            }
        }
    }

    private val settingsFile: File by lazy {
        if (customSettingsPath != null) {
            File(customSettingsPath)
        } else {
            File(settingsDir, "settings.json")
        }
    }

    init {
        loadFromFile()
    }

    /**
     * Update settings and save to file
     */
    fun updateSettings(newSettings: TerminalSettings) {
        _settings.value = newSettings
        saveToFile()
    }

    /**
     * Update a single setting field
     */
    fun updateSetting(updater: TerminalSettings.() -> TerminalSettings) {
        updateSettings(updater(_settings.value))
    }

    /**
     * Reset settings to defaults
     */
    fun resetToDefaults() {
        updateSettings(TerminalSettings.DEFAULT)
    }

    /**
     * Save current settings to file
     */
    fun saveToFile() {
        try {
            val jsonString = json.encodeToString(_settings.value)
            settingsFile.writeText(jsonString)
            println("Settings saved to: ${settingsFile.absolutePath}")
        } catch (e: Exception) {
            System.err.println("Failed to save settings: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * Load settings from file
     */
    fun loadFromFile() {
        try {
            if (settingsFile.exists()) {
                val jsonString = settingsFile.readText()
                val loadedSettings = json.decodeFromString<TerminalSettings>(jsonString)
                _settings.value = loadedSettings
                println("Settings loaded from: ${settingsFile.absolutePath}")
            } else {
                println("No settings file found, using defaults")
                // Save defaults on first run
                saveToFile()
            }
        } catch (e: Exception) {
            System.err.println("Failed to load settings, using defaults: ${e.message}")
            e.printStackTrace()
            _settings.value = TerminalSettings.DEFAULT
        }
    }

    companion object {
        /**
         * Global singleton instance using default settings path
         */
        val instance: SettingsManager by lazy { SettingsManager() }

        /**
         * Create a new SettingsManager with a custom settings file path.
         *
         * @param path Path to the settings JSON file
         * @return New SettingsManager instance using the custom path
         */
        fun withCustomPath(path: String): SettingsManager {
            return SettingsManager(customSettingsPath = path)
        }
    }
}
