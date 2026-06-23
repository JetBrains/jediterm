package com.jediterm.ghostty

import org.junit.Test
import org.junit.Assert.assertEquals

/**
 * Ghostty-backed counterpart of `com.jediterm.BufferResizeTest`.
 *
 * Each test feeds VT byte sequences to the ghostty engine (including
 * `ghostty_terminal_resize`) and mirrors ghostty's screen + scrollback into a
 * `TerminalTextBuffer`; assertions match the originals.
 *
 * Unlike the plain VT operations in `GhosttyTerminalTextBufferTest`, resize-time reflow is
 * NOT standardized &mdash; ghostty has its own reflow algorithm. Where ghostty's result differs from
 * JediTerm's hand-rolled `doResizeTextBuffer`, that is an engine difference, not a bug. Two
 * originals are intentionally not ported (see the bottom of this file): the `MIN_WIDTH=5`
 * clamp case (a JediTerm UI policy, not emulator behavior) and the selection-point-tracking case (it
 * exercises JediTerm's own coordinate remapping during resize, which we no longer perform).
 */
class GhosttyBufferResizeTest {

  // ===================== main buffer: height-only resize =====================

  @Test
  fun mainBufferResizeToBiggerHeight() {
    GhosttyResizeSession(5, 5).use { session ->
      session.writeText("line\nline2\nline3\nli")
      session.assertCursorPosition(3, 4)

      session.resize(10, 10)

      assertEquals(0, session.historyLinesCount())
      assertEquals(padded(10, "line", "line2", "line3", "li", "", "", "", "", "", ""),
          session.getScreenLines())
      session.assertCursorPosition(3, 4)
    }
  }

  @Test
  fun mainBufferResizeToSmallerHeight() {
    GhosttyResizeSession(5, 5).use { session ->
      session.writeText("line\nline2\nline3\nli")
      session.assertCursorPosition(3, 4)

      session.resize(10, 2)

      assertEquals("line\nline2", session.historyLinesAsString())
      assertEquals(padded(10, "line3", "li"), session.getScreenLines())
      session.assertCursorPosition(3, 2)
    }
  }

  @Test
  fun mainBufferResizeToSmallerHeightAndBack() {
    GhosttyResizeSession(5, 5).use { session ->
      session.writeText("line\nline2\nline3\nline4\nli")
      session.assertCursorPosition(3, 5)

      session.resize(10, 2)

      assertEquals("line\nline2\nline3", session.historyLinesAsString())
      assertEquals(padded(10, "line4", "li"), session.getScreenLines())
      session.assertCursorPosition(3, 2)

      session.resize(5, 5)

      // ENGINE DIFFERENCE: when the screen grows again, ghostty leaves the rows it had moved to
      // scrollback there (the screen grows by adding blank rows at the bottom), whereas JediTerm
      // pulls scrollback back into the screen (history 0, "line".."li" restored, cursor 3,5).
      assertEquals(3, session.historyLinesCount())
      assertEquals("line\nline2\nline3", session.historyLinesAsString())
      assertEquals(padded(5, "line4", "li", "", "", ""), session.getScreenLines())
      session.assertCursorPosition(3, 2)
    }
  }

  @Test
  fun mainBufferResizeToSmallerHeightAndKeepCursorVisible() {
    GhosttyResizeSession(10, 4).use { session ->
      session.writeString("line1")
      session.crnl()
      session.writeString("line2")
      session.crnl()
      session.writeString("line3")
      session.crnl()

      session.assertCursorPosition(1, 4)

      session.resize(10, 3)
      assertEquals(listOf("line1"), session.historyLineTexts())
      assertEquals(listOf("line2", "line3"), session.screenLineTexts())
      session.assertCursorPosition(1, 3)
    }
  }

