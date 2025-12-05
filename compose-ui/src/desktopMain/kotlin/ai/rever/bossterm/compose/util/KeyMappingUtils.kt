package ai.rever.bossterm.compose.util

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import ai.rever.bossterm.core.input.InputEvent
import java.awt.event.KeyEvent as JavaKeyEvent

/**
 * Utility functions for mapping Compose Desktop key events to terminal key codes.
 */
object KeyMappingUtils {

    /**
     * Maps Compose Desktop Key constants to Java AWT VK (Virtual Key) codes.
     * Returns null for keys that don't have a direct VK mapping.
     */
    fun mapComposeKeyToVK(key: Key): Int? {
        return when (key) {
            Key.Enter -> JavaKeyEvent.VK_ENTER
            Key.Backspace -> JavaKeyEvent.VK_BACK_SPACE
            Key.Tab -> JavaKeyEvent.VK_TAB
            Key.Escape -> JavaKeyEvent.VK_ESCAPE
            Key.DirectionUp -> JavaKeyEvent.VK_UP
            Key.DirectionDown -> JavaKeyEvent.VK_DOWN
            Key.DirectionLeft -> JavaKeyEvent.VK_LEFT
            Key.DirectionRight -> JavaKeyEvent.VK_RIGHT
            Key.Home -> JavaKeyEvent.VK_HOME
            Key.MoveEnd -> JavaKeyEvent.VK_END
            Key.PageUp -> JavaKeyEvent.VK_PAGE_UP
            Key.PageDown -> JavaKeyEvent.VK_PAGE_DOWN
            Key.Insert -> JavaKeyEvent.VK_INSERT
            Key.Delete -> JavaKeyEvent.VK_DELETE
            Key.F1 -> JavaKeyEvent.VK_F1
            Key.F2 -> JavaKeyEvent.VK_F2
            Key.F3 -> JavaKeyEvent.VK_F3
            Key.F4 -> JavaKeyEvent.VK_F4
            Key.F5 -> JavaKeyEvent.VK_F5
            Key.F6 -> JavaKeyEvent.VK_F6
            Key.F7 -> JavaKeyEvent.VK_F7
            Key.F8 -> JavaKeyEvent.VK_F8
            Key.F9 -> JavaKeyEvent.VK_F9
            Key.F10 -> JavaKeyEvent.VK_F10
            Key.F11 -> JavaKeyEvent.VK_F11
            Key.F12 -> JavaKeyEvent.VK_F12
            else -> null
        }
    }

    /**
     * Maps Compose Desktop key event modifiers to BossTerm InputEvent modifier masks.
     * Note: Using BossTerm's InputEvent constants (SHIFT_MASK, etc.) not Java AWT's
     * SHIFT_DOWN_MASK, as TerminalKeyEncoder expects the old Event mask values.
     */
    fun mapComposeModifiers(keyEvent: KeyEvent): Int {
        var modifiers = 0
        if (keyEvent.isShiftPressed) modifiers = modifiers or InputEvent.SHIFT_MASK
        if (keyEvent.isCtrlPressed) modifiers = modifiers or InputEvent.CTRL_MASK
        if (keyEvent.isAltPressed) modifiers = modifiers or InputEvent.ALT_MASK
        if (keyEvent.isMetaPressed) modifiers = modifiers or InputEvent.META_MASK
        return modifiers
    }
}
