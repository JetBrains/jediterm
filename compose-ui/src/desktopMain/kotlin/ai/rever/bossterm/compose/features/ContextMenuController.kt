package ai.rever.bossterm.compose.features

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposeWindow
import java.awt.Color
import java.awt.Font
import java.awt.KeyboardFocusManager
import java.awt.MouseInfo
import java.awt.Window
import java.awt.event.ActionListener
import javax.swing.BorderFactory
import javax.swing.JMenuItem
import javax.swing.JPopupMenu
import javax.swing.JSeparator
import javax.swing.UIManager
import javax.swing.plaf.ColorUIResource

/**
 * Controller for managing context menu state and actions.
 * Uses AWT JPopupMenu for native context menu that can extend beyond window bounds.
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
     * Show context menu at screen coordinates using native AWT popup
     */
    fun showMenuAtScreenPosition(screenX: Int, screenY: Int, items: List<MenuItem>) {
        showNativeMenuAtScreen(screenX, screenY, items)
    }

    /**
     * Show context menu at current mouse position using native AWT popup
     */
    fun showMenu(x: Float, y: Float, items: List<MenuItem>, window: ComposeWindow? = null) {
        // Get actual mouse screen position - most reliable way
        val mouseLocation = MouseInfo.getPointerInfo()?.location
        if (mouseLocation != null) {
            showNativeMenuAtScreen(mouseLocation.x, mouseLocation.y, items)
        } else {
            // Fallback to state-based menu if mouse info not available
            _menuState.value = MenuState(
                isVisible = true,
                x = x,
                y = y,
                items = items
            )
        }
    }

    /**
     * Show native AWT popup menu at screen coordinates with dark theme styling
     */
    private fun showNativeMenuAtScreen(screenX: Int, screenY: Int, items: List<MenuItem>) {
        val popup = JPopupMenu().apply {
            // Dark theme colors
            background = Color(0x2B, 0x2B, 0x2B)
            border = BorderFactory.createLineBorder(Color(0x3C, 0x3F, 0x41), 1)
        }

        items.forEach { item ->
            if (item.id.startsWith("separator")) {
                popup.add(JSeparator().apply {
                    background = Color(0x2B, 0x2B, 0x2B)
                    foreground = Color(0x3C, 0x3F, 0x41)
                })
            } else {
                val menuItem = JMenuItem(item.label).apply {
                    isEnabled = item.enabled
                    background = Color(0x2B, 0x2B, 0x2B)
                    foreground = if (item.enabled) Color.WHITE else Color.GRAY
                    font = Font(".AppleSystemUIFont", Font.PLAIN, 13)
                    border = BorderFactory.createEmptyBorder(4, 12, 4, 12)
                    isOpaque = true
                    addActionListener { item.action() }
                }
                popup.add(menuItem)
            }
        }

        // Get the focused window to use as invoker
        val focusedWindow = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusedWindow
        if (focusedWindow != null) {
            // Convert screen coordinates to window-relative coordinates
            val windowLocation = focusedWindow.locationOnScreen
            val relativeX = screenX - windowLocation.x
            val relativeY = screenY - windowLocation.y
            // Use show() for proper dismiss behavior
            popup.show(focusedWindow, relativeX, relativeY)
        } else {
            // Fallback: show at screen location (may not dismiss properly)
            popup.location = java.awt.Point(screenX, screenY)
            popup.isVisible = true
        }
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
    onClearScrollback: () -> Unit,
    onFind: () -> Unit,
    onSplitVertical: (() -> Unit)? = null,
    onSplitHorizontal: (() -> Unit)? = null,
    onMoveToNewTab: (() -> Unit)? = null,
    onShowDebug: (() -> Unit)? = null,
    onShowSettings: (() -> Unit)? = null
): List<ContextMenuController.MenuItem> {
    val baseItems = listOf(
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
        ),
        ContextMenuController.MenuItem(
            id = "clear_scrollback",
            label = "Clear Scrollback",
            enabled = true,
            action = onClearScrollback
        )
    )

    // Add split options section
    val splitItems = mutableListOf<ContextMenuController.MenuItem>()

    if (onSplitVertical != null || onSplitHorizontal != null || onMoveToNewTab != null) {
        splitItems.add(
            ContextMenuController.MenuItem(
                id = "separator_split",
                label = "",
                enabled = false,
                action = {}
            )
        )

        if (onSplitVertical != null) {
            splitItems.add(
                ContextMenuController.MenuItem(
                    id = "split_vertical",
                    label = "Split Pane Vertically",
                    enabled = true,
                    action = onSplitVertical
                )
            )
        }

        if (onSplitHorizontal != null) {
            splitItems.add(
                ContextMenuController.MenuItem(
                    id = "split_horizontal",
                    label = "Split Pane Horizontally",
                    enabled = true,
                    action = onSplitHorizontal
                )
            )
        }

        if (onMoveToNewTab != null) {
            splitItems.add(
                ContextMenuController.MenuItem(
                    id = "move_to_new_tab",
                    label = "Move Pane to New Tab",
                    enabled = true,
                    action = onMoveToNewTab
                )
            )
        }
    }

    // Add extra options section
    val extraItems = mutableListOf<ContextMenuController.MenuItem>()

    if (onShowSettings != null || onShowDebug != null) {
        extraItems.add(
            ContextMenuController.MenuItem(
                id = "separator_extra",
                label = "",
                enabled = false,
                action = {}
            )
        )
    }

    if (onShowSettings != null) {
        extraItems.add(
            ContextMenuController.MenuItem(
                id = "show_settings",
                label = "Settings...",
                enabled = true,
                action = onShowSettings
            )
        )
    }

    if (onShowDebug != null) {
        extraItems.add(
            ContextMenuController.MenuItem(
                id = "show_debug",
                label = "Show Debug Panel",
                enabled = true,
                action = onShowDebug
            )
        )
    }

    return baseItems + splitItems + extraItems
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
    onClearScrollback: () -> Unit,
    onFind: () -> Unit,
    onSplitVertical: (() -> Unit)? = null,
    onSplitHorizontal: (() -> Unit)? = null,
    onMoveToNewTab: (() -> Unit)? = null,
    onShowDebug: (() -> Unit)? = null,
    onShowSettings: (() -> Unit)? = null,
    window: ComposeWindow? = null
) {
    val items = createTerminalContextMenuItems(
        hasSelection = hasSelection,
        onCopy = onCopy,
        onPaste = onPaste,
        onSelectAll = onSelectAll,
        onClearScreen = onClearScreen,
        onClearScrollback = onClearScrollback,
        onFind = onFind,
        onSplitVertical = onSplitVertical,
        onSplitHorizontal = onSplitHorizontal,
        onMoveToNewTab = onMoveToNewTab,
        onShowDebug = onShowDebug,
        onShowSettings = onShowSettings
    )
    controller.showMenu(x, y, items, window)
}

