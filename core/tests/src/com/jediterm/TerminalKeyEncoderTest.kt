package com.jediterm

import com.jediterm.core.Platform
import com.jediterm.core.input.InputEvent
import com.jediterm.core.input.KeyEvent
import com.jediterm.core.util.Ascii
import com.jediterm.terminal.TerminalKeyEncoder
import org.junit.Assert
import org.junit.Test

class TerminalKeyEncoderTest {

  @Test
  fun `Alt Backspace`() {
    assertKeyCode(Ascii.BS.toInt(), InputEvent.ALT_MASK, byteArrayOf(Ascii.ESC, Ascii.DEL))
  }

  @Test
  fun `Alt Left`() {
    val expected = if (Platform.isMacOS()) prependEsc("b") else prependEsc("[1;3D")
    assertKeyCode(KeyEvent.VK_LEFT, InputEvent.ALT_MASK, expected)
  }

  @Test
  fun `Shift Left`() {
    assertKeyCode(KeyEvent.VK_LEFT, InputEvent.SHIFT_MASK, prependEsc("[1;2D"))
  }

  @Test
  fun `Shift Left application`() {
    assertKeyCode(KeyEvent.VK_LEFT, InputEvent.SHIFT_MASK, prependEsc("[1;2D"))
  }

  @Test
  fun `Control F1`() {
    assertKeyCode(KeyEvent.VK_F1, InputEvent.CTRL_MASK, prependEsc("[1;5P"))
  }

  @Test
  fun `Control F11`() {
    assertKeyCode(KeyEvent.VK_F11, InputEvent.CTRL_MASK, prependEsc("[23;5~"))
  }

  private fun assertKeyCode(key: Int, modifiers: Int, expectedKeyCodeStr: String) {
    assertKeyCode(key, modifiers, expectedKeyCodeStr.toByteArray(Charsets.UTF_8))
  }

  private fun assertKeyCode(key: Int, modifiers: Int, expectedKeyCode: ByteArray) {
    val keyEncoder = TerminalKeyEncoder()
    val actual = keyEncoder.getCode(key, modifiers)
    Assert.assertArrayEquals(expectedKeyCode, actual)
  }

  private fun prependEsc(str: String): String = Ascii.ESC_CHAR + str
}
