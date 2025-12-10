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
        // Shell Settings
        SettingsSection(title = "Shell") {
            SettingsToggle(
                label = "Use Login Session (macOS)",
                checked = settings.useLoginSession,
                onCheckedChange = { onSettingsChange(settings.copy(useLoginSession = it)) },
                description = "Show 'Last login' message and register in utmp/wtmp"
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

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

            SettingsToggle(
                label = "OSC 52 Clipboard Access",
                checked = settings.clipboardOsc52Enabled,
                onCheckedChange = { onSettingsChange(settings.copy(clipboardOsc52Enabled = it)) },
                description = "Allow terminal apps to access clipboard"
            )

            SettingsToggle(
                label = "Allow Clipboard Read (OSC 52)",
                checked = settings.clipboardOsc52AllowRead,
                onCheckedChange = { onSettingsChange(settings.copy(clipboardOsc52AllowRead = it)) },
                description = "Allow apps to read clipboard (security risk)",
                enabled = settings.clipboardOsc52Enabled
            )

            SettingsToggle(
                label = "Allow Clipboard Write (OSC 52)",
                checked = settings.clipboardOsc52AllowWrite,
                onCheckedChange = { onSettingsChange(settings.copy(clipboardOsc52AllowWrite = it)) },
                description = "Allow apps to write to clipboard (tmux, etc.)",
                enabled = settings.clipboardOsc52Enabled
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

        // Bell Settings
        SettingsSection(title = "Bell") {
            SettingsToggle(
                label = "Audible Bell",
                checked = settings.audibleBell,
                onCheckedChange = { onSettingsChange(settings.copy(audibleBell = it)) },
                description = "Play sound on ASCII bell (Ctrl+G)"
            )

            SettingsToggle(
                label = "Visual Bell",
                checked = settings.visualBell,
                onCheckedChange = { onSettingsChange(settings.copy(visualBell = it)) },
                description = "Flash screen on ASCII bell"
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Progress Bar Settings
        SettingsSection(title = "Progress Bar") {
            SettingsToggle(
                label = "Enable Progress Bar",
                checked = settings.progressBarEnabled,
                onCheckedChange = { onSettingsChange(settings.copy(progressBarEnabled = it)) },
                description = "Show progress indicator (OSC 1337/9;4)"
            )

            SettingsDropdown(
                label = "Position",
                options = listOf("Top", "Bottom"),
                selectedOption = settings.progressBarPosition.replaceFirstChar { it.uppercase() },
                onOptionSelected = { onSettingsChange(settings.copy(progressBarPosition = it.lowercase())) },
                description = "Where to show the progress bar",
                enabled = settings.progressBarEnabled
            )

            SettingsSlider(
                label = "Height",
                value = settings.progressBarHeight,
                onValueChange = { onSettingsChange(settings.copy(progressBarHeight = it)) },
                valueRange = 1f..10f,
                steps = 8,
                valueDisplay = { "${it.toInt()} dp" },
                description = "Progress bar thickness",
                enabled = settings.progressBarEnabled
            )
        }
    }
}
