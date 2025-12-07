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
 * Behavior settings section: mouse, keyboard, and interaction.
 */
@Composable
fun BehaviorSettingsSection(
    settings: TerminalSettings,
    onSettingsChange: (TerminalSettings) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        // Clipboard Settings
        SettingsSection(title = "Clipboard") {
            SettingsToggle(
                label = "Copy on Select",
                checked = settings.copyOnSelect,
                onCheckedChange = { onSettingsChange(settings.copy(copyOnSelect = it)) },
                description = "Automatically copy selected text to clipboard"
            )

            SettingsToggle(
                label = "Paste on Middle Click",
                checked = settings.pasteOnMiddleClick,
                onCheckedChange = { onSettingsChange(settings.copy(pasteOnMiddleClick = it)) },
                description = "Middle mouse button pastes clipboard"
            )

            SettingsToggle(
                label = "Emulate X11 Copy/Paste",
                checked = settings.emulateX11CopyPaste,
                onCheckedChange = { onSettingsChange(settings.copy(emulateX11CopyPaste = it)) },
                description = "Separate selection and system clipboard"
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Keyboard Settings
        SettingsSection(title = "Keyboard") {
            SettingsToggle(
                label = "Alt Sends Escape",
                checked = settings.altSendsEscape,
                onCheckedChange = { onSettingsChange(settings.copy(altSendsEscape = it)) },
                description = "Alt key sends Escape prefix to terminal"
            )

            SettingsToggle(
                label = "Scroll to Bottom on Typing",
                checked = settings.scrollToBottomOnTyping,
                onCheckedChange = { onSettingsChange(settings.copy(scrollToBottomOnTyping = it)) },
                description = "Auto-scroll to bottom when typing"
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Mouse Settings
        SettingsSection(title = "Mouse") {
            SettingsToggle(
                label = "Enable Mouse Reporting",
                checked = settings.enableMouseReporting,
                onCheckedChange = { onSettingsChange(settings.copy(enableMouseReporting = it)) },
                description = "Forward mouse events to terminal applications"
            )

            SettingsToggle(
                label = "Force Local Actions",
                checked = settings.forceActionOnMouseReporting,
                onCheckedChange = { onSettingsChange(settings.copy(forceActionOnMouseReporting = it)) },
                description = "Always use local selection/scroll even with mouse reporting"
            )

            SettingsSlider(
                label = "Scroll Sensitivity Threshold",
                value = settings.mouseScrollThreshold,
                onValueChange = { onSettingsChange(settings.copy(mouseScrollThreshold = it)) },
                valueRange = 0f..2f,
                valueDisplay = { "%.1f".format(it) },
                description = "Higher = less sensitive (filters tiny scroll events)"
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Selection Settings
        SettingsSection(title = "Selection") {
            SettingsToggle(
                label = "Use Inverse Selection Color",
                checked = settings.useInverseSelectionColor,
                onCheckedChange = { onSettingsChange(settings.copy(useInverseSelectionColor = it)) },
                description = "Swap foreground/background colors for selection"
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Audio Settings
        SettingsSection(title = "Audio") {
            SettingsToggle(
                label = "Audible Bell",
                checked = settings.audibleBell,
                onCheckedChange = { onSettingsChange(settings.copy(audibleBell = it)) },
                description = "Play sound on ASCII bell (Ctrl+G)"
            )
        }
    }
}
