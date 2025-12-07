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
 * Terminal emulation settings section: compatibility options.
 */
@Composable
fun TerminalEmulationSection(
    settings: TerminalSettings,
    onSettingsChange: (TerminalSettings) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        // Compatibility Settings
        SettingsSection(title = "Compatibility") {
            SettingsToggle(
                label = "DEC Compatibility Mode",
                checked = settings.decCompatibilityMode,
                onCheckedChange = { onSettingsChange(settings.copy(decCompatibilityMode = it)) },
                description = "Enable DEC terminal compatibility"
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Character Encoding
        SettingsSection(title = "Character Encoding") {
            SettingsDropdown(
                label = "Character Encoding",
                options = listOf("UTF-8", "ISO-8859-1"),
                selectedOption = settings.characterEncoding,
                onOptionSelected = { onSettingsChange(settings.copy(characterEncoding = it)) },
                description = "Text encoding mode (auto-detected from locale)"
            )

            SettingsToggle(
                label = "Ambiguous Chars Are Double-Width",
                checked = settings.ambiguousCharsAreDoubleWidth,
                onCheckedChange = { onSettingsChange(settings.copy(ambiguousCharsAreDoubleWidth = it)) },
                description = "Treat ambiguous-width Unicode chars as double-width"
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Mouse Emulation
        SettingsSection(title = "Mouse Emulation") {
            SettingsToggle(
                label = "Simulate Scroll in Alternate Screen",
                checked = settings.simulateMouseScrollInAlternateScreen,
                onCheckedChange = { onSettingsChange(settings.copy(simulateMouseScrollInAlternateScreen = it)) },
                description = "Convert scroll wheel to arrow keys in vim/less"
            )
        }
    }
}
