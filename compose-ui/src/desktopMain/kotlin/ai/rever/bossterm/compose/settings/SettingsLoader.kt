package ai.rever.bossterm.compose.settings

import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import java.io.File

/**
 * Utility for loading terminal settings from various sources.
 *
 * Supports loading from:
 * - Custom file paths
 * - Default location (~/.bossterm/settings.json)
 * - TerminalSettings objects directly
 */
object SettingsLoader {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    private val defaultSettingsFile: File by lazy {
        File(System.getProperty("user.home"), ".bossterm/settings.json")
    }

    /**
     * Load settings from a specific file path.
     *
     * @param path Absolute path to the settings JSON file
     * @return Loaded settings, or DEFAULT if file doesn't exist or is invalid
     */
    fun loadFromPath(path: String): TerminalSettings {
        return try {
            val file = File(path)
            if (file.exists()) {
                val jsonString = file.readText()
                json.decodeFromString<TerminalSettings>(jsonString)
            } else {
                println("Settings file not found at: $path, using defaults")
                TerminalSettings.DEFAULT
            }
        } catch (e: Exception) {
            System.err.println("Failed to load settings from $path: ${e.message}")
            TerminalSettings.DEFAULT
        }
    }

    /**
     * Load settings from a path if provided, otherwise use default location.
     *
     * @param path Optional path to settings file. If null, loads from ~/.bossterm/settings.json
     * @return Loaded settings
     */
    fun loadFromPathOrDefault(path: String?): TerminalSettings {
        return if (path != null) {
            loadFromPath(path)
        } else {
            loadFromPath(defaultSettingsFile.absolutePath)
        }
    }

    /**
     * Resolve settings based on priority:
     * 1. Direct settings object (highest priority)
     * 2. Custom path
     * 3. Default path (~/.bossterm/settings.json)
     *
     * @param settings Direct settings object (optional)
     * @param settingsPath Custom path to settings file (optional)
     * @return Resolved settings based on priority
     */
    fun resolveSettings(
        settings: TerminalSettings? = null,
        settingsPath: String? = null
    ): TerminalSettings {
        return settings ?: loadFromPathOrDefault(settingsPath)
    }
}
