package com.jediterm.ghostty

import com.jediterm.core.Color
import com.jediterm.core.util.TermSize
import com.jediterm.terminal.CursorShape
import com.jediterm.terminal.RequestOrigin
import com.jediterm.terminal.TerminalColor
import com.jediterm.terminal.TerminalDisplay
import com.jediterm.terminal.TerminalOutputStream
import com.jediterm.terminal.emulator.mouse.MouseFormat
import com.jediterm.terminal.emulator.mouse.MouseMode
import com.jediterm.terminal.model.StyleState
import com.jediterm.terminal.model.TerminalSelection
import com.jediterm.terminal.model.TerminalTextBuffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets

/**
 * Headless end-to-end test of the ghostty-backed engine: it drives [GhosttyTerminalEngine] with raw
 * VT byte sequences (exactly what a pty would deliver) and asserts that ghostty's screen, styles,
 * cursor, modes, and query responses are correctly mirrored to the JediTerm [TerminalTextBuffer] /
 * [TerminalDisplay] that the Swing widget renders.
 *
 * No Swing involved, so it runs headless; the actual widget wiring is exercised by the demo.
 */
class GhosttyTerminalEngineTest {

  @Test
  fun rendersTextWithSgrColorAndTracksCursor() {
    val display = CaptureDisplay()
    val style = StyleState()
    val buffer = TerminalTextBuffer(20, 5, style)
    val engine = GhosttyTerminalEngine(display, buffer, style)

    feed(engine, "Hello " + ESC + "[31mRED" + ESC + "[0m!")

    assertEquals("Hello RED!", firstLine(buffer))

    // 'R' is the 7th cell (index 6); SGR 31 -> palette index 1 (named red).
    val redStyle = buffer.getStyleAt(6, 0)!!
    assertEquals(TerminalColor.index(1), redStyle.foreground)
    // The leading "Hello " uses the default (unset) foreground.
    assertEquals(null, buffer.getStyleAt(0, 0)!!.foreground)

    // Cursor followed the output: 10 glyphs written -> column 10 (0-based); display Y is 1-based.
    assertEquals(10, display.cursorX)
    assertEquals(1, display.cursorY)
    // The terminal's own cursor coordinates (read by the Swing widget's type-ahead model to paint
    // the cursor column) must also track ghostty, not stay at home. JediTerminal reports 1-based.
    assertEquals(11, engine.getCursorX())
    assertEquals(1, engine.getCursorY())

    engine.disconnected()
  }

  @Test
  fun tracksBracketedPasteAndAlternateScreenModes() {
    val display = CaptureDisplay()
    val style = StyleState()
    val buffer = TerminalTextBuffer(20, 5, style)
    val engine = GhosttyTerminalEngine(display, buffer, style)

    assertFalse(display.bracketedPaste)
    feed(engine, ESC + "[?2004h")
    assertTrue(display.bracketedPaste)

    assertFalse(display.alternateScreen)
    feed(engine, ESC + "[?1049h")
    assertTrue(display.alternateScreen)
    feed(engine, ESC + "[?1049l")
    assertFalse(display.alternateScreen)

    engine.disconnected()
  }

  @Test
  fun resizeReflowsAndKeepsRendering() {
    val display = CaptureDisplay()
    val style = StyleState()
    val buffer = TerminalTextBuffer(20, 5, style)
    val engine = GhosttyTerminalEngine(display, buffer, style)

    feed(engine, "abcdefghij")           // 10 chars on row 0
    engine.resize(TermSize(5, 5), RequestOrigin.User)  // width 5 -> soft-wrap

    // "abcdefghij" reflowed at width 5 -> "abcde" / "fghij".
    assertEquals("abcde", firstLine(buffer))
    engine.disconnected()
  }

  @Test
  fun answersDeviceStatusReportViaWritePty() {
    val display = CaptureDisplay()
    val style = StyleState()
    val buffer = TerminalTextBuffer(20, 5, style)
    val engine = GhosttyTerminalEngine(display, buffer, style)

    val ptyWrites = ArrayList<ByteArray>()
    engine.setTerminalOutput(object : TerminalOutputStream {
      override fun sendBytes(response: ByteArray, userInput: Boolean) {
        ptyWrites.add(response)
      }

      override fun sendString(string: String, userInput: Boolean) {
        ptyWrites.add(string.toByteArray(StandardCharsets.UTF_8))
      }
    })

    feed(engine, "ab" + ESC + "[6n") // DSR: report cursor position

    assertEquals(1, ptyWrites.size)
    val reply = String(ptyWrites[0], StandardCharsets.UTF_8)
    // CSI row ; col R  -> cursor is at row 1, col 3 (after "ab").
    assertEquals(ESC + "[1;3R", reply)

    engine.disconnected()
  }

  private fun feed(engine: GhosttyTerminalEngine, vt: String) {
    engine.processBytes(vt.toByteArray(StandardCharsets.UTF_8))
  }

  private fun firstLine(buffer: TerminalTextBuffer): String {
    return buffer.screenLinesStorage.get(0).text
  }

  /** Captures the [TerminalDisplay] callbacks the engine drives. */
  private class CaptureDisplay : TerminalDisplay {
    var cursorX = 0
    var cursorY = 0

    @JvmField
    var cursorVisible = true
    var alternateScreen = false
    var bracketedPaste = false
    var title = ""
    var mouseMode = MouseMode.MOUSE_REPORTING_NONE

    override fun setCursor(x: Int, y: Int) {
      cursorX = x
      cursorY = y
    }

    override fun setCursorShape(cursorShape: CursorShape?) {}
    override fun beep() {}
    override fun scrollArea(top: Int, height: Int, dy: Int) {}
    override fun setCursorVisible(visible: Boolean) {
      cursorVisible = visible
    }

    override fun useAlternateScreenBuffer(enabled: Boolean) {
      alternateScreen = enabled
    }

    override fun getWindowTitle(): String = title
    override fun setWindowTitle(windowTitle: String) {
      title = windowTitle
    }

    override fun getSelection(): TerminalSelection? = null
    override fun terminalMouseModeSet(mode: MouseMode) {
      mouseMode = mode
    }

    override fun setMouseFormat(mouseFormat: MouseFormat) {}
    override fun ambiguousCharsAreDoubleWidth(): Boolean = false
    override fun setBracketedPasteMode(enabled: Boolean) {
      bracketedPaste = enabled
    }

    override fun getWindowForeground(): Color? = null
    override fun getWindowBackground(): Color? = null
  }

  companion object {
    private const val ESC = "\u001b"
  }
}
