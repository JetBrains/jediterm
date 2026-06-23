package com.jediterm.ghostty

import com.jediterm.terminal.TerminalColor
import com.jediterm.terminal.TextStyle
import com.jediterm.terminal.model.CharBuffer
import com.jediterm.terminal.model.TerminalLine
import com.jediterm.terminal.model.TerminalTextBuffer
import com.jediterm.terminal.util.CharUtils
import java.util.EnumSet

/**
 * Projects the ghostty engine's current screen + scrollback (content *and* per-cell style) onto a
 * JediTerm [TerminalTextBuffer].
 *
 * This is the "receive updates to TerminalTextBuffer" half of the integration: after feeding VT
 * bytes to [GhosttyVt], call [sync] to rebuild the buffer's screen and history line storages from
 * ghostty's grid. The buffer's own emulator-facing mutators (`writeString`, `insertLines`,
 * `useAlternateBuffer`, ...) are never used — we mirror whatever ghostty's *active* screen currently
 * is, which also transparently covers the alternate screen.
 *
 * ## Cell → char mapping
 * - An empty cell (codepoint 0) becomes a space; trailing empty cells are trimmed so a line's stored
 *   text matches how JediTerm itself stores lines (interior blanks are kept).
 * - A wide cell holding a BMP codepoint (e.g. CJK 生) emits the char followed by JediTerm's
 *   [CharUtils.DWC] placeholder for the trailing spacer cell.
 * - A wide cell holding a supplementary codepoint (e.g. 😀 U+1F600) emits the surrogate pair, which
 *   already spans two UTF-16 units / two columns, so *no* DWC placeholder.
 * - Spacer cells (the tail/head of a wide character) are skipped; the base cell produced their
 *   representation.
 *
 * Per cell, the ghostty `GhosttyStyle` (fg/bg as palette index or RGB + bold/italic/faint/blink/
 * inverse/invisible/underline) is mapped to a JediTerm [TextStyle], and consecutive cells sharing a
 * style are coalesced into a single text run.
 */
object GhosttyTextBufferSync {

  /** Rebuild [buffer]'s screen and history from [vt]'s current grid. */
  fun sync(vt: GhosttyVt, buffer: TerminalTextBuffer) {
    val cols = vt.cols()
    val rows = vt.rows()
    val cell = GhosttyVt.CellData()

    // Build everything from ghostty first (no buffer lock held during native reads).
    val screen = Array(rows) { y -> buildLine(vt, GhosttyVt.POINT_TAG_ACTIVE, y, cols, cell) }
    var lastNonEmptyRow = -1
    for (y in 0 until rows) {
      if (screen[y].text.isNotEmpty()) {
        lastNonEmptyRow = y
      }
    }

    val scrollbackRows = minOf(Int.MAX_VALUE.toLong(), vt.scrollbackRows()).toInt()
    val historyLines = ArrayList<TerminalLine>(maxOf(0, scrollbackRows))
    for (h in 0 until scrollbackRows) {
      historyLines.add(buildLine(vt, GhosttyVt.POINT_TAG_HISTORY, h, cols, cell))
    }

    val lastRow = lastNonEmptyRow
    buffer.modify {
      val history = buffer.historyLinesStorage
      history.clear()
      for (line in historyLines) {
        history.addToBottom(line)
      }

      val screenStorage = buffer.screenLinesStorage
      screenStorage.clear()
      // Materialize only up to the last non-empty row; trailing empty rows are produced on demand by
      // the storage (and padded to width by getScreenLines()), matching JediTerm semantics.
      for (y in 0..lastRow) {
        screenStorage.addToBottom(screen[y])
      }
    }
  }

  private fun buildLine(vt: GhosttyVt, pointTag: Int, y: Int, cols: Int, cell: GhosttyVt.CellData): TerminalLine {
    val chars = StringBuilder(cols)
    val styles = ArrayList<TextStyle>(cols) // one entry per appended char
    var significant = 0 // length up to (and including) the last meaningful char

    for (x in 0 until cols) {
      if (!vt.readCell(pointTag, x, y, cell)) {
        chars.append(' ')
        styles.add(TextStyle.EMPTY)
        continue
      }
      if (cell.wide == GhosttyVt.WIDE_SPACER_TAIL || cell.wide == GhosttyVt.WIDE_SPACER_HEAD) {
        continue // the base (wide) cell already emitted this character
      }
      if (cell.codepoint == 0) {
        chars.append(' ') // empty / erased cell
        styles.add(TextStyle.EMPTY)
        continue
      }
      val style = toTextStyle(cell)
      for (c in Character.toChars(cell.codepoint)) {
        chars.append(c)
        styles.add(style)
      }
      if (cell.wide == GhosttyVt.WIDE_WIDE && cell.codepoint <= 0xFFFF) {
        chars.append(CharUtils.DWC)
        styles.add(style)
      }
      significant = chars.length
    }

    if (significant == 0) {
      return TerminalLine.createEmpty()
    }

    // Coalesce consecutive equal styles into runs, appended via the public writeString API
    // (TerminalLine.appendEntry is package-private).
    val line = TerminalLine()
    var runStart = 0
    var idx = 0
    var runStyle = styles[0]
    for (i in 1 until significant) {
      if (styles[i] != runStyle) {
        line.writeString(idx, CharBuffer(chars.substring(runStart, i)), runStyle)
        idx += i - runStart
        runStart = i
        runStyle = styles[i]
      }
    }
    line.writeString(idx, CharBuffer(chars.substring(runStart, significant)), runStyle)
    return line
  }

  private fun toTextStyle(c: GhosttyVt.CellData): TextStyle {
    val fg = toColor(c.fgTag, c.fg)
    val bg = toColor(c.bgTag, c.bg)
    val options = EnumSet.noneOf(TextStyle.Option::class.java)
    if (c.bold) options.add(TextStyle.Option.BOLD)
    if (c.italic) options.add(TextStyle.Option.ITALIC)
    if (c.faint) options.add(TextStyle.Option.DIM)
    if (c.blink) options.add(TextStyle.Option.SLOW_BLINK)
    if (c.inverse) options.add(TextStyle.Option.INVERSE)
    if (c.invisible) options.add(TextStyle.Option.HIDDEN)
    if (c.underline != 0) options.add(TextStyle.Option.UNDERLINED)
    if (fg == null && bg == null && options.isEmpty()) {
      return TextStyle.EMPTY
    }
    return TextStyle(fg, bg, options)
  }

  private fun toColor(tag: Int, value: Int): TerminalColor? {
    if (tag == GhosttyVt.STYLE_COLOR_PALETTE) {
      return TerminalColor.index(value)
    }
    if (tag == GhosttyVt.STYLE_COLOR_RGB) {
      return TerminalColor.rgb((value shr 16) and 0xFF, (value shr 8) and 0xFF, value and 0xFF)
    }
    return null // STYLE_COLOR_NONE -> use the panel's default fg/bg
  }
}
