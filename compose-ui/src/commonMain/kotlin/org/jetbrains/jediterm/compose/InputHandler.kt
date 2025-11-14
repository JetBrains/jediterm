package org.jetbrains.jediterm.compose

import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.pointer.PointerEvent

interface InputHandler {
    fun handleKeyEvent(keyEvent: KeyEvent): ByteArray? = null
    fun handleMouseEvent(pointerEvent: PointerEvent): ByteArray? = null
    fun handleTouchEvent(pointerEvent: PointerEvent): ByteArray? = null
}
