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
 * Notification settings section: command completion alerts.
 */
@Composable
fun NotificationSettingsSection(
    settings: TerminalSettings,
    onSettingsChange: (TerminalSettings) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        SettingsSection(title = "Command Notifications") {
            SettingsToggle(
                label = "Enable Notifications",
                checked = settings.notifyOnCommandComplete,
                onCheckedChange = { onSettingsChange(settings.copy(notifyOnCommandComplete = it)) },
                description = "Show notification when commands complete while unfocused"
            )

            SettingsSlider(
                label = "Minimum Duration",
                value = settings.notifyMinDurationSeconds.toFloat(),
                onValueChange = { onSettingsChange(settings.copy(notifyMinDurationSeconds = it.toInt())) },
                valueRange = 1f..60f,
                steps = 58,
                valueDisplay = { "${it.toInt()} sec" },
                description = "Only notify for commands longer than this",
                enabled = settings.notifyOnCommandComplete
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        SettingsSection(title = "Notification Style") {
            SettingsToggle(
                label = "Show Exit Code",
                checked = settings.notifyShowExitCode,
                onCheckedChange = { onSettingsChange(settings.copy(notifyShowExitCode = it)) },
                description = "Include exit code in notification message",
                enabled = settings.notifyOnCommandComplete
            )

            SettingsToggle(
                label = "Play Sound",
                checked = settings.notifyWithSound,
                onCheckedChange = { onSettingsChange(settings.copy(notifyWithSound = it)) },
                description = "Play system notification sound",
                enabled = settings.notifyOnCommandComplete
            )
        }
    }
}
