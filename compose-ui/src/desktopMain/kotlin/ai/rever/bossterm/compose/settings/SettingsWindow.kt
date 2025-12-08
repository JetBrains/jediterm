package ai.rever.bossterm.compose.settings

import androidx.compose.runtime.*
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.rememberWindowState

/**
 * Settings window (non-modal, allows terminal interaction).
 *
 * @param visible Whether the window is visible
 * @param onDismiss Called when the window should be closed
 * @param onRestartApp Called when app should restart (for settings that require restart)
 */
@Composable
fun SettingsWindow(
    visible: Boolean,
    onDismiss: () -> Unit,
    onRestartApp: (() -> Unit)? = null
) {
    if (!visible) return

    val settingsManager = remember { SettingsManager.instance }
    val currentSettings by settingsManager.settings.collectAsState()

    Window(
        onCloseRequest = onDismiss,
        title = "BossTerm Settings",
        resizable = false,
        alwaysOnTop = false,
        state = rememberWindowState(
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
            },
            onRestartApp = onRestartApp
        )
    }
}
