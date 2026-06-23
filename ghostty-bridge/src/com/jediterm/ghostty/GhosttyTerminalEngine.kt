package com.jediterm.ghostty

import com.jediterm.core.util.TermSize
import com.jediterm.terminal.RequestOrigin
import com.jediterm.terminal.TerminalDisplay
import com.jediterm.terminal.TerminalOutputStream
import com.jediterm.terminal.emulator.mouse.MouseFormat
import com.jediterm.terminal.emulator.mouse.MouseMode
import com.jediterm.terminal.model.JediTerminal
import com.jediterm.terminal.model.StyleState
import com.jediterm.terminal.model.TerminalTextBuffer

/**
 * A [JediTerminal] whose screen is driven by the ghostty VT engine instead of JediTerm's built-in
 * emulator.
 *
 * It extends `JediTerminal` purely to *reuse* the parts the Swing widget needs that are unrelated to
 * VT parsing: keyboard encoding (`getCodeForKey`), mouse-report encoding (it is the
 * `TerminalMouseListener`), the `TerminalCoordinates` accessor, and the `TerminalOutputStream`
 * plumbing. The inherited emulator-mutator methods (`writeCharacters`, `cursorPosition`,
 * `insertLines`, ...) are never invoked, because there is no `JediEmulator`: raw pty bytes go
 * straight to [GhosttyVt] via [GhosttyEmulator], and [processBytes] mirrors ghostty's screen +
 * scrollback back into the shared [TerminalTextBuffer] and pushes cursor/title/mode state to the
 * display.
 *
 * ghostty is touched from two threads here (the VT read loop and the resize executor), so all engine
 * access is serialized on [lock]; [GhosttyVt] uses a shared arena to permit it.
 */
open class GhosttyTerminalEngine(
  private val display: TerminalDisplay,
  private val buffer: TerminalTextBuffer,
  styleState: StyleState,
) : JediTerminal(display, buffer, styleState) {

  private val vt: GhosttyVt = GhosttyVt(buffer.width, buffer.height, MAX_SCROLLBACK)
  private val lock = Any()

  @Volatile
  private var output: TerminalOutputStream? = null
  private var lastTitle = ""

  // ghostty's last-synced cursor (0-based), cached so getCursorX()/getCursorY() — read off the EDT
  // by the widget's type-ahead model to paint the cursor — never touch the (non-thread-safe) engine.
  @Volatile
  private var lastCursorX = 0

  @Volatile
  private var lastCursorY = 0

  init {
    vt.setWritePtyCallback(this::onEngineWrite)
  }

  /** Feed a chunk of the shell's output (UTF-8) to ghostty, then mirror + drive the UI. */
  internal fun processBytes(utf8: ByteArray) {
    synchronized(lock) {
      vt.write(utf8)
      syncAndDrive()
    }
  }

  override fun resize(newTermSize: TermSize, origin: RequestOrigin) {
    synchronized(lock) {
      // Reuse JediTerminal's size bookkeeping (width/height fields, buffer size, display.onResize).
      // Its reflow of the buffer is harmless: syncAndDrive() immediately overwrites the content from
      // ghostty, which performed the authoritative reflow.
      super.resize(newTermSize, origin)
      vt.resize(newTermSize.columns, newTermSize.rows)
      syncAndDrive()
    }
  }

  override fun setTerminalOutput(terminalOutput: TerminalOutputStream?) {
    super.setTerminalOutput(terminalOutput)
    this.output = terminalOutput
  }

  override fun disconnected() {
    super.disconnected()
    synchronized(lock) {
      vt.close()
    }
  }

  // The Swing widget paints the cursor column via the type-ahead model, which reads getCursorX() /
  // getCursorY() (JediTerminal's own cursor state) rather than the display.setCursor() values. Since
  // ghostty — not JediTerminal — drives the screen, those fields are never advanced, which left the
  // painted cursor stuck at home (1,1). Report ghostty's cursor instead (JediTerminal: 1-based).
  override fun getCursorX(): Int = lastCursorX + 1

  override fun getCursorY(): Int = lastCursorY + 1

  private fun onEngineWrite(data: ByteArray) {
    output?.sendBytes(data, false) // DSR / device-attributes / mode reports -> pty
  }

  private fun syncAndDrive() {
    GhosttyTextBufferSync.sync(vt, buffer) // also fires modelChanged -> panel repaint

    // Cursor: JediTerm's display convention is 0-based X, 1-based Y; ghostty reports both 0-based.
    lastCursorX = vt.cursorX()
    lastCursorY = vt.cursorY()
    display.setCursor(lastCursorX, lastCursorY + 1)
    display.setCursorVisible(vt.cursorVisible())
    display.useAlternateScreenBuffer(vt.altScreen())

    // Input-relevant modes, so the inherited getCodeForKey / mouse / paste behave correctly.
    setApplicationArrowKeys(vt.modeEnabled(GhosttyVt.MODE_DECCKM))
    setApplicationKeypad(vt.modeEnabled(GhosttyVt.MODE_KEYPAD_KEYS))
    display.setBracketedPasteMode(vt.modeEnabled(GhosttyVt.MODE_BRACKETED_PASTE))
    setMouseMode(currentMouseMode())
    setMouseFormat(currentMouseFormat())

    val title = vt.title()
    if (title != lastTitle) {
      lastTitle = title
      display.setWindowTitle(title)
    }
  }

  private fun currentMouseMode(): MouseMode {
    if (vt.modeEnabled(GhosttyVt.MODE_ANY_MOUSE)) return MouseMode.MOUSE_REPORTING_ALL_MOTION
    if (vt.modeEnabled(GhosttyVt.MODE_BUTTON_MOUSE)) return MouseMode.MOUSE_REPORTING_BUTTON_MOTION
    if (vt.modeEnabled(GhosttyVt.MODE_NORMAL_MOUSE)) return MouseMode.MOUSE_REPORTING_NORMAL
    if (vt.modeEnabled(GhosttyVt.MODE_X10_MOUSE)) return MouseMode.MOUSE_REPORTING_NORMAL
    return MouseMode.MOUSE_REPORTING_NONE
  }

  private fun currentMouseFormat(): MouseFormat {
    if (vt.modeEnabled(GhosttyVt.MODE_SGR_MOUSE) || vt.modeEnabled(GhosttyVt.MODE_SGR_PIXELS_MOUSE)) {
      return MouseFormat.MOUSE_FORMAT_SGR
    }
    if (vt.modeEnabled(GhosttyVt.MODE_URXVT_MOUSE)) return MouseFormat.MOUSE_FORMAT_URXVT
    if (vt.modeEnabled(GhosttyVt.MODE_UTF8_MOUSE)) return MouseFormat.MOUSE_FORMAT_XTERM_EXT
    return MouseFormat.MOUSE_FORMAT_XTERM
  }

  companion object {
    private const val MAX_SCROLLBACK = 5000L
  }
}
