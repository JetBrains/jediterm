package ai.rever.bossterm.compose.settings.sections

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import ai.rever.bossterm.compose.settings.TerminalSettings
import ai.rever.bossterm.compose.settings.components.*

/**
 * Hyperlink settings section: URL detection and click behavior.
 */
@Composable
fun HyperlinkSettingsSection(
    settings: TerminalSettings,
    onSettingsChange: (TerminalSettings) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        SettingsSection(title = "Hyperlink Behavior") {
            SettingsToggle(
                label = "Underline on Hover",
                checked = settings.hyperlinkUnderlineOnHover,
                onCheckedChange = { onSettingsChange(settings.copy(hyperlinkUnderlineOnHover = it)) },
                description = "Show underline when hovering over hyperlinks"
            )

            SettingsToggle(
                label = "Require Modifier Key to Click",
                checked = settings.hyperlinkRequireModifier,
                onCheckedChange = { onSettingsChange(settings.copy(hyperlinkRequireModifier = it)) },
                description = "Require Ctrl/Cmd to open links"
            )
        }
    }
}
