package com.jediterm.util

import com.jediterm.core.Platform
import com.jediterm.terminal.model.JediTerminal
import com.jediterm.terminal.model.StyleState
import com.jediterm.terminal.model.TerminalTextBuffer

internal object TerminalTestUtil {
  @JvmStatic
  fun createJediTerminal(buffer: TerminalTextBuffer, styleState: StyleState): JediTerminal {
    return JediTerminal(BackBufferDisplay(buffer), buffer, styleState, Platform.current())
  }
}