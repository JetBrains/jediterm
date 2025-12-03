package ai.rever.bossterm.terminal.emulator.mouse

import ai.rever.bossterm.core.input.MouseEvent
import ai.rever.bossterm.core.input.MouseWheelEvent

interface TerminalMouseListener {
    fun mousePressed(x: Int, y: Int, event: MouseEvent)
    fun mouseReleased(x: Int, y: Int, event: MouseEvent)
    fun mouseMoved(x: Int, y: Int, event: MouseEvent)
    fun mouseDragged(x: Int, y: Int, event: MouseEvent)
    fun mouseWheelMoved(x: Int, y: Int, event: MouseWheelEvent)
}
