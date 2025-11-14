package org.jetbrains.jediterm.compose.features

import kotlin.test.*

class ContextMenuTest {

    @Test
    fun testContextMenuControllerInitialization() {
        val controller = ContextMenuController()
        val state = controller.menuState.value

        assertFalse(state.isVisible)
        assertEquals(0f, state.x)
        assertEquals(0f, state.y)
        assertTrue(state.items.isEmpty())
    }

    @Test
    fun testShowMenu() {
        val controller = ContextMenuController()
        val items = listOf(
            ContextMenuController.MenuItem("copy", "Copy", true) { },
            ContextMenuController.MenuItem("paste", "Paste", true) { }
        )

        controller.showMenu(100f, 200f, items)

        val state = controller.menuState.value
        assertTrue(state.isVisible)
        assertEquals(100f, state.x)
        assertEquals(200f, state.y)
        assertEquals(2, state.items.size)
    }

    @Test
    fun testHideMenu() {
        val controller = ContextMenuController()
        val items = listOf(
            ContextMenuController.MenuItem("copy", "Copy", true) { }
        )

        controller.showMenu(100f, 200f, items)
        assertTrue(controller.menuState.value.isVisible)

        controller.hideMenu()
        assertFalse(controller.menuState.value.isVisible)
    }

    @Test
    fun testExecuteMenuItem() {
        val controller = ContextMenuController()
        var actionExecuted = false

        val items = listOf(
            ContextMenuController.MenuItem("test", "Test", true) {
                actionExecuted = true
            }
        )

        controller.showMenu(0f, 0f, items)
        controller.executeItem("test")

        assertTrue(actionExecuted, "Menu item action should be executed")
        assertFalse(controller.menuState.value.isVisible, "Menu should be hidden after execution")
    }

    @Test
    fun testExecuteDisabledMenuItem() {
        val controller = ContextMenuController()
        var actionExecuted = false

        val items = listOf(
            ContextMenuController.MenuItem("test", "Test", false) {
                actionExecuted = true
            }
        )

        controller.showMenu(0f, 0f, items)
        controller.executeItem("test")

        assertFalse(actionExecuted, "Disabled menu item action should not be executed")
    }

    @Test
    fun testExecuteNonExistentMenuItem() {
        val controller = ContextMenuController()
        var actionExecuted = false

        val items = listOf(
            ContextMenuController.MenuItem("test", "Test", true) {
                actionExecuted = true
            }
        )

        controller.showMenu(0f, 0f, items)
        controller.executeItem("nonexistent")

        assertFalse(actionExecuted, "Non-existent menu item should not execute any action")
    }

    @Test
    fun testCreateTerminalContextMenuItems() {
        var copyExecuted = false
        var pasteExecuted = false
        var selectAllExecuted = false
        var clearExecuted = false
        var findExecuted = false

        val items = createTerminalContextMenuItems(
            hasSelection = true,
            onCopy = { copyExecuted = true },
            onPaste = { pasteExecuted = true },
            onSelectAll = { selectAllExecuted = true },
            onClearScreen = { clearExecuted = true },
            onFind = { findExecuted = true }
        )

        assertEquals(5, items.size)

        // Verify all items are present
        assertNotNull(items.find { it.id == "copy" })
        assertNotNull(items.find { it.id == "paste" })
        assertNotNull(items.find { it.id == "select_all" })
        assertNotNull(items.find { it.id == "find" })
        assertNotNull(items.find { it.id == "clear" })

        // Copy should be enabled when there's selection
        val copyItem = items.find { it.id == "copy" }!!
        assertTrue(copyItem.enabled)
    }

    @Test
    fun testTerminalContextMenuItemsWithoutSelection() {
        val items = createTerminalContextMenuItems(
            hasSelection = false,
            onCopy = { },
            onPaste = { },
            onSelectAll = { },
            onClearScreen = { },
            onFind = { }
        )

        // Copy should be disabled when there's no selection
        val copyItem = items.find { it.id == "copy" }!!
        assertFalse(copyItem.enabled)

        // Other items should still be enabled
        val pasteItem = items.find { it.id == "paste" }!!
        assertTrue(pasteItem.enabled)
    }

    @Test
    fun testShowTerminalContextMenu() {
        val controller = ContextMenuController()
        var copyExecuted = false

        showTerminalContextMenu(
            controller = controller,
            x = 50f,
            y = 100f,
            hasSelection = true,
            onCopy = { copyExecuted = true },
            onPaste = { },
            onSelectAll = { },
            onClearScreen = { },
            onFind = { }
        )

        val state = controller.menuState.value
        assertTrue(state.isVisible)
        assertEquals(50f, state.x)
        assertEquals(100f, state.y)
        assertTrue(state.items.isNotEmpty())

        // Execute copy action
        controller.executeItem("copy")
        assertTrue(copyExecuted)
    }

    @Test
    fun testMenuItemDataClass() {
        val item = ContextMenuController.MenuItem(
            id = "test",
            label = "Test Item",
            enabled = true,
            action = { }
        )

        assertEquals("test", item.id)
        assertEquals("Test Item", item.label)
        assertTrue(item.enabled)
    }

    @Test
    fun testMenuStateDataClass() {
        val state = ContextMenuController.MenuState(
            isVisible = true,
            x = 10f,
            y = 20f,
            items = emptyList()
        )

        assertTrue(state.isVisible)
        assertEquals(10f, state.x)
        assertEquals(20f, state.y)
        assertTrue(state.items.isEmpty())
    }

    @Test
    fun testMultipleMenuItems() {
        val controller = ContextMenuController()
        val executionOrder = mutableListOf<String>()

        val items = listOf(
            ContextMenuController.MenuItem("first", "First", true) {
                executionOrder.add("first")
            },
            ContextMenuController.MenuItem("second", "Second", true) {
                executionOrder.add("second")
            },
            ContextMenuController.MenuItem("third", "Third", true) {
                executionOrder.add("third")
            }
        )

        controller.showMenu(0f, 0f, items)

        controller.executeItem("second")
        assertEquals(listOf("second"), executionOrder)

        controller.showMenu(0f, 0f, items) // Show again
        controller.executeItem("first")
        assertEquals(listOf("second", "first"), executionOrder)
    }
}
