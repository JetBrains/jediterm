package org.jetbrains.jediterm.compose.features

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Divider
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties

/**
 * Controller for managing context menu state and actions.
 * Based on test expectations from ContextMenuTest.kt.
 */
class ContextMenuController {
    /**
     * Menu item data class
     */
    data class MenuItem(
        val id: String,
        val label: String,
        val enabled: Boolean,
        val action: () -> Unit
    )

    /**
     * Menu state data class
     */
    data class MenuState(
        val isVisible: Boolean = false,
        val x: Float = 0f,
        val y: Float = 0f,
        val items: List<MenuItem> = emptyList()
    )

    private val _menuState = mutableStateOf(MenuState())
    val menuState: State<MenuState> = _menuState

    /**
     * Show context menu at specified position
     */
    fun showMenu(x: Float, y: Float, items: List<MenuItem>) {
        _menuState.value = MenuState(
            isVisible = true,
            x = x,
            y = y,
            items = items
        )
    }

    /**
     * Hide context menu
     */
    fun hideMenu() {
        _menuState.value = MenuState()
    }

    /**
     * Execute menu item by ID (if enabled)
     */
    fun executeItem(id: String) {
        val item = _menuState.value.items.find { it.id == id }
        if (item != null && item.enabled) {
            item.action()
            hideMenu()
        }
    }
}

/**
 * Create terminal-specific context menu items
 */
fun createTerminalContextMenuItems(
    hasSelection: Boolean,
    onCopy: () -> Unit,
    onPaste: () -> Unit,
    onSelectAll: () -> Unit,
    onClearScreen: () -> Unit,
    onFind: () -> Unit
): List<ContextMenuController.MenuItem> {
    return listOf(
        ContextMenuController.MenuItem(
            id = "copy",
            label = "Copy",
            enabled = hasSelection,
            action = onCopy
        ),
        ContextMenuController.MenuItem(
            id = "paste",
            label = "Paste",
            enabled = true,
            action = onPaste
        ),
        ContextMenuController.MenuItem(
            id = "select_all",
            label = "Select All",
            enabled = true,
            action = onSelectAll
        ),
        ContextMenuController.MenuItem(
            id = "find",
            label = "Find...",
            enabled = true,
            action = onFind
        ),
        ContextMenuController.MenuItem(
            id = "clear",
            label = "Clear Screen",
            enabled = true,
            action = onClearScreen
        )
    )
}

/**
 * Show terminal context menu with standard items
 */
fun showTerminalContextMenu(
    controller: ContextMenuController,
    x: Float,
    y: Float,
    hasSelection: Boolean,
    onCopy: () -> Unit,
    onPaste: () -> Unit,
    onSelectAll: () -> Unit,
    onClearScreen: () -> Unit,
    onFind: () -> Unit
) {
    val items = createTerminalContextMenuItems(
        hasSelection = hasSelection,
        onCopy = onCopy,
        onPaste = onPaste,
        onSelectAll = onSelectAll,
        onClearScreen = onClearScreen,
        onFind = onFind
    )
    controller.showMenu(x, y, items)
}

/**
 * Context menu popup composable
 */
@Composable
fun ContextMenuPopup(
    controller: ContextMenuController,
    modifier: Modifier = Modifier
) {
    val state by controller.menuState

    if (state.isVisible) {
        Popup(
            onDismissRequest = { controller.hideMenu() },
            properties = PopupProperties(
                focusable = true,
                dismissOnBackPress = true,
                dismissOnClickOutside = true
            )
        ) {
            Column(
                modifier = modifier
                    .background(Color(0xFF2B2B2B), shape = RoundedCornerShape(4.dp))
                    .padding(vertical = 4.dp)
                    .width(200.dp)
            ) {
                state.items.forEach { item ->
                    if (item.id.startsWith("separator")) {
                        Divider(
                            color = Color(0xFF3C3F41),
                            thickness = 1.dp,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    } else {
                        MenuItemRow(
                            item = item,
                            onClick = { controller.executeItem(item.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MenuItemRow(
    item: ContextMenuController.MenuItem,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = item.enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = item.label,
            color = if (item.enabled) Color.White else Color.Gray,
            fontSize = 13.sp,
            fontWeight = if (item.enabled) FontWeight.Normal else FontWeight.Light
        )
    }
}
