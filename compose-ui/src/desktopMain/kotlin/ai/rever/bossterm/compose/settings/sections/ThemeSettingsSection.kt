package ai.rever.bossterm.compose.settings.sections

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.rever.bossterm.compose.settings.SettingsTheme.AccentColor
import ai.rever.bossterm.compose.settings.SettingsTheme.BackgroundColor
import ai.rever.bossterm.compose.settings.SettingsTheme.BorderColor
import ai.rever.bossterm.compose.settings.SettingsTheme.SurfaceColor
import ai.rever.bossterm.compose.settings.SettingsTheme.TextMuted
import ai.rever.bossterm.compose.settings.SettingsTheme.TextPrimary
import ai.rever.bossterm.compose.settings.SettingsTheme.TextSecondary
import ai.rever.bossterm.compose.settings.TerminalSettings
import ai.rever.bossterm.compose.settings.components.ColorPickerDialog
import ai.rever.bossterm.compose.settings.components.SettingsSection
import ai.rever.bossterm.compose.settings.theme.BuiltinThemes
import ai.rever.bossterm.compose.settings.theme.Theme
import ai.rever.bossterm.compose.settings.theme.ThemeManager

/**
 * Theme settings section: theme selection, ANSI palette editing.
 */
@Composable
fun ThemeSettingsSection(
    settings: TerminalSettings,
    onSettingsChange: (TerminalSettings) -> Unit,
    modifier: Modifier = Modifier
) {
    val themeManager = remember { ThemeManager.instance }
    val currentTheme by themeManager.currentTheme.collectAsState()
    val customThemes by themeManager.customThemes.collectAsState()

    var showCreateDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmation by remember { mutableStateOf<Theme?>(null) }
    var editingColorIndex by remember { mutableStateOf<Int?>(null) }
    var editingTerminalColor by remember { mutableStateOf<TerminalColorType?>(null) }

    Column(modifier = modifier) {
        // Theme selector
        SettingsSection(title = "Select Theme") {
            ThemeGrid(
                themes = themeManager.getAllThemes(),
                selectedThemeId = currentTheme.id,
                onThemeSelected = { theme ->
                    themeManager.applyTheme(theme)
                }
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Current theme info and actions
        SettingsSection(title = "Current Theme: ${currentTheme.name}") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Create custom theme button
                Button(
                    onClick = { showCreateDialog = true },
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = AccentColor
                    ),
                    modifier = Modifier.height(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Save as Custom", fontSize = 13.sp, color = TextPrimary)
                }

                // Delete button (only for custom themes)
                if (!currentTheme.isBuiltin) {
                    Button(
                        onClick = { showDeleteConfirmation = currentTheme },
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = Color(0xFFE04040)
                        ),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Delete", fontSize = 13.sp, color = TextPrimary)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Terminal colors editor
        SettingsSection(title = "Terminal Colors") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TerminalColorRow(
                    label = "Foreground",
                    color = currentTheme.foregroundColor,
                    onClick = { editingTerminalColor = TerminalColorType.FOREGROUND }
                )
                TerminalColorRow(
                    label = "Background",
                    color = currentTheme.backgroundColorValue,
                    onClick = { editingTerminalColor = TerminalColorType.BACKGROUND }
                )
                TerminalColorRow(
                    label = "Cursor",
                    color = currentTheme.cursorColor,
                    onClick = { editingTerminalColor = TerminalColorType.CURSOR }
                )
                TerminalColorRow(
                    label = "Selection",
                    color = currentTheme.selectionColor,
                    onClick = { editingTerminalColor = TerminalColorType.SELECTION }
                )
                TerminalColorRow(
                    label = "Search Match",
                    color = currentTheme.searchMatchColor,
                    onClick = { editingTerminalColor = TerminalColorType.SEARCH_MATCH }
                )
                TerminalColorRow(
                    label = "Hyperlink",
                    color = currentTheme.hyperlinkColor,
                    onClick = { editingTerminalColor = TerminalColorType.HYPERLINK }
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ANSI palette editor
        SettingsSection(title = "ANSI Color Palette") {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Normal colors (0-7)
                Text(
                    text = "Normal",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    for (i in 0..7) {
                        AnsiColorSwatch(
                            color = currentTheme.getAnsiColor(i),
                            label = Theme.ANSI_COLOR_NAMES[i],
                            onClick = { editingColorIndex = i },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Bright colors (8-15)
                Text(
                    text = "Bright",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    for (i in 8..15) {
                        AnsiColorSwatch(
                            color = currentTheme.getAnsiColor(i),
                            label = Theme.ANSI_COLOR_NAMES[i],
                            onClick = { editingColorIndex = i },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }

    // Create custom theme dialog
    if (showCreateDialog) {
        CreateThemeDialog(
            basedOn = currentTheme,
            onDismiss = { showCreateDialog = false },
            onCreate = { name ->
                themeManager.createCustomTheme(currentTheme, name)
                showCreateDialog = false
            }
        )
    }

    // Delete confirmation dialog
    showDeleteConfirmation?.let { theme ->
        DeleteThemeDialog(
            theme = theme,
            onDismiss = { showDeleteConfirmation = null },
            onConfirm = {
                themeManager.deleteCustomTheme(theme.id)
                showDeleteConfirmation = null
            }
        )
    }

    // ANSI color picker
    editingColorIndex?.let { index ->
        ColorPickerDialog(
            initialColor = currentTheme.getAnsiColor(index),
            onColorSelected = { newColor ->
                val newHex = Theme.colorToHex(newColor)
                val updatedTheme = currentTheme.withAnsiColor(index, newHex)
                if (currentTheme.isBuiltin) {
                    // For built-in themes, create a copy
                    val customTheme = themeManager.createCustomTheme(updatedTheme, "${currentTheme.name} Custom")
                    themeManager.applyTheme(customTheme)
                } else {
                    themeManager.updateCustomTheme(updatedTheme)
                }
                editingColorIndex = null
            },
            onDismiss = { editingColorIndex = null }
        )
    }

    // Terminal color picker
    editingTerminalColor?.let { colorType ->
        val currentColor = when (colorType) {
            TerminalColorType.FOREGROUND -> currentTheme.foregroundColor
            TerminalColorType.BACKGROUND -> currentTheme.backgroundColorValue
            TerminalColorType.CURSOR -> currentTheme.cursorColor
            TerminalColorType.SELECTION -> currentTheme.selectionColor
            TerminalColorType.SEARCH_MATCH -> currentTheme.searchMatchColor
            TerminalColorType.HYPERLINK -> currentTheme.hyperlinkColor
        }
        ColorPickerDialog(
            initialColor = currentColor,
            onColorSelected = { newColor ->
                val newHex = Theme.colorToHex(newColor)
                val updatedTheme = when (colorType) {
                    TerminalColorType.FOREGROUND -> currentTheme.copy(foreground = newHex)
                    TerminalColorType.BACKGROUND -> currentTheme.copy(background = newHex)
                    TerminalColorType.CURSOR -> currentTheme.copy(cursor = newHex)
                    TerminalColorType.SELECTION -> currentTheme.copy(selection = newHex)
                    TerminalColorType.SEARCH_MATCH -> currentTheme.copy(searchMatch = newHex)
                    TerminalColorType.HYPERLINK -> currentTheme.copy(hyperlink = newHex)
                }
                if (currentTheme.isBuiltin) {
                    val customTheme = themeManager.createCustomTheme(updatedTheme, "${currentTheme.name} Custom")
                    themeManager.applyTheme(customTheme)
                } else {
                    themeManager.updateCustomTheme(updatedTheme)
                }
                editingTerminalColor = null
            },
            onDismiss = { editingTerminalColor = null }
        )
    }
}

/**
 * Terminal color types for editing.
 */
private enum class TerminalColorType {
    FOREGROUND, BACKGROUND, CURSOR, SELECTION, SEARCH_MATCH, HYPERLINK
}

/**
 * Grid of theme preview cards.
 */
@Composable
private fun ThemeGrid(
    themes: List<Theme>,
    selectedThemeId: String,
    onThemeSelected: (Theme) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 120.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.heightIn(max = 300.dp)
    ) {
        items(themes) { theme ->
            ThemePreviewCard(
                theme = theme,
                isSelected = theme.id == selectedThemeId,
                onClick = { onThemeSelected(theme) }
            )
        }
    }
}

/**
 * Theme preview card with mini terminal preview.
 */
@Composable
private fun ThemePreviewCard(
    theme: Theme,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) AccentColor else BorderColor,
                shape = RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onClick)
    ) {
        Column {
            // Mini terminal preview
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .background(theme.backgroundColorValue)
                    .padding(6.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    // Sample text lines
                    Text(
                        text = "$ ls -la",
                        color = theme.foregroundColor,
                        fontSize = 8.sp
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("drwxr", color = theme.getAnsiColor(4), fontSize = 7.sp)
                        Text("file.txt", color = theme.foregroundColor, fontSize = 7.sp)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("-rw-r", color = theme.getAnsiColor(2), fontSize = 7.sp)
                        Text("README", color = theme.getAnsiColor(6), fontSize = 7.sp)
                    }
                }
            }

            // Theme name
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SurfaceColor)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = theme.name,
                        color = TextPrimary,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal
                    )
                    if (!theme.isBuiltin) {
                        Text(
                            text = "Custom",
                            color = TextMuted,
                            fontSize = 9.sp
                        )
                    }
                }
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Selected",
                        tint = AccentColor,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

/**
 * Row for editing a terminal color.
 */
@Composable
private fun TerminalColorRow(
    label: String,
    color: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(SurfaceColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = TextPrimary,
            fontSize = 13.sp
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = Theme.colorToHex(color),
                color = TextSecondary,
                fontSize = 11.sp
            )
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(color)
                    .border(1.dp, BorderColor, RoundedCornerShape(4.dp))
            )
        }
    }
}

/**
 * ANSI color swatch with tooltip.
 */
@Composable
private fun AnsiColorSwatch(
    color: Color,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(color)
                .border(1.dp, BorderColor, RoundedCornerShape(4.dp))
                .clickable(onClick = onClick)
        )
        Text(
            text = label.split(" ").last().take(3),
            color = TextMuted,
            fontSize = 8.sp,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

/**
 * Dialog for creating a new custom theme.
 */
@Composable
private fun CreateThemeDialog(
    basedOn: Theme,
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit
) {
    var themeName by remember { mutableStateOf("${basedOn.name} Copy") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Create Custom Theme",
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Column {
                Text(
                    text = "Based on: ${basedOn.name}",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                OutlinedTextField(
                    value = themeName,
                    onValueChange = { themeName = it },
                    label = { Text("Theme Name") },
                    singleLine = true,
                    colors = TextFieldDefaults.outlinedTextFieldColors(
                        textColor = TextPrimary,
                        focusedBorderColor = AccentColor,
                        unfocusedBorderColor = BorderColor,
                        cursorColor = AccentColor,
                        focusedLabelColor = AccentColor,
                        unfocusedLabelColor = TextSecondary
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onCreate(themeName) },
                enabled = themeName.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = AccentColor
                )
            ) {
                Text("Create", color = TextPrimary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        },
        backgroundColor = BackgroundColor,
        contentColor = TextPrimary
    )
}

/**
 * Dialog for confirming theme deletion.
 */
@Composable
private fun DeleteThemeDialog(
    theme: Theme,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Delete Theme?",
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Text(
                text = "Are you sure you want to delete \"${theme.name}\"? This action cannot be undone.",
                color = TextSecondary
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = Color(0xFFE04040)
                )
            ) {
                Text("Delete", color = TextPrimary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        },
        backgroundColor = BackgroundColor,
        contentColor = TextPrimary
    )
}
