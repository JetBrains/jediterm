package com.jediterm.ghostty.demo

import com.jediterm.core.util.TermSize
import com.jediterm.terminal.ProcessTtyConnector
import com.pty4j.PtyProcess
import com.pty4j.WinSize
import java.nio.charset.Charset

/** Minimal pty4j-backed [com.jediterm.terminal.TtyConnector] for the demo / integration test. */
class DemoPtyConnector(
  private val process: PtyProcess,
  charset: Charset,
  commandLine: List<String>,
) : ProcessTtyConnector(process, charset, commandLine) {

  override fun resize(termSize: TermSize) {
    if (isConnected) {
      process.setWinSize(WinSize(termSize.columns, termSize.rows))
    }
  }

  override fun isConnected(): Boolean = process.isAlive

  override fun getName(): String = "ghostty-demo"
}
