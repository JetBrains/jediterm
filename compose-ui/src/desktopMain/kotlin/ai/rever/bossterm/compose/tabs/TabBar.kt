package ai.rever.bossterm.compose.tabs

import ai.rever.bossterm.compose.features.ContextMenuController
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Tab bar component for multiple terminal sessions.
 *
 * Displays a horizontal strip of tabs with:
 * - Tab titles (with ellipsis for long names)
 * - Close button per tab (X)
 * - New tab button (+)
 * - Active tab highlighting
 * - Right-click context menu with Close/Move to New Window
 *
 * Styling matches the Material 3 design of the search bar for visual consistency.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun TabBar(
    tabs: List<TerminalTab>,
    activeTabIndex: Int,
    onTabSelected: (Int) -> Unit,
    onTabClosed: (Int) -> Unit,
    onNewTab: () -> Unit,
    onTabMoveToNewWindow: (Int) -> Unit = {},
    modifier: Modifier = Modifier
) {
    // Context menu controller for tab right-click menu
    val contextMenuController = remember { ContextMenuController() }
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp),
        color = Color(0xFF1E1E1E),
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Tab strip (scrollable if many tabs)
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                tabs.forEachIndexed { index, tab ->
                    TabItem(
                        title = tab.title.value,
                        isActive = index == activeTabIndex,
                        onSelected = { onTabSelected(index) },
                        onClose = { onTabClosed(index) },
                        onContextMenu = {
                            val items = listOf(
                                ContextMenuController.MenuItem(
                                    id = "close_tab",
                                    label = "Close Tab",
                                    enabled = true,
                                    action = { onTabClosed(index) }
                                ),
                                ContextMenuController.MenuItem(
                                    id = "move_to_new_window",
                                    label = "Move to New Window",
                                    enabled = true,
                                    action = { onTabMoveToNewWindow(index) }
                                )
                            )
                            contextMenuController.showMenu(0f, 0f, items)
                        },
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }
            }

            // New tab button
            IconButton(
                onClick = onNewTab,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "New Tab",
                    tint = Color.White
                )
            }
        }
    }
}

/**
 * Individual tab item component.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun TabItem(
    title: String,
    isActive: Boolean,
    onSelected: () -> Unit,
    onClose: () -> Unit,
    onContextMenu: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onSelected,
        modifier = modifier
            .height(36.dp)
            .widthIn(min = 80.dp, max = 200.dp)
            .onPointerEvent(PointerEventType.Press) { event ->
                // Handle right-click for context menu
                if (event.button == PointerButton.Secondary) {
                    onContextMenu()
                }
            },
        shape = RoundedCornerShape(6.dp),
        color = if (isActive) Color(0xFF2B2B2B) else Color(0xFF1E1E1E),
        border = if (isActive) {
            androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF4A90E2))
        } else {
            androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF404040))
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Tab title - use Monospace font (Menlo on macOS) for monochrome symbols
            Text(
                text = title,
                color = if (isActive) Color.White else Color(0xFFB0B0B0),
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,  // Menlo has monochrome Dingbats (✳, ❯, etc.)
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            // Close button
            IconButton(
                onClick = onClose,
                modifier = Modifier.size(20.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close Tab",
                    tint = if (isActive) Color(0xFFB0B0B0) else Color(0xFF707070),
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

/**
 * Preview for development (not rendered in production).
 */
@Composable
private fun TabBarPreview() {
    // This is just for development visualization
    // Not used in the actual app
}
