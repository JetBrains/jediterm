package com.jediterm.ghostty

import com.jediterm.terminal.Terminal
import com.jediterm.terminal.TerminalDataStream
import com.jediterm.terminal.TerminalDisplay
import com.jediterm.terminal.TerminalStarter
import com.jediterm.terminal.TtyBasedArrayDataStream
import com.jediterm.terminal.TtyConnector
import com.jediterm.terminal.emulator.Emulator
import com.jediterm.terminal.model.JediTerminal
import com.jediterm.terminal.model.StyleState
import com.jediterm.terminal.model.TerminalTextBuffer
import com.jediterm.terminal.ui.JediTermWidget
import com.jediterm.terminal.ui.settings.SettingsProvider

/**
 * A [JediTermWidget] whose terminal engine is ghostty.
 *
 * It reuses the entire JediTerm Swing widget — the renderer (`TerminalPanel`), the scrollbar,
 * selection, search, and the keyboard/mouse/paste input stack — and only swaps the two stages that
 * constitute "the emulator": the VT parser and the model mutator. It does so via the two existing
 * factory seams:
 * - [createTerminal] returns a [GhosttyTerminalEngine] (a `JediTerminal` subclass) instead of a
 *   plain `JediTerminal`; and
 * - [createTerminalStarter] installs a [GhosttyEmulator] (feeding pty bytes to ghostty) in place of
 *   `JediEmulator`.
 *
 * Requires JDK 22+ (Java FFM) and the `ghostty.vt.lib` system property pointing at libghostty-vt;
 * launch with `--enable-native-access=ALL-UNNAMED`.
 */
open class GhosttyTermWidget : JediTermWidget {

  constructor(settingsProvider: SettingsProvider) : super(settingsProvider)

  constructor(columns: Int, lines: Int, settingsProvider: SettingsProvider) :
    super(columns, lines, settingsProvider)

  override fun createTerminal(
    display: TerminalDisplay,
    textBuffer: TerminalTextBuffer,
    initialStyleState: StyleState,
  ): JediTerminal {
    return GhosttyTerminalEngine(display, textBuffer, initialStyleState)
  }

  override fun createTerminalStarter(terminal: JediTerminal, connector: TtyConnector): TerminalStarter {
    return object : TerminalStarter(
      terminal, connector,
      TtyBasedArrayDataStream(connector) { typeAheadManager.onTerminalStateChanged() },
      typeAheadManager, executorServiceManager,
    ) {
      override fun createEmulator(dataStream: TerminalDataStream, t: Terminal): Emulator {
        return GhosttyEmulator(dataStream, t as GhosttyTerminalEngine)
      }
    }
  }
}
