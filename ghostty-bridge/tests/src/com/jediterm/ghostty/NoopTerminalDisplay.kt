package com.jediterm.ghostty

import com.jediterm.terminal.CursorShape
import com.jediterm.terminal.TerminalDisplay
import com.jediterm.terminal.emulator.mouse.MouseFormat
import com.jediterm.terminal.emulator.mouse.MouseMode
import com.jediterm.terminal.model.TerminalSelection

/** No-op [TerminalDisplay] for headless tests. */
internal class NoopTerminalDisplay : TerminalDisplay {
  override fun setCursor(x: Int, y: Int) {}
  override fun setCursorShape(cursorShape: CursorShape?) {}
  override fun beep() {}
  override fun scrollArea(top: Int, height: Int, dy: Int) {}
  override fun setCursorVisible(visible: Boolean) {}
  override fun useAlternateScreenBuffer(enabled: Boolean) {}
  override fun getWindowTitle(): String = ""
  override fun setWindowTitle(windowTitle: String) {}
  override fun getSelection(): TerminalSelection? = null
  override fun terminalMouseModeSet(mode: MouseMode) {}
  override fun setMouseFormat(mouseFormat: MouseFormat) {}
  override fun ambiguousCharsAreDoubleWidth(): Boolean = false
  override fun setBracketedPasteMode(enabled: Boolean) {}
}