/**
 * Create hyperlink-specific context menu items
 */
fun createHyperlinkContextMenuItems(
    url: String,
    onOpenLink: () -> Unit,
    onCopyLinkAddress: () -> Unit
): List<ContextMenuController.MenuItem> {
    return listOf(
        ContextMenuController.MenuItem(
            id = "open_link",
            label = "Open Link",
            enabled = true,
            action = onOpenLink
        ),
        ContextMenuController.MenuItem(
            id = "copy_link",
            label = "Copy Link Address",
            enabled = true,
            action = onCopyLinkAddress
        ),
        ContextMenuController.MenuItem(
            id = "separator_hyperlink",
            label = "",
            enabled = false,
            action = {}
        )
    )
}

/**
 * Show context menu with hyperlink actions followed by standard terminal items
 */
/**
 * Context menu popup composable - no longer needed when using native AWT menu.
 * Kept for backward compatibility but does nothing since native menu is shown directly.
 */
@Composable
fun ContextMenuPopup(
    controller: ContextMenuController,
    modifier: Modifier = Modifier
) {
    // No-op - native menu is shown directly via JPopupMenu
    // The state-based fallback could be implemented here if needed
}

fun showHyperlinkContextMenu(
    controller: ContextMenuController,
    x: Float,
    y: Float,
    url: String,
    onOpenLink: () -> Unit,
    onCopyLinkAddress: () -> Unit,
    hasSelection: Boolean,
    onCopy: () -> Unit,
    onPaste: () -> Unit,
    onSelectAll: () -> Unit,
    onClearScreen: () -> Unit,
    onClearScrollback: () -> Unit,
    onFind: () -> Unit,
    onSplitVertical: (() -> Unit)? = null,
    onSplitHorizontal: (() -> Unit)? = null,
    onMoveToNewTab: (() -> Unit)? = null,
    onShowDebug: (() -> Unit)? = null,
    onShowSettings: (() -> Unit)? = null,
    window: ComposeWindow? = null
) {
    val hyperlinkItems = createHyperlinkContextMenuItems(
        url = url,
        onOpenLink = onOpenLink,
        onCopyLinkAddress = onCopyLinkAddress
    )
    val terminalItems = createTerminalContextMenuItems(
        hasSelection = hasSelection,
        onCopy = onCopy,
        onPaste = onPaste,
        onSelectAll = onSelectAll,
        onClearScreen = onClearScreen,
        onClearScrollback = onClearScrollback,
        onFind = onFind,
        onSplitVertical = onSplitVertical,
        onSplitHorizontal = onSplitHorizontal,
        onMoveToNewTab = onMoveToNewTab,
        onShowDebug = onShowDebug,
        onShowSettings = onShowSettings
    )
    controller.showMenu(x, y, hyperlinkItems + terminalItems, window)
}
