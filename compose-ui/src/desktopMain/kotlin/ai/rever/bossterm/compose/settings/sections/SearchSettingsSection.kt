package ai.rever.bossterm.compose.settings.sections

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import ai.rever.bossterm.compose.settings.TerminalSettings
import ai.rever.bossterm.compose.settings.components.*

/**
 * Search settings section: search behavior defaults.
 */
@Composable
fun SearchSettingsSection(
    settings: TerminalSettings,
    onSettingsChange: (TerminalSettings) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        SettingsSection(title = "Search Defaults") {
            SettingsToggle(
                label = "Case Sensitive by Default",
                checked = settings.searchCaseSensitive,
                onCheckedChange = { onSettingsChange(settings.copy(searchCaseSensitive = it)) },
                description = "Search is case-sensitive when opened"
            )

            SettingsToggle(
                label = "Use Regex by Default",
                checked = settings.searchUseRegex,
                onCheckedChange = { onSettingsChange(settings.copy(searchUseRegex = it)) },
                description = "Enable regular expression search when opened"
            )
        }
    }
}