  @Test
  fun mainBufferResizeInHeightWithScrolling() {
    GhosttyResizeSession(5, 2).use { session ->
      // The original pre-seeds history with "line"/"line2"; the equivalent for a byte-stream engine
      // is to let those lines scroll off a 2-row screen.
      session.writeText("line\nline2\nline3\nli")
      session.assertCursorPosition(3, 2)

      session.resize(10, 5)

      assertEquals(0, session.historyLinesCount())
      assertEquals(padded(10, "line", "line2", "line3", "li", ""), session.getScreenLines())
      session.assertCursorPosition(3, 4)
    }
  }

  @Test
  fun mainBufferClearAndResizeVertically() {
    GhosttyResizeSession(10, 4).use { session ->
      session.writeText("hi>\nhi2>")

      session.clearScreen()

      session.cursorPosition(0, 0)
      session.writeString("hi3>")

      session.assertCursorPosition(5, 1)

      session.resize(10, 3)

      assertEquals("", session.historyLinesAsString())
      assertEquals(padded(10, "hi3>", "", ""), session.getScreenLines())
      session.assertCursorPosition(5, 1)
    }
  }

  @Test
  fun mainBufferInitialResize() {
    GhosttyResizeSession(10, 24).use { session ->
      session.writeString("hi>")

      session.assertCursorPosition(4, 1)

      session.resize(10, 3)

      assertEquals("", session.historyLinesAsString())
      assertEquals(padded(10, "hi>", "", ""), session.getScreenLines())
      session.assertCursorPosition(4, 1)
    }
  }

  // ===================== main buffer: width / both reflow =====================

  @Test
  fun mainBufferResizeWidthScenario1() {
    GhosttyResizeSession(15, 24).use { session ->
      session.writeString("$ cat long.txt")
      session.crnl()
      session.writeString("1_2_3_4_5_6_7_8")
      session.writeString("_9_10_11_12_13_")
      session.writeString("14_15_16_17_18_")
      session.writeString("19_20_21_22_23_")
      session.writeString("24_25_26")
      session.crnl()
      session.writeString("$ ")
      session.assertCursorPosition(3, 7)
      assertEquals("", session.historyLinesAsString())

      session.resize(20, 7)

      assertEquals("", session.historyLinesAsString())
      assertEquals(padded(20,
          "$ cat long.txt",
          "1_2_3_4_5_6_7_8_9_10",
          "_11_12_13_14_15_16_1",
          "7_18_19_20_21_22_23_",
          "24_25_26",
          "$ ",
          ""), session.getScreenLines())
      session.assertCursorPosition(3, 6)
    }
  }

  @Test
  fun mainBufferResizeWidthScenario2() {
    GhosttyResizeSession(100, 5).use { session ->
      session.writeString("$ cat long.txt")
      session.crnl()
      session.writeString("1_2_3_4_5_6_7_8_9_10_11_12_13_14_15_16_17_18_19_20_21_22_23_24_25_26_27_28_30")
      session.crnl()
      session.crnl()
      session.writeString("$ ")
      session.assertCursorPosition(3, 4)
      assertEquals("", session.historyLinesAsString())

      session.resize(6, 4)

      assertEquals(listOf(
          "$ cat ",
          "long.t",
          "xt",
          "1_2_3_",
          "4_5_6_",
          "7_8_9_",
          "10_11_",
          "12_13_",
          "14_15_",
          "16_17_",
          "18_19_",
          "20_21_",
          "22_23_",
          "24_25_").joinToString("\n"), session.historyLinesAsString())
      assertEquals(padded(6, "26_27_", "28_30", "", "$ "), session.getScreenLines())
      session.assertCursorPosition(3, 4)
    }
  }

  @Test
  fun mainBufferPointsTrackingDuringResize() {
    GhosttyResizeSession(10, 4).use { session ->
      session.writeText("line1\nline2\nline3\nline4")
      session.assertCursorPosition(6, 4)

      session.resize(5, 4)

      assertEquals(listOf("line1"), session.historyLineTexts())
      assertEquals(padded(5, "line2", "line3", "line4", ""), session.getScreenLines())
      session.assertCursorPosition(1, 4)
    }
  }

