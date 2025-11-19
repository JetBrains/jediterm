package org.jetbrains.jediterm.compose.actions

import androidx.compose.ui.input.key.*
import kotlin.test.*

/**
 * Unit tests for the terminal actions framework.
 *
 * Note: KeyEvent creation is not tested directly because KeyEvent constructor
 * is internal to Compose UI. The keystroke matching logic has been validated
 * through manual integration testing. These tests focus on:
 * - Action registration and retrieval
 * - TerminalAction properties and state
 * - ActionRegistry operations
 */
class ActionRegistryTest {

    // ======================== KeyStroke Tests ========================

    @Test
    fun testKeyStrokeCreation() {
        val keyStroke = KeyStroke(key = Key.A)
        assertEquals(Key.A, keyStroke.key)
        assertFalse(keyStroke.ctrl)
        assertFalse(keyStroke.shift)
        assertFalse(keyStroke.alt)
        assertFalse(keyStroke.meta)
    }

    @Test
    fun testKeyStrokeWithModifiers() {
        val keyStroke = KeyStroke(key = Key.C, ctrl = true, shift = true)
        assertEquals(Key.C, keyStroke.key)
        assertTrue(keyStroke.ctrl)
        assertTrue(keyStroke.shift)
        assertFalse(keyStroke.alt)
        assertFalse(keyStroke.meta)
    }

    @Test
    fun testKeyStrokeWithAllModifiers() {
        val keyStroke = KeyStroke(key = Key.Z, ctrl = true, shift = true, alt = true, meta = true)
        assertEquals(Key.Z, keyStroke.key)
        assertTrue(keyStroke.ctrl)
        assertTrue(keyStroke.shift)
        assertTrue(keyStroke.alt)
        assertTrue(keyStroke.meta)
    }

    // ======================== TerminalAction Tests ========================

    @Test
    fun testTerminalActionCreation() {
        var executed = false
        val action = TerminalAction(
            id = "test_action",
            name = "Test Action",
            keyStroke = KeyStroke(key = Key.T),
            handler = {
                executed = true
                true
            }
        )

        assertEquals("test_action", action.id)
        assertEquals("Test Action", action.name)
        assertEquals(1, action.keyStrokes.size)
        assertEquals(Key.T, action.keyStrokes[0].key)
        assertFalse(executed)
    }

    @Test
    fun testTerminalActionWithMultipleKeyStrokes() {
        val action = TerminalAction(
            id = "copy",
            name = "Copy",
            keyStrokes = listOf(
                KeyStroke(key = Key.C, ctrl = true),  // Windows/Linux
                KeyStroke(key = Key.C, meta = true)   // macOS
            ),
            handler = { true }
        )

        assertEquals("copy", action.id)
        assertEquals("Copy", action.name)
        assertEquals(2, action.keyStrokes.size)
        assertTrue(action.keyStrokes[0].ctrl)
        assertTrue(action.keyStrokes[1].meta)
    }

    @Test
    fun testTerminalActionEnabledPredicate() {
        var isEnabled = true
        val action = TerminalAction(
            id = "test",
            name = "Test",
            keyStroke = KeyStroke(key = Key.T),
            enabled = { isEnabled },
            handler = { true }
        )

        // When enabled
        assertTrue(action.enabled())

        // When disabled
        isEnabled = false
        assertFalse(action.enabled())
    }

    // ======================== ActionRegistry Tests ========================

    @Test
    fun testActionRegistryCreation() {
        val registry = ActionRegistry(isMacOS = false)

        assertEquals(0, registry.size())
        assertTrue(registry.getAllActions().isEmpty())
    }

    @Test
    fun testRegisterSingleAction() {
        val registry = ActionRegistry(isMacOS = false)
        val action = TerminalAction(
            id = "test",
            name = "Test",
            keyStroke = KeyStroke(key = Key.T),
            handler = { true }
        )

        val result = registry.register(action)

        assertNull(result) // First registration returns null
        assertEquals(1, registry.size())
        assertTrue(registry.hasAction("test"))
    }

