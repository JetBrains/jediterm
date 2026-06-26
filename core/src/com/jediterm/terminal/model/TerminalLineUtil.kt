package com.jediterm.terminal.model

// internal accessor to package-private stuff in TerminalLine
internal object TerminalLineUtil {
  @JvmName("incModificationCount") // keep the name stable for Java callers
  internal fun incModificationCount(line: TerminalLine) {
    line.incrementAndGetModificationCount()
  }

  @JvmName("getModificationCount") // keep the name stable for Java callers
  internal fun getModificationCount(line: TerminalLine): Int = line.modificationCount
}
