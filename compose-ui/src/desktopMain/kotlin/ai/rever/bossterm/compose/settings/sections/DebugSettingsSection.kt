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
 * Debug settings section: debug panel and data capture.
 */
@Composable
fun DebugSettingsSection(
    settings: TerminalSettings,
    onSettingsChange: (TerminalSettings) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        // Enable Debug Mode
        SettingsSection(title = "Debug Mode") {
            SettingsToggle(
                label = "Enable Debug Mode",
                checked = settings.debugModeEnabled,
                onCheckedChange = { onSettingsChange(settings.copy(debugModeEnabled = it)) },
                description = "Capture I/O for debug panel inspection"
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Buffer Settings
        SettingsSection(title = "Data Capture") {
            SettingsNumberInput(
                label = "Max I/O Chunks",
                value = settings.debugMaxChunks,
                onValueChange = { onSettingsChange(settings.copy(debugMaxChunks = it)) },
                range = 100..10000,
                description = "Circular buffer size for I/O data (100-10000)",
                enabled = settings.debugModeEnabled
            )

            SettingsNumberInput(
                label = "Max State Snapshots",
                value = settings.debugMaxSnapshots,
                onValueChange = { onSettingsChange(settings.copy(debugMaxSnapshots = it)) },
                range = 10..1000,
                description = "Time-travel snapshots to keep (10-1000)",
                enabled = settings.debugModeEnabled
            )

            SettingsLongInput(
                label = "Snapshot Interval (ms)",
                value = settings.debugCaptureInterval,
                onValueChange = { onSettingsChange(settings.copy(debugCaptureInterval = it)) },
                range = 50L..1000L,
                description = "Auto-capture interval (50-1000 ms)",
                enabled = settings.debugModeEnabled
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Visualization Settings
        SettingsSection(title = "Visualization") {
            SettingsToggle(
                label = "Show Chunk IDs",
                checked = settings.debugShowChunkIds,
                onCheckedChange = { onSettingsChange(settings.copy(debugShowChunkIds = it)) },
                description = "Display chunk IDs in control sequence view",
                enabled = settings.debugModeEnabled
            )

            SettingsToggle(
                label = "Show Invisible Characters",
                checked = settings.debugShowInvisibleChars,
                onCheckedChange = { onSettingsChange(settings.copy(debugShowInvisibleChars = it)) },
                description = "Show tabs, spaces, newlines",
                enabled = settings.debugModeEnabled
            )

            SettingsToggle(
                label = "Wrap Long Lines",
                checked = settings.debugWrapLines,
                onCheckedChange = { onSettingsChange(settings.copy(debugWrapLines = it)) },
                description = "Wrap lines in debug sequence view",
                enabled = settings.debugModeEnabled
            )

            SettingsToggle(
                label = "Color-Code Sequences",
                checked = settings.debugColorCodeSequences,
                onCheckedChange = { onSettingsChange(settings.copy(debugColorCodeSequences = it)) },
                description = "Syntax highlighting for escape sequences",
                enabled = settings.debugModeEnabled
            )
        }
    }
}
