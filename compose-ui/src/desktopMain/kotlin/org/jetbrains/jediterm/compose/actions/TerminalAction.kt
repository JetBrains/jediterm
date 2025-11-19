package org.jetbrains.jediterm.compose.actions

import androidx.compose.ui.input.key.*

/**
 * Represents a keyboard shortcut with modifier keys.
 * Used to match keyboard events in the terminal.
 */
data class KeyStroke(
    val key: Key,
    val ctrl: Boolean = false,
    val shift: Boolean = false,
    val alt: Boolean = false,
    val meta: Boolean = false
) {
    /**
     * Checks if this keystroke matches the given KeyEvent.
     * Takes into account the platform (macOS uses meta, others use ctrl).
     */
    fun matches(event: KeyEvent, isMacOS: Boolean): Boolean {
        if (event.key != key) return false

        // On macOS, Cmd key is the primary modifier (meta)
        // On other platforms, Ctrl is the primary modifier
        val eventCtrl = event.isCtrlPressed
        val eventMeta = event.isMetaPressed
        val eventShift = event.isShiftPressed
        val eventAlt = event.isAltPressed

        // Check if the modifiers match
        // For platform-agnostic matching, we check both ctrl and meta flags
        val primaryModifier = if (isMacOS) meta else ctrl
        val primaryEventModifier = if (isMacOS) eventMeta else eventCtrl

        if (primaryModifier != primaryEventModifier) return false
        if (shift != eventShift) return false
        if (alt != eventAlt) return false

        // Check secondary modifier (ctrl on macOS, meta on other platforms)
        // This should match the non-primary modifier in the KeyStroke
        val secondaryModifier = if (isMacOS) ctrl else meta
        val secondaryEventModifier = if (isMacOS) eventCtrl else eventMeta
        if (secondaryModifier != secondaryEventModifier) return false

        return true
    }

    /**
     * Returns a human-readable representation of this keystroke.
     */
    fun toDisplayString(isMacOS: Boolean): String {
        val modifiers = mutableListOf<String>()

        if (isMacOS) {
            if (ctrl) modifiers.add("⌃")
            if (alt) modifiers.add("⌥")
            if (shift) modifiers.add("⇧")
            if (meta) modifiers.add("⌘")
        } else {
            if (ctrl) modifiers.add("Ctrl")
            if (alt) modifiers.add("Alt")
            if (shift) modifiers.add("Shift")
            if (meta) modifiers.add("Meta")
        }

        val keyName = key.keyCode.toString()
        return if (modifiers.isEmpty()) {
            keyName
        } else {
            modifiers.joinToString(if (isMacOS) "" else "+") + (if (isMacOS) "" else "+") + keyName
        }
    }
}

/**
 * Represents a terminal action that can be triggered by keyboard shortcuts.
 *
 * @param id Unique identifier for this action (e.g., "copy", "paste")
 * @param name Human-readable name (e.g., "Copy", "Paste")
 * @param keyStrokes List of keyboard shortcuts that trigger this action
 * @param enabled Lambda that determines if the action is currently enabled
 * @param handler Lambda that handles the action execution, returns true if consumed
 */
data class TerminalAction(
    val id: String,
    val name: String,
    val keyStrokes: List<KeyStroke>,
    val enabled: () -> Boolean = { true },
    val handler: (KeyEvent) -> Boolean
) {
    /**
     * Convenience constructor for a single keystroke.
     */
    constructor(
        id: String,
        name: String,
        keyStroke: KeyStroke,
        enabled: () -> Boolean = { true },
        handler: (KeyEvent) -> Boolean
    ) : this(id, name, listOf(keyStroke), enabled, handler)

    /**
     * Checks if any of this action's keystrokes match the given event.
     */
    fun matchesKeyEvent(event: KeyEvent, isMacOS: Boolean): Boolean {
        return keyStrokes.any { it.matches(event, isMacOS) }
    }

    /**
     * Executes this action if it's enabled.
     * @return true if the action was executed and consumed the event
     */
    fun execute(event: KeyEvent): Boolean {
        return if (enabled()) {
            handler(event)
        } else {
            false
        }
    }
}
