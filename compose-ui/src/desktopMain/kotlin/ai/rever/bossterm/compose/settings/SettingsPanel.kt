package ai.rever.bossterm.compose.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
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
import ai.rever.bossterm.compose.settings.sections.*

private val NavRailWidth = 180.dp

/**
 * Main settings panel with navigation and content area.
 */
@Composable
fun SettingsPanel(
    settings: TerminalSettings,
    onSettingsChange: (TerminalSettings) -> Unit,
    onResetToDefaults: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedCategory by remember { mutableStateOf(SettingsCategory.default) }
    var showResetConfirmation by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundColor)
    ) {
        // Left navigation rail
        NavigationRail(
            selectedCategory = selectedCategory,
            onCategorySelected = { selectedCategory = it },
            modifier = Modifier
                .width(NavRailWidth)
                .fillMaxHeight()
        )

        // Divider
        Box(
            modifier = Modifier
                .width(1.dp)
                .fillMaxHeight()
                .background(BorderColor)
        )

        // Right content area
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        ) {
            // Content
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                SettingsContent(
                    category = selectedCategory,
                    settings = settings,
                    onSettingsChange = onSettingsChange,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Footer with reset button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SurfaceColor)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Changes are saved automatically",
                        color = TextMuted,
                        fontSize = 12.sp
                    )
                    TextButton(
                        onClick = { showResetConfirmation = true },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = TextSecondary
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Reset to Defaults", fontSize = 13.sp)
                    }
                }
            }
        }
    }

    // Reset confirmation dialog
    if (showResetConfirmation) {
        AlertDialog(
            onDismissRequest = { showResetConfirmation = false },
            title = {
                Text(
                    text = "Reset Settings?",
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold
                )
            },
            text = {
                Text(
                    text = "This will reset all settings to their default values. This action cannot be undone.",
                    color = TextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onResetToDefaults()
                        showResetConfirmation = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = Color(0xFFE04040)
                    )
                ) {
                    Text("Reset", color = TextPrimary)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showResetConfirmation = false }
                ) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            backgroundColor = SurfaceColor,
            contentColor = TextPrimary
        )
    }
}

/**
 * Navigation rail with category icons and labels.
 */
@Composable
private fun NavigationRail(
    selectedCategory: SettingsCategory,
    onCategorySelected: (SettingsCategory) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(SurfaceColor)
            .padding(vertical = 8.dp)
    ) {
        SettingsCategory.entries.forEach { category ->
            val isSelected = category == selectedCategory
            NavigationRailItem(
                category = category,
                isSelected = isSelected,
                onClick = { onCategorySelected(category) }
            )
        }
    }
}

/**
 * Single navigation rail item.
 */
@Composable
private fun NavigationRailItem(
    category: SettingsCategory,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(if (isSelected) AccentColor.copy(alpha = 0.15f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Selection indicator
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(20.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(if (isSelected) AccentColor else Color.Transparent)
        )

        Icon(
            imageVector = category.icon,
            contentDescription = category.displayName,
            tint = if (isSelected) AccentColor else TextSecondary,
            modifier = Modifier.size(18.dp)
        )

        Text(
            text = category.displayName,
            color = if (isSelected) AccentColor else TextPrimary,
            fontSize = 13.sp,
            fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal
        )
    }
}

/**
 * Content area displaying the selected category's settings.
 */
@Composable
private fun SettingsContent(
    category: SettingsCategory,
    settings: TerminalSettings,
    onSettingsChange: (TerminalSettings) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .verticalScroll(scrollState)
            .padding(20.dp)
    ) {
        // Category header
        Text(
            text = category.displayName,
            color = TextPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Text(
            text = category.description,
            color = TextMuted,
            fontSize = 13.sp,
            modifier = Modifier.padding(bottom = 20.dp)
        )

        // Category-specific content
        when (category) {
            SettingsCategory.VISUAL -> VisualSettingsSection(
                settings = settings,
                onSettingsChange = onSettingsChange
            )
            SettingsCategory.BEHAVIOR -> BehaviorSettingsSection(
                settings = settings,
                onSettingsChange = onSettingsChange
            )
            SettingsCategory.SCROLLBAR -> ScrollbarSettingsSection(
                settings = settings,
                onSettingsChange = onSettingsChange
            )
            SettingsCategory.PERFORMANCE -> PerformanceSettingsSection(
                settings = settings,
                onSettingsChange = onSettingsChange
            )
            SettingsCategory.EMULATION -> TerminalEmulationSection(
                settings = settings,
                onSettingsChange = onSettingsChange
            )
            SettingsCategory.SEARCH -> SearchSettingsSection(
                settings = settings,
                onSettingsChange = onSettingsChange
            )
            SettingsCategory.HYPERLINKS -> HyperlinkSettingsSection(
                settings = settings,
                onSettingsChange = onSettingsChange
            )
            SettingsCategory.TYPE_AHEAD -> TypeAheadSettingsSection(
                settings = settings,
                onSettingsChange = onSettingsChange
            )
            SettingsCategory.DEBUG -> DebugSettingsSection(
                settings = settings,
                onSettingsChange = onSettingsChange
            )
            SettingsCategory.LOGGING -> LoggingSettingsSection(
                settings = settings,
                onSettingsChange = onSettingsChange
            )
            SettingsCategory.NOTIFICATIONS -> NotificationSettingsSection(
                settings = settings,
                onSettingsChange = onSettingsChange
            )
            SettingsCategory.SPLITS -> SplitsSettingsSection(
                settings = settings,
                onSettingsChange = onSettingsChange
            )
        }
    }
}
