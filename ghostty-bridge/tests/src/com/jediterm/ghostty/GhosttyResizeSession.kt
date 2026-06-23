package com.jediterm.ghostty

import com.jediterm.terminal.model.StyleState
import com.jediterm.terminal.model.TerminalLine
import com.jediterm.terminal.model.TerminalTextBuffer
import org.junit.Assert

/**
 * Ghostty-backed counterpart of `com.jediterm.util.TestSession` for the resize tests.
 *
 * Drives a ghostty VT engine with byte sequences (including `ghostty_terminal_resize`) and, on
 * demand, snapshots ghostty's screen + scrollback into a fresh `TerminalTextBuffer` sized to
 * ghostty's current geometry. Because the snapshot is rebuilt for every read, the buffer always
 * reflects the post-resize dimensions without using JediTerm's own (now unused) reflow path.
 */
internal class GhosttyResizeSession(width: Int, height: Int) : AutoCloseable {

  private val vt: GhosttyVt = GhosttyVt(width, height, 5000L)

  fun terminalWidth(): Int = vt.cols()

  // ---- byte-sequence operations ----

  fun writeString(s: String) {
    vt.write(s)
  }

  /** CR + LF. */
  fun crnl() {
    vt.write("\r\n")
  }

  /** CUP (1-based); values &le; 0 clamp to 1 (home). */
  fun cursorPosition(x: Int, y: Int) {
    vt.write(ESC + "[" + maxOf(1, y) + ";" + maxOf(1, x) + "H")
  }

  /** DECSC. */
  fun saveCursor() {
    vt.write(ESC + "7")
  }

  /** DECRC. */
  fun restoreCursor() {
    vt.write(ESC + "8")
  }

  /** Erase entire display (ED 2); does not move the cursor or touch scrollback. */
  fun clearScreen() {
    vt.write(ESC + "[2J")
  }

  fun useAlternateBuffer(enabled: Boolean) {
    vt.write(ESC + "[?1049" + (if (enabled) "h" else "l"))
  }

  fun resize(width: Int, height: Int) {
    vt.resize(width, height)
  }

  /**
   * Mirrors the `JediTerminal.writeText` test helper: writes multi-line text, hard-wrapping each
   * logical line into terminal-width chunks (so the engine soft-wraps), and emitting CR+LF between
   * logical lines.
   */
  fun writeText(text: String) {
    val lines = text.split("\n")
    val width = vt.cols()
    for (i in lines.indices) {
      val line = lines[i]
      var charInd = 0
      while (charInd < line.length) {
        val availableLen = minOf(line.length - charInd, width)
        vt.write(line.substring(charInd, charInd + availableLen))
        charInd += availableLen
      }
      if (i != lines.size - 1) {
        crnl()
      }
    }
  }

  // ---- read back (each call re-snapshots ghostty into a fresh, correctly-sized buffer) ----

  fun snapshot(): TerminalTextBuffer {
    val buffer = TerminalTextBuffer(vt.cols(), vt.rows(), StyleState())
    GhosttyTextBufferSync.sync(vt, buffer)
    return buffer
  }

  fun getScreenLines(): String = snapshot().getScreenLines()

  fun historyLinesCount(): Int = snapshot().historyLinesCount

  fun screenLineTexts(): List<String> = lineTexts(snapshot().screenLinesStorage)

  fun historyLineTexts(): List<String> = lineTexts(snapshot().historyLinesStorage)

  /** Equivalent of `LinesStorage.getLinesAsString()`: line texts joined by '\n', no trailer. */
  fun historyLinesAsString(): String = historyLineTexts().joinToString("\n")

  fun assertCursorPosition(expectedOneBasedX: Int, expectedOneBasedY: Int) {
    Assert.assertEquals(
      "cursorX=$expectedOneBasedX, cursorY=$expectedOneBasedY",
      "cursorX=" + (vt.cursorX() + 1) + ", cursorY=" + (vt.cursorY() + 1))
  }

  private fun lineTexts(storage: Iterable<TerminalLine>): List<String> {
    val texts = ArrayList<String>()
    for (line in storage) {
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
