package com.jediterm.terminal

import com.jediterm.core.Color
import org.junit.Assert.*
import org.junit.Test

internal class TerminalColorTest {

  @Test
  fun `color preserves alpha component`() {
    checkColor(Color(255, 128, 64, 32))
  }

  @Test
  fun `color with null returns null`() {
    val terminalColor = TerminalColor.color(null)
    assertNull(terminalColor)
  }

  private fun checkColor(color: Color) {
    val terminalColor = TerminalColor.color(color)
    assertNotNull(terminalColor)
    assertEquals(color, terminalColor!!.toColor())
  }
}
