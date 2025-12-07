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
 * Logging settings section: file logging options.
 */
@Composable
fun LoggingSettingsSection(
    settings: TerminalSettings,
    onSettingsChange: (TerminalSettings) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        SettingsSection(title = "File Logging") {
            SettingsToggle(
                label = "Enable File Logging",
                checked = settings.fileLoggingEnabled,
                onCheckedChange = { onSettingsChange(settings.copy(fileLoggingEnabled = it)) },
                description = "Automatically log all terminal I/O to files"
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        SettingsSection(title = "Log Files") {
            SettingsTextField(
                label = "Log Directory",
                value = settings.fileLoggingDirectory,
                onValueChange = { onSettingsChange(settings.copy(fileLoggingDirectory = it)) },
                placeholder = "~/.bossterm/logs/",
                description = "Directory for log files (empty = default)",
                enabled = settings.fileLoggingEnabled
            )

            SettingsTextField(
                label = "Filename Pattern",
                value = settings.fileLoggingPattern,
                onValueChange = { onSettingsChange(settings.copy(fileLoggingPattern = it)) },
                placeholder = "bossterm_{timestamp}_{tab}.log",
                description = "Supports {timestamp}, {tab}, {pid} placeholders",
                enabled = settings.fileLoggingEnabled
            )
        }
    }
}
