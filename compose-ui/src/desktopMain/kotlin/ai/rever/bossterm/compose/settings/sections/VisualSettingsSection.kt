package ai.rever.bossterm.compose.settings.sections

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.AlertDialog
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.rever.bossterm.compose.settings.TerminalSettings
import ai.rever.bossterm.compose.settings.SettingsTheme.SurfaceColor
import ai.rever.bossterm.compose.settings.SettingsTheme.TextPrimary
import ai.rever.bossterm.compose.settings.SettingsTheme.TextSecondary
import ai.rever.bossterm.compose.settings.components.*

/**
 * Visual settings section: font, text rendering, and transparency.
 * Note: Color settings are in the Themes section.
 */
@Composable
fun VisualSettingsSection(
    settings: TerminalSettings,
    onSettingsChange: (TerminalSettings) -> Unit,
    onRestartApp: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var showRestartDialog by remember { mutableStateOf(false) }
    var pendingNativeTitleBarValue by remember { mutableStateOf(false) }

    Column(modifier = modifier) {
        // Font Settings
        SettingsSection(title = "Font") {
            SettingsSlider(
                label = "Font Size",
                value = settings.fontSize,
                onValueChange = { onSettingsChange(settings.copy(fontSize = it)) },
                valueRange = 8f..24f,
                steps = 15,
                valueDisplay = { "${it.toInt()} sp" }
            )

            SettingsSlider(
                label = "Line Spacing",
                value = settings.lineSpacing,
                onValueChange = { onSettingsChange(settings.copy(lineSpacing = it)) },
                valueRange = 0.8f..2.0f,
                valueDisplay = { "%.2fx".format(it) },
                description = "Multiplier for line height (1.0 = normal)"
            )

            SettingsToggle(
                label = "Disable Line Spacing in Fullscreen Apps",
                checked = settings.disableLineSpacingInAlternateBuffer,
                onCheckedChange = { onSettingsChange(settings.copy(disableLineSpacingInAlternateBuffer = it)) },
                description = "Remove extra spacing in vim, htop, less"
            )

            SettingsToggle(
                label = "Fill Background in Line Spacing",
                checked = settings.fillBackgroundInLineSpacing,
                onCheckedChange = { onSettingsChange(settings.copy(fillBackgroundInLineSpacing = it)) },
                description = "Extend background colors into line spacing area"
            )

            SettingsToggle(
                label = "Use Antialiasing",
                checked = settings.useAntialiasing,
                onCheckedChange = { onSettingsChange(settings.copy(useAntialiasing = it)) },
                description = "Smooth text rendering"
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Window Style Settings (before Transparency since it affects transparency availability)
        SettingsSection(title = "Window Style") {
            SettingsToggle(
                label = "Use Native Title Bar",
                checked = settings.useNativeTitleBar,
                onCheckedChange = { newValue ->
                    if (onRestartApp != null) {
                        pendingNativeTitleBarValue = newValue
                        showRestartDialog = true
                    } else {
                        onSettingsChange(settings.copy(useNativeTitleBar = newValue))
                    }
                },
                description = if (settings.useNativeTitleBar) {
                    "Native macOS title bar with proper fullscreen (no transparency)"
                } else {
                    "Custom title bar with transparency support"
                }
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Transparency Settings (only available with custom title bar)
        SettingsSection(title = "Transparency") {
            if (settings.useNativeTitleBar) {
                Text(
                    text = "Transparency requires custom title bar. Disable 'Use Native Title Bar' above to enable transparency.",
                    color = Color.Gray,
                    fontSize = 12.sp
                )
            } else {
                SettingsSlider(
                    label = "Background Opacity",
                    value = settings.backgroundOpacity,
                    onValueChange = { onSettingsChange(settings.copy(backgroundOpacity = it)) },
                    valueRange = 0.1f..1.0f,
                    steps = 17,
                    valueDisplay = { "${(it * 100).toInt()}%" },
                    description = "Make the terminal background transparent to see through to desktop"
                )

                SettingsToggle(
                    label = "Enable Blur Effect",
                    checked = settings.windowBlur,
                    onCheckedChange = { newValue ->
                        onSettingsChange(settings.copy(windowBlur = newValue))
                    },
                    description = "Blurs background image or shows frosted glass effect"
                )

                if (settings.windowBlur) {
                    SettingsSlider(
                        label = "Blur Radius",
                        value = settings.blurRadius,
                        onValueChange = { onSettingsChange(settings.copy(blurRadius = it)) },
                        valueRange = 5f..50f,
                        steps = 8,
                        valueDisplay = { "${it.toInt()} dp" },
                        description = "Intensity of the blur effect"
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Background Image Settings
        SettingsSection(title = "Background Image") {
            SettingsFilePicker(
                label = "Image Path",
                value = settings.backgroundImagePath,
                onValueChange = { onSettingsChange(settings.copy(backgroundImagePath = it)) },
                description = "Select a PNG or JPG image for the background",
                fileExtensions = listOf("png", "jpg", "jpeg")
            )

            if (settings.backgroundImagePath.isNotEmpty()) {
                SettingsSlider(
                    label = "Image Opacity",
                    value = settings.backgroundImageOpacity,
                    onValueChange = { onSettingsChange(settings.copy(backgroundImageOpacity = it)) },
                    valueRange = 0.1f..1.0f,
                    steps = 17,
                    valueDisplay = { "${(it * 100).toInt()}%" },
                    description = "How visible the background image is"
                )
            }
        }
    }

    // Restart confirmation dialog
    if (showRestartDialog) {
        AlertDialog(
            onDismissRequest = { showRestartDialog = false },
            title = {
                Text(
                    text = "Restart Required",
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold
                )
            },
            text = {
                Text(
                    text = "Changing the title bar style requires restarting the application. Do you want to restart now?",
                    color = TextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onSettingsChange(settings.copy(useNativeTitleBar = pendingNativeTitleBarValue))
                        showRestartDialog = false
                        onRestartApp?.invoke()
                    },
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = Color(0xFF4A90E2)
                    )
                ) {
                    Text("Restart", color = TextPrimary)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showRestartDialog = false }
                ) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            backgroundColor = SurfaceColor,
            contentColor = TextPrimary
        )
    }

}
