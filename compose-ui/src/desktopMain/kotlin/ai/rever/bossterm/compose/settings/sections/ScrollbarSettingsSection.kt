package ai.rever.bossterm.compose.settings.sections

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ai.rever.bossterm.compose.settings.TerminalSettings
import ai.rever.bossterm.compose.settings.TerminalSettings.Companion.toHexString
import ai.rever.bossterm.compose.settings.components.*

/**
 * Scrollbar settings section: appearance and search markers.
 */
@Composable
fun ScrollbarSettingsSection(
    settings: TerminalSettings,
    onSettingsChange: (TerminalSettings) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        // Appearance Settings
        SettingsSection(title = "Appearance") {
            SettingsToggle(
                label = "Show Scrollbar",
                checked = settings.showScrollbar,
                onCheckedChange = { onSettingsChange(settings.copy(showScrollbar = it)) },
                description = "Display scrollbar on the right side"
            )

            SettingsToggle(
                label = "Always Visible",
                checked = settings.scrollbarAlwaysVisible,
                onCheckedChange = { onSettingsChange(settings.copy(scrollbarAlwaysVisible = it)) },
                description = "Always show scrollbar (vs auto-hide on inactivity)",
                enabled = settings.showScrollbar
            )

            SettingsSlider(
                label = "Scrollbar Width",
                value = settings.scrollbarWidth,
                onValueChange = { onSettingsChange(settings.copy(scrollbarWidth = it)) },
                valueRange = 6f..20f,
                steps = 13,
                valueDisplay = { "${it.toInt()} px" },
                enabled = settings.showScrollbar
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Colors
        SettingsSection(title = "Colors") {
            ColorSetting(
                label = "Track Color",
                color = settings.scrollbarColorValue,
                onColorChange = { onSettingsChange(settings.copy(scrollbarColor = it.toHexString())) },
                description = "Scrollbar background track",
                enabled = settings.showScrollbar
            )

            ColorSetting(
                label = "Thumb Color",
                color = settings.scrollbarThumbColorValue,
                onColorChange = { onSettingsChange(settings.copy(scrollbarThumbColor = it.toHexString())) },
                description = "Scrollbar handle",
                enabled = settings.showScrollbar
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Search Markers
        SettingsSection(title = "Search Markers") {
            SettingsToggle(
                label = "Show Search Markers",
                checked = settings.showSearchMarkersInScrollbar,
                onCheckedChange = { onSettingsChange(settings.copy(showSearchMarkersInScrollbar = it)) },
                description = "Highlight search matches in scrollbar",
                enabled = settings.showScrollbar
            )

            ColorSetting(
                label = "Marker Color",
                color = settings.searchMarkerColorValue,
                onColorChange = { onSettingsChange(settings.copy(searchMarkerColor = it.toHexString())) },
                description = "Search match indicator",
                enabled = settings.showScrollbar && settings.showSearchMarkersInScrollbar
            )

            ColorSetting(
                label = "Current Match Color",
                color = settings.currentSearchMarkerColorValue,
                onColorChange = { onSettingsChange(settings.copy(currentSearchMarkerColor = it.toHexString())) },
                description = "Current match indicator",
                enabled = settings.showScrollbar && settings.showSearchMarkersInScrollbar
            )
        }
    }
}
