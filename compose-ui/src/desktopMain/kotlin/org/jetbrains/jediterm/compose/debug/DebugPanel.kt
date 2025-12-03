package org.jetbrains.jediterm.compose.debug

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jediterm.terminal.model.TerminalTextBuffer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Debug panel UI for visualizing terminal I/O data and state.
 *
 * This panel provides time-travel debugging capabilities by allowing users to:
 * - Scrub through captured terminal state snapshots
 * - View terminal buffer content (screen, style, history)
 * - Visualize control sequences in human-readable format
 * - Inspect debug statistics
 *
 * The panel appears as a bottom overlay (600px height) with Material 3 dark theme,
 * matching the existing search bar design.
 *
 * @param visible Whether the panel should be displayed
 * @param collector Debug data collector with captured chunks and snapshots
 * @param onClose Callback when user closes the panel
 * @param modifier Modifier for the panel container
 */
@Composable
fun DebugPanel(
    visible: Boolean,
    collector: DebugDataCollector?,
    textBuffer: TerminalTextBuffer?,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!visible || collector == null) return

    // State management
    var selectedBufferType by remember { mutableStateOf(BufferType.SCREEN) }
    var currentSnapshotIndex by remember { mutableStateOf(0) }
    var visualizationSettings by remember { mutableStateOf(VisualizationSettings()) }
    var showStats by remember { mutableStateOf(false) }

    // Auto-refresh: Update snapshot list periodically
    val snapshots = remember { mutableStateOf(collector.getSnapshots()) }
    val chunks = remember { mutableStateOf(collector.getDebugChunks()) }
    val stats = remember { mutableStateOf(collector.getStats()) }
    val snapshotBuilderStats = remember { mutableStateOf(textBuffer?.getSnapshotBuilderStats()) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)  // Refresh every second
            snapshots.value = collector.getSnapshots()
            chunks.value = collector.getDebugChunks()
            stats.value = collector.getStats()
            snapshotBuilderStats.value = textBuffer?.getSnapshotBuilderStats()
        }
    }

    // Get current snapshot
    val currentSnapshot = snapshots.value.getOrNull(currentSnapshotIndex)

    // Get buffer content based on selected type
    val bufferContent = remember(currentSnapshot, selectedBufferType) {
        when {
            currentSnapshot == null -> "No snapshot available"
            selectedBufferType == BufferType.SCREEN -> currentSnapshot.screenLines
            selectedBufferType == BufferType.STYLE -> currentSnapshot.styleLines
            selectedBufferType == BufferType.HISTORY -> currentSnapshot.historyLines
            else -> ""
        }
    }

    // Get relevant chunks for current snapshot
    val relevantChunks = remember(currentSnapshot, chunks.value) {
        if (currentSnapshot == null) emptyList()
        else {
            // Get chunks around the snapshot's chunk index (±10 chunks)
            val startIndex = (currentSnapshot.chunkIndex - 10).coerceAtLeast(0)
            val endIndex = currentSnapshot.chunkIndex
            collector.getChunksInRange(startIndex, endIndex)
        }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(600.dp)
            .padding(8.dp),
        color = Color(0xFF1E1E1E),
        shape = RoundedCornerShape(8.dp),
        shadowElevation = 4.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "🛠️ Debug Tools - Terminal Inspector",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Stats toggle button
                    IconButton(
                        onClick = { showStats = !showStats },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Info,
                            contentDescription = if (showStats) "Hide stats" else "Show stats",
                            tint = if (showStats) Color(0xFF4A90E2) else Color.Gray
                        )
                    }

                    // Close button
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Close debug panel",
                            tint = Color.White
                        )
                    }
                }
            }

            Divider(color = Color(0xFF3A3A3A), thickness = 1.dp)

            // Buffer type selector
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                BufferTypeSelector(
                    selectedType = selectedBufferType,
                    onSelectType = { selectedBufferType = it }
                )

                if (currentSnapshot != null) {
                    Text(
                        text = "Cursor: (${currentSnapshot.cursorY}, ${currentSnapshot.cursorX}) | " +
                                "Chunk: ${currentSnapshot.chunkIndex}",
                        color = Color.Gray,
                        fontSize = 12.sp
                    )
                }
            }

            // Time slider
            TimeSlider(
                currentIndex = currentSnapshotIndex,
                totalStates = snapshots.value.size,
                onIndexChange = { currentSnapshotIndex = it.coerceIn(0, snapshots.value.size - 1) }
            )

            // Stats view (collapsible)
            if (showStats) {
                DebugStatsView(
                    stats = stats.value,
                    snapshotBuilderStats = snapshotBuilderStats.value
                )
            }

            // Split view: Buffer content | Control sequences
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Left: Buffer content
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Buffer Content (${selectedBufferType.name})",
                        color = Color.Gray,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )

                    BufferView(
                        content = bufferContent,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Right: Control sequence visualization
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Control Sequences (Last 10 chunks)",
                        color = Color.Gray,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )

                    ControlSequenceView(
                        chunks = relevantChunks,
                        settings = visualizationSettings,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            // Visualization settings
            VisualizationControls(
                settings = visualizationSettings,
                onSettingsChange = { visualizationSettings = it }
            )
        }
    }
}
