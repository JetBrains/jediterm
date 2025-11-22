package org.jetbrains.jediterm.compose.demo

import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEvent
import com.jediterm.core.input.MouseEvent as CoreMouseEvent
import com.jediterm.core.input.MouseWheelEvent as CoreMouseWheelEvent
import com.jediterm.terminal.emulator.mouse.MouseButtonCodes
import com.jediterm.terminal.emulator.mouse.MouseButtonModifierFlags

/**
 * Creates a JediTerm MouseEvent from Compose button code and modifiers.
 */
fun createMouseEvent(buttonCode: Int, modifierKeys: Int): CoreMouseEvent {
    return CoreMouseEvent(buttonCode, modifierKeys)
}

/**
 * Creates a JediTerm MouseWheelEvent from Compose button code and modifiers.
 */
fun createMouseWheelEvent(buttonCode: Int, modifierKeys: Int): CoreMouseWheelEvent {
    return CoreMouseWheelEvent(buttonCode, modifierKeys)
}

/**
 * Converts Compose PointerButton to JediTerm MouseButtonCodes constant.
 *
 * @return MouseButtonCodes constant (LEFT=0, MIDDLE=1, RIGHT=2, NONE=-1)
 */
fun PointerButton.toMouseButtonCode(): Int {
    return when (this) {
        PointerButton.Primary -> MouseButtonCodes.LEFT
        PointerButton.Secondary -> MouseButtonCodes.RIGHT
        PointerButton.Tertiary -> MouseButtonCodes.MIDDLE
        else -> MouseButtonCodes.NONE
    }
}

/**
 * Converts Compose PointerEvent keyboard modifiers to JediTerm mouse modifier flags.
 * Uses platform AWT event to check modifiers since PointerKeyboardModifiers doesn't expose public API.
 * Modifiers are combined as bit flags:
 * - SHIFT = 4 (bit 2)
 * - META = 8 (bit 3)
 * - CTRL = 16 (bit 4)
 *
 * @return Combined modifier flags as integer bitmask
 */
fun PointerEvent.toMouseModifierFlags(): Int {
    var flags = 0
    // Use AWT modifiers from native event if available
    val awtEvent = nativeEvent as? java.awt.event.MouseEvent
    if (awtEvent != null) {
        if (awtEvent.isShiftDown) {
            flags = flags or MouseButtonModifierFlags.MOUSE_BUTTON_SHIFT_FLAG
        }
        if (awtEvent.isControlDown) {
            flags = flags or MouseButtonModifierFlags.MOUSE_BUTTON_CTRL_FLAG
        }
        if (awtEvent.isMetaDown) {
            flags = flags or MouseButtonModifierFlags.MOUSE_BUTTON_META_FLAG
        }
    }
    return flags
}

/**
 * Checks if Shift key is pressed in a PointerEvent.
 * Reuses modifier flag extraction to avoid duplication.
 */
fun PointerEvent.isShiftPressed(): Boolean {
    return (toMouseModifierFlags() and MouseButtonModifierFlags.MOUSE_BUTTON_SHIFT_FLAG) != 0
}

/**
 * Creates a JediTerm MouseEvent from a Compose PointerEvent.
 *
 * @param event The Compose pointer event
 * @param button The button that triggered the event
 * @return MouseEvent ready for terminal forwarding
 */
fun createComposeMouseEvent(
    event: PointerEvent,
    button: PointerButton
): CoreMouseEvent {
    val buttonCode = button.toMouseButtonCode()
    val modifiers = event.toMouseModifierFlags()
    return createMouseEvent(buttonCode, modifiers)
}

/**
 * Creates a JediTerm MouseWheelEvent from a Compose PointerEvent.
 *
 * Terminal protocol convention (matches xterm):
 * - Positive scroll delta (wheel DOWN, away from user) → SCROLLUP code (64+0)
 * - Negative scroll delta (wheel UP, toward user) → SCROLLDOWN code (64+1)
 *
 * This mapping seems counter-intuitive but matches standard xterm/terminal behavior:
 * the button codes represent the scroll direction of the content, not the wheel movement.
 * When you scroll wheel DOWN, content scrolls UP (revealing earlier content).
 *
 * @param event The Compose pointer event with scroll delta
 * @param scrollDirection The Y scroll delta from change.scrollDelta.y
 * @return MouseWheelEvent ready for terminal forwarding
 */
fun createComposeMouseWheelEvent(
    event: PointerEvent,
    scrollDirection: Float
): CoreMouseWheelEvent {
    // Terminal protocol: positive delta → SCROLLUP, negative → SCROLLDOWN
    val buttonCode = if (scrollDirection > 0) {
        MouseButtonCodes.SCROLLUP
    } else {
        MouseButtonCodes.SCROLLDOWN
    }
    val modifiers = event.toMouseModifierFlags()
    return createMouseWheelEvent(buttonCode, modifiers)
}
