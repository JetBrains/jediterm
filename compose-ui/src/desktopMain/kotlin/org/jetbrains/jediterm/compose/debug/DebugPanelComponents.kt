package org.jetbrains.jediterm.compose.debug

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Segmented button control for selecting buffer type (SCREEN/STYLE/HISTORY).
 */
@Composable
fun BufferTypeSelector(
    selectedType: BufferType,
    onSelectType: (BufferType) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .background(Color(0xFF2A2A2A), RoundedCornerShape(6.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        BufferType.values().forEach { type ->
            Button(
                onClick = { onSelectType(type) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (selectedType == type) Color(0xFF4A90E2) else Color.Transparent,
                    contentColor = if (selectedType == type) Color.White else Color.Gray
                ),
                shape = RoundedCornerShape(4.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                modifier = Modifier.height(36.dp)
            ) {
                Text(
                    text = type.name,
                    fontSize = 13.sp,
                    fontWeight = if (selectedType == type) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}

/**
 * Time slider for scrubbing through terminal history snapshots.
 */
@Composable
fun TimeSlider(
    currentIndex: Int,
    totalStates: Int,
    onIndexChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    if (totalStates == 0) {
        Text(
            text = "No snapshots captured yet",
            color = Color.Gray,
            fontSize = 13.sp,
            modifier = modifier.padding(8.dp)
        )
        return
    }

    Column(modifier = modifier) {
        // Slider label
        Text(
            text = "Snapshot: ${currentIndex + 1} / $totalStates",
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(bottom = 4.dp)
        )

        // Slider with controls
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Previous button
            IconButton(
                onClick = { if (currentIndex > 0) onIndexChange(currentIndex - 1) },
                enabled = currentIndex > 0,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.KeyboardArrowLeft,
                    contentDescription = "Previous snapshot",
                    tint = if (currentIndex > 0) Color.White else Color.Gray
                )
            }

            // Slider
            Slider(
                value = currentIndex.toFloat(),
                onValueChange = { onIndexChange(it.toInt()) },
                valueRange = 0f..(totalStates - 1).toFloat(),
                steps = if (totalStates > 2) totalStates - 2 else 0,
                colors = SliderDefaults.colors(
                    thumbColor = Color(0xFF4A90E2),
                    activeTrackColor = Color(0xFF4A90E2),
                    inactiveTrackColor = Color(0xFF3A3A3A)
                ),
                modifier = Modifier.weight(1f)
            )

            // Next button
            IconButton(
                onClick = { if (currentIndex < totalStates - 1) onIndexChange(currentIndex + 1) },
                enabled = currentIndex < totalStates - 1,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.KeyboardArrowRight,
                    contentDescription = "Next snapshot",
                    tint = if (currentIndex < totalStates - 1) Color.White else Color.Gray
                )
            }
        }
    }
}

/**
 * Monospace text view for displaying terminal buffer content.
 */
@Composable
fun BufferView(
    content: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(Color(0xFF1A1A1A), RoundedCornerShape(6.dp))
            .padding(12.dp)
    ) {
        Text(
            text = content.ifEmpty { "No data available" },
            color = if (content.isEmpty()) Color.Gray else Color(0xFFE0E0E0),
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .horizontalScroll(rememberScrollState())
        )
    }
}

/**
 * Control sequence visualization view with color-coded escape sequences.
 */
@Composable
fun ControlSequenceView(
    chunks: List<DebugChunk>,
    settings: VisualizationSettings,
    modifier: Modifier = Modifier
) {
    val visualized = remember(chunks, settings) {
        if (chunks.isEmpty()) {
            "No data captured"
        } else {
            DebugControlSequenceVisualizer.visualize(chunks, settings)
        }
    }

    Box(
        modifier = modifier
            .background(Color(0xFF1A1A1A), RoundedCornerShape(6.dp))
            .padding(12.dp)
    ) {
        Text(
            text = visualized,
            color = if (chunks.isEmpty()) Color.Gray else Color(0xFFE0E0E0),
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            lineHeight = 16.sp,
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .horizontalScroll(rememberScrollState())
        )
    }
}

/**
 * Checkbox controls for visualization settings.
 */
@Composable
fun VisualizationControls(
    settings: VisualizationSettings,
    onSettingsChange: (VisualizationSettings) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .background(Color(0xFF2A2A2A), RoundedCornerShape(6.dp))
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Show Chunk IDs
        CheckboxWithLabel(
            checked = settings.showChunkIds,
            onCheckedChange = { onSettingsChange(settings.copy(showChunkIds = it)) },
            label = "Chunk IDs"
        )

        // Show Invisible Characters
        CheckboxWithLabel(
            checked = settings.showInvisibleChars,
            onCheckedChange = { onSettingsChange(settings.copy(showInvisibleChars = it)) },
            label = "Invisible Chars"
        )

        // Wrap Lines
        CheckboxWithLabel(
            checked = settings.wrapLines,
            onCheckedChange = { onSettingsChange(settings.copy(wrapLines = it)) },
            label = "Wrap Lines"
        )

        // Color Code Sequences
        CheckboxWithLabel(
            checked = settings.colorCodeSequences,
            onCheckedChange = { onSettingsChange(settings.copy(colorCodeSequences = it)) },
            label = "Color Code"
        )
    }
}

/**
 * Checkbox with text label helper component.
 */
@Composable
private fun CheckboxWithLabel(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    label: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = CheckboxDefaults.colors(
                checkedColor = Color(0xFF4A90E2),
                uncheckedColor = Color.Gray,
                checkmarkColor = Color.White
            ),
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = label,
            color = Color.White,
            fontSize = 13.sp
        )
    }
}

/**
 * Statistics display component showing debug collector stats.
 */
@Composable
fun DebugStatsView(
    stats: DebugStats,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = Color(0xFF2A2A2A),
        shape = RoundedCornerShape(6.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "Debug Statistics",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )

            Divider(color = Color(0xFF3A3A3A), thickness = 1.dp)

            Text(
                text = stats.toDisplayString(),
                color = Color(0xFFE0E0E0),
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                lineHeight = 18.sp
            )
        }
    }
}
