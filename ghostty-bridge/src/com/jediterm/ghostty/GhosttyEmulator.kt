package com.jediterm.ghostty

import com.jediterm.terminal.TerminalDataStream
import com.jediterm.terminal.emulator.Emulator
import java.io.IOException
import java.nio.charset.StandardCharsets

/**
 * An [Emulator] that replaces JediTerm's `JediEmulator`: instead of parsing the VT stream itself, it
 * drains a chunk of the pty's character stream and hands it to the ghostty engine.
 *
 * The data stream is character-based (the `TtyConnector` already decoded the pty's bytes with its
 * charset), whereas ghostty consumes raw UTF-8 bytes, so each chunk is re-encoded as UTF-8. For the
 * common UTF-8 pty case this round-trips losslessly.
 */
internal class GhosttyEmulator(
  private val dataStream: TerminalDataStream,
  private val engine: GhosttyTerminalEngine,
) : Emulator {

  private var eof = false

  override fun hasNext(): Boolean = !eof

  override fun resetEof() {
    eof = false
  }

  @Throws(IOException::class)
  override fun next() {
    try {
      val chunk = StringBuilder()
      chunk.append(dataStream.char) // blocks until data arrives (or EOF)
      while (!dataStream.isEmpty) {
        chunk.append(dataStream.char)
      }
      engine.processBytes(chunk.toString().toByteArray(StandardCharsets.UTF_8))
    } catch (e: TerminalDataStream.EOF) {
      eof = true
    }
  }
}
