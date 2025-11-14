package com.jediterm.terminal.model

// internal accessor to package-private stuff in TerminalLine
internal object TerminalLineUtil {
  internal fun incModificationCount(line: TerminalLine) {
    line.incrementAndGetModificationCount()
  }  

  internal fun getModificationCount(line: TerminalLine): Int = line.modificationCount  
}
