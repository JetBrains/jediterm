package com.jediterm.terminal.model

// internal accessor to package-private stuff in TerminalLine
object TerminalLineUtil {
  @JvmStatic
  fun incModificationCount(line: TerminalLine) {
    line.incrementAndGetModificationCount()
  }

  @JvmStatic
  fun getModificationCount(line: TerminalLine): Int = line.modificationCount
}
