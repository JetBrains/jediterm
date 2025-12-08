package ai.rever.bossterm.compose.settings.sections

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ai.rever.bossterm.compose.settings.TerminalSettings
import ai.rever.bossterm.compose.settings.components.*

/**
 * Visual settings section: font, text rendering, and transparency.
 * Note: Color settings are in the Themes section.
 */
@Composable
fun VisualSettingsSection(
    settings: TerminalSettings,
    onSettingsChange: (TerminalSettings) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        // Font Settings
        SettingsSection(title = "Font") {
            SettingsSlider(
                label = "Font Size",
                value = settings.fontSize,
                onValueChange = { onSettingsChange(settings.copy(fontSize = it)) },
                valueRange = 8f..24f,
                steps = 15,
                valueDisplay = { "${it.toInt()} sp" }
            )

            SettingsSlider(
                label = "Line Spacing",
                value = settings.lineSpacing,
                onValueChange = { onSettingsChange(settings.copy(lineSpacing = it)) },
                valueRange = 0.8f..2.0f,
                valueDisplay = { "%.2fx".format(it) },
                description = "Multiplier for line height (1.0 = normal)"
            )

            SettingsToggle(
                label = "Disable Line Spacing in Fullscreen Apps",
                checked = settings.disableLineSpacingInAlternateBuffer,
                onCheckedChange = { onSettingsChange(settings.copy(disableLineSpacingInAlternateBuffer = it)) },
                description = "Remove extra spacing in vim, htop, less"
            )

            SettingsToggle(
                label = "Fill Background in Line Spacing",
                checked = settings.fillBackgroundInLineSpacing,
                onCheckedChange = { onSettingsChange(settings.copy(fillBackgroundInLineSpacing = it)) },
                description = "Extend background colors into line spacing area"
            )

            SettingsToggle(
                label = "Use Antialiasing",
                checked = settings.useAntialiasing,
                onCheckedChange = { onSettingsChange(settings.copy(useAntialiasing = it)) },
                description = "Smooth text rendering"
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Transparency Settings
        SettingsSection(title = "Transparency") {
            SettingsSlider(
                label = "Background Opacity",
                value = settings.backgroundOpacity,
                onValueChange = { onSettingsChange(settings.copy(backgroundOpacity = it)) },
                valueRange = 0.1f..1.0f,
                steps = 17,
                valueDisplay = { "${(it * 100).toInt()}%" },
                description = "Make the terminal background transparent to see through to desktop"
            )

            SettingsToggle(
                label = "Enable Blur Effect",
                checked = settings.windowBlur,
                onCheckedChange = { onSettingsChange(settings.copy(windowBlur = it)) },
                description = "Frosted glass effect behind transparent terminal (macOS)",
                enabled = settings.backgroundOpacity < 1.0f
            )
        }
    }
}
