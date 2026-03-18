package com.jediterm.terminal

import com.jediterm.core.Color
import org.junit.Assert.*
import org.junit.Test

internal class TerminalColorTest {

  @Test
  fun `fromColor preserves alpha component`() {
    checkColor(Color(255, 128, 64, 32))
  }

  @Test
  fun `fromColor with null returns null`() {
    val terminalColor = TerminalColor.fromColor(null)
    assertNull(terminalColor)
  }

  private fun checkColor(color: Color) {
    val terminalColor = TerminalColor.fromColor(color)
    assertNotNull(terminalColor)
    assertEquals(color, terminalColor!!.toColor())
  }
}