  @Test
  fun mainBufferResizeWidthIncrease() {
    GhosttyResizeSession(5, 5).use { session ->
      session.writeText("lin1\nlin2\nlin")
      session.assertCursorPosition(4, 3)

      session.resize(10, 5)

      assertEquals(0, session.historyLinesCount())
      assertEquals(padded(10, "lin1", "lin2", "lin", "", ""), session.getScreenLines())
      session.assertCursorPosition(4, 3)
    }
  }

  @Test
  fun mainBufferResizeWidthDecrease() {
    GhosttyResizeSession(10, 5).use { session ->
      session.writeText("line_one\nline_two\nline_thre\n")
      session.assertCursorPosition(1, 4)

      session.resize(5, 5)

      assertEquals("line_\none", session.historyLinesAsString())
      assertEquals(padded(5, "line_", "two", "line_", "thre", ""), session.getScreenLines())
      session.assertCursorPosition(1, 5)
    }
  }

  @Test
  fun mainBufferResizeBothDimensionsIncrease() {
    GhosttyResizeSession(5, 5).use { session ->
      session.writeText("lin1\nlin2\nlin3\nlin4\nlin")
      session.assertCursorPosition(4, 5)

      session.resize(10, 8)

      assertEquals(0, session.historyLinesCount())
      assertEquals(padded(10, "lin1", "lin2", "lin3", "lin4", "lin", "", "", ""),
          session.getScreenLines())
      session.assertCursorPosition(4, 5)
    }
  }

  @Test
  fun mainBufferResizeBothDimensionsDecrease() {
    GhosttyResizeSession(10, 8).use { session ->
      session.writeText("first_line\nsecond_lin\nthird_line\nfourth_lin\nfifth_line\nsixth_line\n")
      session.assertCursorPosition(1, 7)

      session.resize(5, 4)

      assertEquals(listOf(
          "first", "_line", "secon", "d_lin", "third", "_line", "fourt", "h_lin", "fifth").joinToString("\n"),
          session.historyLinesAsString())
      assertEquals(padded(5, "_line", "sixth", "_line", ""), session.getScreenLines())
      session.assertCursorPosition(1, 4)
    }
  }

  // ===================== alternate buffer (truncate/extend, no reflow) =====================

  @Test
  fun altBufferResizeWidthIncrease() {
    GhosttyResizeSession(5, 5).use { session ->
      session.useAlternateBuffer(true)
      session.writeText("lin1\nlin2\nlin")
      session.assertCursorPosition(4, 3)

      session.resize(10, 5)

      assertEquals(0, session.historyLinesCount())
      assertEquals(padded(10, "lin1", "lin2", "lin", "", ""), session.getScreenLines())
      session.assertCursorPosition(4, 3)
    }
  }

  @Test
  fun altBufferResizeWidthDecrease() {
    GhosttyResizeSession(10, 5).use { session ->
      session.useAlternateBuffer(true)
      session.writeText("line_one_A\nline_two_B\nline_thre")
      session.assertCursorPosition(10, 3)

      session.resize(5, 5)

      assertEquals(0, session.historyLinesCount())
      assertEquals(padded(5, "line_", "line_", "line_", "", ""), session.getScreenLines())
      session.assertCursorPosition(5, 3)
    }
  }

  @Test
  fun altBufferResizeHeightIncrease() {
    GhosttyResizeSession(5, 5).use { session ->
      session.useAlternateBuffer(true)
      session.writeText("lin1\nlin2\nlin3\nlin")
      session.assertCursorPosition(4, 4)

      session.resize(5, 8)

      assertEquals(0, session.historyLinesCount())
      assertEquals(padded(5, "lin1", "lin2", "lin3", "lin", "", "", "", ""), session.getScreenLines())
      session.assertCursorPosition(4, 4)
    }
  }

