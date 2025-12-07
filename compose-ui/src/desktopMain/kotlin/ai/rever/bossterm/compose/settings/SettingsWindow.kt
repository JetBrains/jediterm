package ai.rever.bossterm.compose.settings

import androidx.compose.runtime.*
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState

/**
 * Settings dialog window.
 *
 * @param visible Whether the dialog is visible
 * @param onDismiss Called when the dialog should be closed
 */
@Composable
fun SettingsWindow(
    visible: Boolean,
    onDismiss: () -> Unit
) {
    if (!visible) return

    val settingsManager = remember { SettingsManager.instance }
    val currentSettings by settingsManager.settings.collectAsState()

    DialogWindow(
        onCloseRequest = onDismiss,
        title = "BossTerm Settings",
        resizable = false,
        state = rememberDialogState(
            size = DpSize(750.dp, 580.dp)
        )
    ) {
        SettingsPanel(
            settings = currentSettings,
            onSettingsChange = { newSettings ->
                settingsManager.updateSettings(newSettings)
            },
            onResetToDefaults = {
                settingsManager.resetToDefaults()
            }
        )
    }
}
