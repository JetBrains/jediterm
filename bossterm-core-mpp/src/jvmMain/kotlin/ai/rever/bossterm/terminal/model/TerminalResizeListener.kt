package ai.rever.bossterm.terminal.model

import ai.rever.bossterm.core.util.TermSize

interface TerminalResizeListener {
  fun onResize(oldTermSize: TermSize, newTermSize: TermSize)
}
