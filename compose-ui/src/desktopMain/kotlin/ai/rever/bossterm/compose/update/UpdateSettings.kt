package ai.rever.bossterm.compose.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Update settings data class for serialization
 */
@Serializable
data class UpdateSettingsData(
    val autoCheckEnabled: Boolean = true,
    val checkIntervalHours: Long = 6
)

/**
 * Update settings singleton for managing update preferences.
 */
object UpdateSettings {
    private var _settings = UpdateSettingsData()

    /**
     * Whether automatic update checks are enabled
     */
    var autoCheckEnabled: Boolean
        get() = _settings.autoCheckEnabled
        set(value) {
            _settings = _settings.copy(autoCheckEnabled = value)
        }

    /**
     * Interval between automatic update checks in hours
     */
    var checkIntervalHours: Long
        get() = _settings.checkIntervalHours
        set(value) {
            _settings = _settings.copy(checkIntervalHours = value)
        }

    /**
     * Load settings from internal data
     */
    internal fun loadFromData(data: UpdateSettingsData) {
        _settings = data
    }

    /**
     * Get current settings data for saving
     */
    internal fun toData(): UpdateSettingsData = _settings
}

/**
 * Settings manager for persisting update preferences to disk.
 */
object UpdateSettingsManager {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    private val settingsDir: File by lazy {
        File(System.getProperty("user.home"), ".bossterm").also { it.mkdirs() }
    }

    private val settingsFile: File by lazy {
        File(settingsDir, "update-settings.json")
    }

    /**
     * Load settings from disk on startup
     */
    suspend fun loadSettings() = withContext(Dispatchers.IO) {
        try {
            if (settingsFile.exists()) {
                val content = settingsFile.readText()
                val data = json.decodeFromString<UpdateSettingsData>(content)
                UpdateSettings.loadFromData(data)
                println("✅ Loaded update settings from ${settingsFile.absolutePath}")
            }
        } catch (e: Exception) {
            println("⚠️ Could not load update settings: ${e.message}")
        }
    }

    /**
     * Save current settings to disk
     */
    suspend fun saveSettings() = withContext(Dispatchers.IO) {
        try {
            val content = json.encodeToString(UpdateSettingsData.serializer(), UpdateSettings.toData())
            settingsFile.writeText(content)
            println("✅ Saved update settings to ${settingsFile.absolutePath}")
        } catch (e: Exception) {
            println("⚠️ Could not save update settings: ${e.message}")
        }
    }
}
