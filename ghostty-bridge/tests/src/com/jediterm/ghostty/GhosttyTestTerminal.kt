package com.jediterm.ghostty

import com.jediterm.terminal.model.StyleState
import com.jediterm.terminal.model.TerminalTextBuffer

/**
 * Test convenience that drives a ghostty VT engine purely with byte sequences and mirrors the result
 * into a [TerminalTextBuffer] via [GhosttyTextBufferSync].
 *
 * This is deliberately *not* an implementation of `com.jediterm.terminal.Terminal`. Each method here
 * just encodes the equivalent VT escape sequence and hands the bytes to the engine — demonstrating
 * the "JediTerm passes byte sequences to ghostty and receives updates to TerminalTextBuffer" model.
 * The high-level method names exist only to keep the tests readable and close to the original
 * `TerminalTextBufferTest`.
 */
internal class GhosttyTestTerminal(width: Int, height: Int) : AutoCloseable {

  private val buffer: TerminalTextBuffer = TerminalTextBuffer(width, height, StyleState())
  private val vt: GhosttyVt = GhosttyVt(width, height, 5000L)

  fun buffer(): TerminalTextBuffer = buffer

  // ---- byte-sequence "operations" (each is just vt.write(...)) ----

  fun writeString(s: String) {
    vt.write(s)
  }

  fun newLine() {
    vt.write("\n") // LF (0x0A)
  }

  fun carriageReturn() {
    vt.write("\r") // CR (0x0D)
  }

  /** CUP: 1-based column [x], row [y]. */
  fun cursorPosition(x: Int, y: Int) {
    vt.write(ESC + "[" + y + ";" + x + "H")
  }

  /** DECSTBM: 1-based inclusive top/bottom. */
  fun setScrollingRegion(top: Int, bottom: Int) {
    vt.write(ESC + "[" + top + ";" + bottom + "r")
  }

  fun insertLines(count: Int) {
    vt.write(ESC + "[" + count + "L") // IL
  }

  fun deleteLines(count: Int) {
    vt.write(ESC + "[" + count + "M") // DL
  }

  fun deleteCharacters(count: Int) {
    vt.write(ESC + "[" + count + "P") // DCH
  }

  fun eraseCharacters(count: Int) {
    vt.write(ESC + "[" + count + "X") // ECH
  }

  fun insertBlankCharacters(count: Int) {
    vt.write(ESC + "[" + count + "@") // ICH
  }

  fun useAlternateBuffer(enabled: Boolean) {
    vt.write(ESC + "[?1049" + (if (enabled) "h" else "l"))
  }

  // ---- read back ----

  /** Sync ghostty's screen + scrollback into the buffer. */
  fun sync() {
    GhosttyTextBufferSync.sync(vt, buffer)
  }

  fun getScreenLines(): String {
    sync()
    return buffer.getScreenLines()
  }

  fun getScreenLineTexts(): List<String> {
    sync()
    val texts = ArrayList<String>()
    for (line in buffer.screenLinesStorage) {
      texts.add(line.text)
    }
    return texts
  }

  override fun close() {
    vt.close()
  }

  companion object {
    private const val ESC = "\u001b"
  }
}
