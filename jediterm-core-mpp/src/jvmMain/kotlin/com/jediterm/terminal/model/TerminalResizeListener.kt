package com.jediterm.terminal.model

import com.jediterm.core.util.TermSize

interface TerminalResizeListener {
  fun onResize(oldTermSize: TermSize, newTermSize: TermSize)
}
