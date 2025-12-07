package ai.rever.bossterm.compose.settings.sections

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import ai.rever.bossterm.compose.settings.TerminalSettings
import ai.rever.bossterm.compose.settings.components.*

/**
 * Type-ahead settings section: latency prediction for SSH.
 */
@Composable
fun TypeAheadSettingsSection(
    settings: TerminalSettings,
    onSettingsChange: (TerminalSettings) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        SettingsSection(title = "Type-Ahead Prediction") {
            SettingsToggle(
                label = "Enable Type-Ahead",
                checked = settings.typeAheadEnabled,
                onCheckedChange = { onSettingsChange(settings.copy(typeAheadEnabled = it)) },
                description = "Predict keystrokes to reduce perceived latency on SSH"
            )

            SettingsLongInput(
                label = "Latency Threshold (nanoseconds)",
                value = settings.typeAheadLatencyThresholdNanos,
                onValueChange = { onSettingsChange(settings.copy(typeAheadLatencyThresholdNanos = it)) },
                range = 10_000_000L..1_000_000_000L,
                description = "Show predictions when latency exceeds this (default: 100ms)",
                enabled = settings.typeAheadEnabled
            )
        }
    }
}
