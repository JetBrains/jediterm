package ai.rever.bossterm.compose.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Categories for organizing terminal settings in the settings panel.
 * Note: Using basic Material icons available in Compose Desktop.
 */
enum class SettingsCategory(
    val displayName: String,
    val icon: ImageVector,
    val description: String
) {
    VISUAL(
        displayName = "Visual",
        icon = Icons.Default.Settings,
        description = "Font, colors, and appearance"
    ),
    BEHAVIOR(
        displayName = "Behavior",
        icon = Icons.Default.Build,
        description = "Mouse, keyboard, and interaction"
    ),
    SCROLLBAR(
        displayName = "Scrollbar",
        icon = Icons.Default.Menu,
        description = "Scrollbar appearance and markers"
    ),
    PERFORMANCE(
        displayName = "Performance",
        icon = Icons.Default.Refresh,
        description = "Refresh rate, buffer, and blink settings"
    ),
    EMULATION(
        displayName = "Emulation",
        icon = Icons.Default.Star,
        description = "Terminal compatibility options"
    ),
    SEARCH(
        displayName = "Search",
        icon = Icons.Default.Search,
        description = "Search behavior defaults"
    ),
    HYPERLINKS(
        displayName = "Hyperlinks",
        icon = Icons.Default.Share,
        description = "URL detection and click behavior"
    ),
    TYPE_AHEAD(
        displayName = "Type-Ahead",
        icon = Icons.Default.Edit,
        description = "Latency prediction for SSH"
    ),
    DEBUG(
        displayName = "Debug",
        icon = Icons.Default.Warning,
        description = "Debug panel and data capture"
    ),
    LOGGING(
        displayName = "Logging",
        icon = Icons.Default.Info,
        description = "File logging options"
    ),
    NOTIFICATIONS(
        displayName = "Notifications",
        icon = Icons.Default.Email,
        description = "Command completion alerts"
    );

    companion object {
        /**
         * Get the default/initial category to display.
         */
        val default: SettingsCategory = VISUAL
    }
}
