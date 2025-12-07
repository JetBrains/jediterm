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
 * Performance settings section: refresh rate, buffer, and blink settings.
 */
@Composable
fun PerformanceSettingsSection(
    settings: TerminalSettings,
    onSettingsChange: (TerminalSettings) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        // Rendering Settings
        SettingsSection(title = "Rendering") {
            SettingsSlider(
                label = "Maximum Refresh Rate",
                value = settings.maxRefreshRate.toFloat(),
                onValueChange = { onSettingsChange(settings.copy(maxRefreshRate = it.toInt())) },
                valueRange = 30f..120f,
                steps = 8,
                valueDisplay = { "${it.toInt()} FPS" },
                description = "Target frame rate (0 = unlimited)"
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Buffer Settings
        SettingsSection(title = "Buffer") {
            SettingsNumberInput(
                label = "Scrollback Buffer Lines",
                value = settings.bufferMaxLines,
                onValueChange = { onSettingsChange(settings.copy(bufferMaxLines = it)) },
                range = 1000..100000,
                description = "Maximum lines in history (1000-100000)"
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Cursor Settings
        SettingsSection(title = "Cursor") {
            SettingsSlider(
                label = "Cursor Blink Rate",
                value = settings.caretBlinkMs.toFloat(),
                onValueChange = { onSettingsChange(settings.copy(caretBlinkMs = it.toInt())) },
                valueRange = 0f..1000f,
                steps = 9,
                valueDisplay = { if (it.toInt() == 0) "Off" else "${it.toInt()} ms" },
                description = "Cursor blink interval (0 = no blink)"
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Text Blink Settings
        SettingsSection(title = "Text Blinking") {
            SettingsToggle(
                label = "Enable Text Blinking",
                checked = settings.enableTextBlinking,
                onCheckedChange = { onSettingsChange(settings.copy(enableTextBlinking = it)) },
                description = "Master toggle for all text blink animations"
            )

            SettingsSlider(
                label = "Slow Blink Rate",
                value = settings.slowTextBlinkMs.toFloat(),
                onValueChange = { onSettingsChange(settings.copy(slowTextBlinkMs = it.toInt())) },
                valueRange = 500f..2000f,
                steps = 14,
                valueDisplay = { "${it.toInt()} ms" },
                description = "Standard blink rate for BLINK attribute",
                enabled = settings.enableTextBlinking
            )

            SettingsSlider(
                label = "Rapid Blink Rate",
                value = settings.rapidTextBlinkMs.toFloat(),
                onValueChange = { onSettingsChange(settings.copy(rapidTextBlinkMs = it.toInt())) },
                valueRange = 200f..1000f,
                steps = 7,
                valueDisplay = { "${it.toInt()} ms" },
                description = "Fast blink rate for RAPID_BLINK",
                enabled = settings.enableTextBlinking
            )
        }
    }
}
