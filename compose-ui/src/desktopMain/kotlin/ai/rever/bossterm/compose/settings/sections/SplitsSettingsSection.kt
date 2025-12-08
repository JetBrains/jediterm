package ai.rever.bossterm.compose.settings.sections

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ai.rever.bossterm.compose.settings.TerminalSettings
import ai.rever.bossterm.compose.settings.components.*
import ai.rever.bossterm.compose.settings.toSettingsHex

/**
 * Split pane settings section: behavior and appearance.
 */
@Composable
fun SplitsSettingsSection(
    settings: TerminalSettings,
    onSettingsChange: (TerminalSettings) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        SettingsSection(title = "Split Behavior") {
            SettingsSlider(
                label = "Default Split Ratio",
                value = settings.splitDefaultRatio,
                onValueChange = { onSettingsChange(settings.copy(splitDefaultRatio = it)) },
                valueRange = 0.3f..0.7f,
                steps = 8,
                valueDisplay = { "${(it * 100).toInt()}%" },
                description = "Initial ratio for new splits (e.g., 50% = equal split)"
            )

            SettingsSlider(
                label = "Minimum Pane Size",
                value = settings.splitMinimumSize,
                onValueChange = { onSettingsChange(settings.copy(splitMinimumSize = it)) },
                valueRange = 0.05f..0.4f,
                steps = 7,
                valueDisplay = { "${(it * 100).toInt()}%" },
                description = "Minimum size when resizing panes"
            )

            SettingsToggle(
                label = "Inherit Working Directory",
                checked = settings.splitInheritWorkingDirectory,
                onCheckedChange = { onSettingsChange(settings.copy(splitInheritWorkingDirectory = it)) },
                description = "New splits start in the same directory as parent"
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        SettingsSection(title = "Focus Indicator") {
            SettingsToggle(
                label = "Show Focus Border",
                checked = settings.splitFocusBorderEnabled,
                onCheckedChange = { onSettingsChange(settings.copy(splitFocusBorderEnabled = it)) },
                description = "Highlight the focused pane with a colored border"
            )

            ColorSetting(
                label = "Focus Border Color",
                color = settings.splitFocusBorderColorValue,
                onColorChange = { onSettingsChange(settings.copy(splitFocusBorderColor = it.toSettingsHex())) },
                description = "Color of the focus indicator border",
                enabled = settings.splitFocusBorderEnabled
            )
        }
    }
}