  @Test
  fun altBufferResizeHeightDecrease() {
    GhosttyResizeSession(5, 8).use { session ->
      session.useAlternateBuffer(true)
      session.writeText("lin1\nlin2\nlin3\nlin4\nlin5\nlin")
      session.assertCursorPosition(4, 6)

      session.resize(5, 4)

      assertEquals(0, session.historyLinesCount())
      // ENGINE DIFFERENCE: on an alternate-screen height shrink, ghostty keeps the rows around the
      // cursor (the bottom) so the cursor stays visible; JediTerm keeps the top rows
      // (lin1..lin4). The alt screen never reflows or scrolls back, so this is just a retention
      // choice and the running app would redraw anyway.
      assertEquals(padded(5, "lin3", "lin4", "lin5", "lin"), session.getScreenLines())
      session.assertCursorPosition(4, 4)
    }
  }

  @Test
  fun altBufferResizeBothDimensionsIncrease() {
    GhosttyResizeSession(5, 5).use { session ->
      session.useAlternateBuffer(true)
      session.writeText("AAA\nBBB\nCC")
      session.assertCursorPosition(3, 3)

      session.resize(10, 8)

      assertEquals(0, session.historyLinesCount())
      assertEquals(padded(10, "AAA", "BBB", "CC", "", "", "", "", ""), session.getScreenLines())
      session.assertCursorPosition(3, 3)
    }
  }

  @Test
  fun altBufferResizeBothDimensionsDecrease() {
    GhosttyResizeSession(10, 8).use { session ->
      session.useAlternateBuffer(true)
      session.writeText("0123456789\n1123456789\n2123456789\n3123456789\n4123456789\n512345678")
      session.assertCursorPosition(10, 6)

      session.resize(5, 4)

      assertEquals(0, session.historyLinesCount())
      // ENGINE DIFFERENCE: ghostty keeps the bottom (cursor) rows on alt-screen height shrink;
      // JediTerm keeps the top rows (01234..31234). See altBufferResizeHeightDecrease.
      assertEquals(padded(5, "21234", "31234", "41234", "51234"), session.getScreenLines())
      session.assertCursorPosition(5, 4)
    }
  }

  @Test
  fun altBufferResizeWidthIncreaseAndHeightDecrease() {
    GhosttyResizeSession(5, 8).use { session ->
      session.useAlternateBuffer(true)
      session.writeText("AAA\nBBB\nCCC\nDDD\nEEE\nFF")
      session.assertCursorPosition(3, 6)

      session.resize(10, 4)

      assertEquals(0, session.historyLinesCount())
      // ENGINE DIFFERENCE: ghostty keeps the bottom (cursor) rows on alt-screen height shrink;
      // JediTerm keeps the top rows (AAA..DDD). See altBufferResizeHeightDecrease.
      assertEquals(padded(10, "CCC", "DDD", "EEE", "FF"), session.getScreenLines())
      session.assertCursorPosition(3, 4)
    }
  }

  @Test
  fun altBufferResizeWidthDecreaseAndHeightIncrease() {
    GhosttyResizeSession(10, 4).use { session ->
      session.useAlternateBuffer(true)
      session.writeText("0123456789\n1123456789\n212345678")
      session.assertCursorPosition(10, 3)

      session.resize(5, 8)

      assertEquals(0, session.historyLinesCount())
      assertEquals(padded(5, "01234", "11234", "21234", "", "", "", "", ""), session.getScreenLines())
      session.assertCursorPosition(5, 3)
    }
  }

  // ===================== main-alternate-main switching =====================

  @Test
  fun altMainSwitchWidthChangeDuringAltBuffer() {
    GhosttyResizeSession(10, 5).use { session ->
      session.writeText("main_line1\nmain_line2\nmain_line")
      session.assertCursorPosition(10, 3)

      session.saveCursor()
      session.useAlternateBuffer(true)
      session.writeText("alt_content")

      session.resize(5, 5)

      session.restoreCursor()
      session.restoreCursor()
      session.useAlternateBuffer(false)

      // The reflowed main-buffer content matches JediTerm exactly.
      assertEquals("main_", session.historyLinesAsString())
      assertEquals(padded(5, "line1", "main_", "line2", "main_", "line"), session.getScreenLines())
      // ENGINE DIFFERENCE: the restored cursor position differs. DECSC/DECRC interacting with the
      // 1049 alt-screen save/restore (and the doubled restoreCursor here) resolves differently in
      // ghostty than in JediTerm, which expects (5,5).
      session.assertCursorPosition(1, 4)
    }
  }

