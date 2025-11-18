package com.jediterm.terminal.emulator.mouse

import com.jediterm.core.input.MouseEvent
import com.jediterm.core.input.MouseWheelEvent

interface TerminalMouseListener {
    fun mousePressed(x: Int, y: Int, event: MouseEvent)
    fun mouseReleased(x: Int, y: Int, event: MouseEvent)
    fun mouseMoved(x: Int, y: Int, event: MouseEvent)
    fun mouseDragged(x: Int, y: Int, event: MouseEvent)
    fun mouseWheelMoved(x: Int, y: Int, event: MouseWheelEvent)
}
