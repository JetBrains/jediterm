package org.jetbrains.jediterm.compose.settings

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import java.io.File

/**
 * Manager for terminal settings with persistence support.
 * Settings are saved to ~/.jediterm/settings.json
 */
class SettingsManager {
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
        File(System.getProperty("user.home"), ".jediterm").apply {
            if (!exists()) {
                mkdirs()
            }
        }
    }

    private val settingsFile: File by lazy {
        File(settingsDir, "settings.json")
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
         * Global singleton instance
         */
        val instance: SettingsManager by lazy { SettingsManager() }
    }
}