  @Test
  fun altMainSwitchHeightChangeDuringAltBuffer() {
    GhosttyResizeSession(10, 8).use { session ->
      session.writeText("line1\nline2\nline3\nline4\nline5\nline")
      session.assertCursorPosition(5, 6)

      session.saveCursor()
      session.useAlternateBuffer(true)
      session.writeText("alt_data")

      session.resize(10, 4)

      session.restoreCursor()
      session.useAlternateBuffer(false)

      assertEquals("line1\nline2", session.historyLinesAsString())
      assertEquals(padded(10, "line3", "line4", "line5", "line"), session.getScreenLines())
      session.assertCursorPosition(5, 4)
    }
  }

  @Test
  fun altMainSwitchBothDimensionsChangeDuringAltBuffer() {
    GhosttyResizeSession(10, 8).use { session ->
      session.writeText("first_line\nsecond_lin\nthird_line\nfourth_lin\nfifth_line\nsixth_lin")
      session.assertCursorPosition(10, 6)

      session.saveCursor()
      session.useAlternateBuffer(true)
      session.writeText("alternate")

      session.resize(5, 4)

      session.restoreCursor()
      session.useAlternateBuffer(false)

      // The reflowed main-buffer content matches JediTerm exactly.
      assertEquals(listOf(
          "first", "_line", "secon", "d_lin", "third", "_line", "fourt", "h_lin").joinToString("\n"),
          session.historyLinesAsString())
      assertEquals(padded(5, "fifth", "_line", "sixth", "_lin"), session.getScreenLines())
      // ENGINE DIFFERENCE: restored cursor differs (see altMainSwitchWidthChangeDuringAltBuffer);
      // JediTerm expects (5,4).
      session.assertCursorPosition(1, 3)
    }
  }

  @Test
  fun altMainSwitchMultipleResizesDuringAltBuffer() {
    GhosttyResizeSession(10, 5).use { session ->
      session.writeText("main_lin1\nmain_lin2\nmain_lin3")
      session.assertCursorPosition(10, 3)

      session.saveCursor()
      session.useAlternateBuffer(true)
      session.writeText("alt")

      session.resize(8, 3)
      session.resize(6, 6)
      session.resize(5, 4)

      session.restoreCursor()
      session.useAlternateBuffer(false)

      // ENGINE DIFFERENCE: like mainBufferResizeToSmallerHeightAndBack, ghostty retains more in
      // scrollback after the final grow and anchors the screen/cursor differently than JediTerm
      // (which expects history "main_\nlin1", screen "main_/lin2/main_/lin3", cursor 5,4).
      assertEquals("main_\nlin1\nmain_", session.historyLinesAsString())
      assertEquals(padded(5, "lin2", "main_", "lin3", ""), session.getScreenLines())
      session.assertCursorPosition(2, 3)
    }
  }

  // Two originals from com.jediterm.BufferResizeTest are intentionally not ported:
  //  * `main buffer - type on last line and resize width` resizes to width 3 and relies on
  //    JediTerminal.MIN_WIDTH = 5 clamping it to 5. That clamp is a JediTerm UI policy, not VT/engine
  //    behavior — ghostty honors width 3 — so the scenario isn't meaningful against the engine.
  //  * `main buffer - selection after resize` asserts JediTerm's own selection-coordinate remapping
  //    across resize (SelectionUtil + display.selection). We delegate resize to ghostty and do not run
  //    JediTerm's resize point-tracking, so there is nothing equivalent to assert here.

  // Each line of `lines` is right-padded with spaces to `width`, joined by '\n' with a trailing '\n'
  // (matching the original test's paddedText + TerminalTextBuffer.getScreenLines()).
  private fun padded(width: Int, vararg lines: String): String {
    val sb = StringBuilder()
    for (line in lines) {
      sb.append(line)
      for (i in line.length until width) {
        sb.append(' ')
      }
      sb.append('\n')
    }
    return sb.toString()
  }
}