    @Test
    fun testRegisterMultipleActions() {
        val registry = ActionRegistry(isMacOS = false)
        val action1 = TerminalAction(id = "action1", name = "Action 1", keyStroke = KeyStroke(key = Key.A), handler = { true })
        val action2 = TerminalAction(id = "action2", name = "Action 2", keyStroke = KeyStroke(key = Key.B), handler = { true })
        val action3 = TerminalAction(id = "action3", name = "Action 3", keyStroke = KeyStroke(key = Key.C), handler = { true })

        registry.registerAll(action1, action2, action3)

        assertEquals(3, registry.size())
        assertTrue(registry.hasAction("action1"))
        assertTrue(registry.hasAction("action2"))
        assertTrue(registry.hasAction("action3"))
    }

    @Test
    fun testRegisterDuplicateActionReturnsOldAction() {
        val registry = ActionRegistry(isMacOS = false)
        val action1 = TerminalAction(id = "test", name = "First", keyStroke = KeyStroke(key = Key.A), handler = { true })
        val action2 = TerminalAction(id = "test", name = "Second", keyStroke = KeyStroke(key = Key.B), handler = { true })

        val result1 = registry.register(action1)
        val result2 = registry.register(action2)

        assertNull(result1) // First registration
        assertNotNull(result2) // Second registration returns old action
        assertEquals("First", result2?.name)
        assertEquals(1, registry.size()) // Only one action (replaced)

        // Verify the new action is now registered
        val currentAction = registry.getAction("test")
        assertEquals("Second", currentAction?.name)
    }

    @Test
    fun testUnregisterAction() {
        val registry = ActionRegistry(isMacOS = false)
        val action = TerminalAction(id = "test", name = "Test", keyStroke = KeyStroke(key = Key.T), handler = { true })

        registry.register(action)
        assertTrue(registry.hasAction("test"))

        val removed = registry.unregister("test")
        assertNotNull(removed)
        assertEquals("test", removed?.id)
        assertFalse(registry.hasAction("test"))
        assertEquals(0, registry.size())
    }

    @Test
    fun testUnregisterNonExistentAction() {
        val registry = ActionRegistry(isMacOS = false)

        val removed = registry.unregister("nonexistent")
        assertNull(removed)
    }

    @Test
    fun testGetAction() {
        val registry = ActionRegistry(isMacOS = false)
        val action = TerminalAction(id = "test", name = "Test", keyStroke = KeyStroke(key = Key.T), handler = { true })

        registry.register(action)

        val retrieved = registry.getAction("test")
        assertNotNull(retrieved)
        assertEquals("test", retrieved.id)
        assertEquals("Test", retrieved.name)
    }

    @Test
    fun testGetNonExistentAction() {
        val registry = ActionRegistry(isMacOS = false)

        val retrieved = registry.getAction("nonexistent")
        assertNull(retrieved)
    }

    @Test
    fun testGetAllActions() {
        val registry = ActionRegistry(isMacOS = false)
        val action1 = TerminalAction(id = "action1", name = "Action 1", keyStroke = KeyStroke(key = Key.A), handler = { true })
        val action2 = TerminalAction(id = "action2", name = "Action 2", keyStroke = KeyStroke(key = Key.B), handler = { true })

        registry.registerAll(action1, action2)

        val allActions = registry.getAllActions()
        assertEquals(2, allActions.size)
        assertTrue(allActions.any { it.id == "action1" })
        assertTrue(allActions.any { it.id == "action2" })
    }

    @Test
    fun testClearRegistry() {
        val registry = ActionRegistry(isMacOS = false)
        val action1 = TerminalAction(id = "action1", name = "Action 1", keyStroke = KeyStroke(key = Key.A), handler = { true })
        val action2 = TerminalAction(id = "action2", name = "Action 2", keyStroke = KeyStroke(key = Key.B), handler = { true })

        registry.registerAll(action1, action2)
        assertEquals(2, registry.size())

        registry.clear()

        assertEquals(0, registry.size())
        assertFalse(registry.hasAction("action1"))
        assertFalse(registry.hasAction("action2"))
        assertTrue(registry.getAllActions().isEmpty())
    }

    @Test
    fun testPlatformSpecificRegistry() {
        // Test macOS registry
        val macRegistry = ActionRegistry(isMacOS = true)
        assertEquals(0, macRegistry.size())

        // Test Windows/Linux registry
        val winRegistry = ActionRegistry(isMacOS = false)
        assertEquals(0, winRegistry.size())
    }
}
