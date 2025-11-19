package org.jetbrains.jediterm.compose.actions

import androidx.compose.ui.input.key.KeyEvent
import java.util.concurrent.ConcurrentHashMap

/**
 * Thread-safe registry for terminal actions.
 * Manages registration, lookup, and execution of keyboard-triggered terminal actions.
 */
class ActionRegistry(private val isMacOS: Boolean) {
    private val actions = ConcurrentHashMap<String, TerminalAction>()

    /**
     * Registers a terminal action.
     * If an action with the same ID already exists, it will be replaced.
     *
     * @param action The action to register
     * @return The previous action with the same ID, or null if there was none
     */
    fun register(action: TerminalAction): TerminalAction? {
        return actions.put(action.id, action)
    }

    /**
     * Registers multiple terminal actions at once.
     */
    fun registerAll(vararg actions: TerminalAction) {
        actions.forEach { register(it) }
    }

    /**
     * Unregisters an action by its ID.
     *
     * @param id The ID of the action to unregister
     * @return The removed action, or null if no action with that ID was found
     */
    fun unregister(id: String): TerminalAction? {
        return actions.remove(id)
    }

    /**
     * Finds an action that matches the given KeyEvent.
     * Returns the first matching action found, or null if no action matches.
     *
     * @param event The keyboard event to match
     * @return The matching action, or null
     */
    fun findAction(event: KeyEvent): TerminalAction? {
        return actions.values.firstOrNull { action ->
            action.matchesKeyEvent(event, isMacOS)
        }
    }

    /**
     * Finds all actions that match the given KeyEvent.
     * Useful for debugging or when multiple actions might match.
     *
     * @param event The keyboard event to match
     * @return List of matching actions (may be empty)
     */
    fun findAllMatchingActions(event: KeyEvent): List<TerminalAction> {
        return actions.values.filter { action ->
            action.matchesKeyEvent(event, isMacOS)
        }
    }

    /**
     * Executes an action by its ID, if it exists and is enabled.
     *
     * @param id The ID of the action to execute
     * @param event The keyboard event that triggered the action
     * @return true if the action was found, enabled, and consumed the event; false otherwise
     */
    fun executeAction(id: String, event: KeyEvent): Boolean {
        val action = actions[id] ?: return false
        return action.execute(event)
    }

    /**
     * Gets an action by its ID.
     *
     * @param id The ID of the action
     * @return The action, or null if not found
     */
    fun getAction(id: String): TerminalAction? {
        return actions[id]
    }

    /**
     * Gets all registered action IDs.
     *
     * @return Set of all action IDs
     */
    fun getAllActionIds(): Set<String> {
        return actions.keys.toSet()
    }

    /**
     * Gets all registered actions.
     *
     * @return Collection of all registered actions
     */
    fun getAllActions(): Collection<TerminalAction> {
        return actions.values.toList()
    }

    /**
     * Checks if an action with the given ID is registered.
     *
     * @param id The ID to check
     * @return true if an action with that ID exists
     */
    fun hasAction(id: String): Boolean {
        return actions.containsKey(id)
    }

    /**
     * Clears all registered actions.
     */
    fun clear() {
        actions.clear()
    }

    /**
     * Returns the number of registered actions.
     */
    fun size(): Int {
        return actions.size
    }
}
