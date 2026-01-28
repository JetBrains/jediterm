package com.jediterm

import com.jediterm.core.compatibility.Point
import com.jediterm.core.util.TermSize
import com.jediterm.terminal.RequestOrigin
import com.jediterm.terminal.model.*
import com.jediterm.util.TestSession
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.min

/**
 * @author traff
 */
class BufferResizeTest {
  @Test
  fun `main buffer - resize to bigger height`() {
    val session = TestSession(5, 5)
    val textBuffer = session.terminalTextBuffer
    val terminal = session.terminal

    terminal.writeText("""
      line
      line2
      line3
      li
    """.trimIndent())

    session.assertCursorPosition(3, 4)
    terminal.resize(TermSize(10, 10), RequestOrigin.User)

    assertEquals(0, textBuffer.historyLinesCount)
    assertEquals(paddedText(10, """
      line
      line2
      line3
      li






    """), textBuffer.getScreenLines())

    session.assertCursorPosition(3, 4)
  }


  @Test
  fun `main buffer - resize to smaller height`() {
    val session = TestSession(5, 5)
    val textBuffer = session.terminalTextBuffer
    val terminal = session.terminal

    terminal.writeText("""
      line
      line2
      line3
      li
    """.trimIndent())

    session.assertCursorPosition(3, 4)

    terminal.resize(TermSize(10, 2), RequestOrigin.User)

    assertEquals("""
      line
      line2
    """.trimIndent(), textBuffer.historyLinesStorage.getLinesAsString())

    assertEquals(paddedText(10, """
      line3
      li
    """), textBuffer.getScreenLines())

    session.assertCursorPosition(3, 2)
  }


  @Test
  fun `main buffer - resize to smaller height and back`() {
    val session = TestSession(5, 5)
    val textBuffer = session.terminalTextBuffer
    val terminal = session.terminal

    terminal.writeText("""
      line
      line2
      line3
      line4
      li
    """.trimIndent())

    session.assertCursorPosition(3, 5)

    terminal.resize(TermSize(10, 2), RequestOrigin.User)

    assertEquals("""
      line
      line2
      line3
    """.trimIndent(), textBuffer.historyLinesStorage.getLinesAsString())

    assertEquals(paddedText(10, """
      line4
      li
    """), textBuffer.getScreenLines())

    session.assertCursorPosition(3, 2)

    terminal.resize(TermSize(5, 5), RequestOrigin.User)

    assertEquals(0, textBuffer.historyLinesCount)

    assertEquals(paddedText(5, """
      line
      line2
      line3
      line4
      li
    """), textBuffer.getScreenLines())

    session.assertCursorPosition(3, 5)
  }

  @Test
  fun `main buffer - resize to smaller height and keep cursor visible`() {
    val session = TestSession(10, 4)
    val textBuffer = session.terminalTextBuffer
    val terminal = session.terminal

    terminal.writeString("line1")
    terminal.crnl()
    terminal.writeString("line2")
    terminal.crnl()
    terminal.writeString("line3")
    terminal.crnl()

    session.assertCursorPosition(1, 4)

    terminal.resize(TermSize(10, 3), RequestOrigin.User)
    assertEquals(mutableListOf("line1"), textBuffer.historyLinesStorage.getLineTexts())
    assertEquals(mutableListOf("line2", "line3"), textBuffer.screenLinesStorage.getLineTexts())
    session.assertCursorPosition(1, 3)
  }

  @Test
  fun `main buffer - resize in height with scrolling`() {
    val session = TestSession(5, 2)
    val terminal = session.terminal
    val textBuffer = session.terminalTextBuffer
    val scrollBuffer = textBuffer.historyLinesStorage

    scrollBuffer.addToBottom(terminalLine("line", session.currentStyle))
    scrollBuffer.addToBottom(terminalLine("line2", session.currentStyle))

    terminal.writeText("""
      line3
      li
    """.trimIndent())

    session.assertCursorPosition(3, 2)
    terminal.resize(TermSize(10, 5), RequestOrigin.User)

    assertEquals(0, scrollBuffer.size)

    assertEquals(paddedText(10, """
      line
      line2
      line3
      li

    """), textBuffer.getScreenLines())

    session.assertCursorPosition(3, 4)
  }


  @Test
  fun `main buffer - type on last line and resize width`() {
    val session = TestSession(6, 5)
    val terminal = session.terminal
    val textBuffer = session.terminalTextBuffer

    terminal.writeText("""
      >line1
      >line2
      >line3
      >line4
      >line5
      >
    """.trimIndent())

    assertEquals(">line1", textBuffer.historyLinesStorage.getLinesAsString())
    assertEquals(paddedText(6, """
      >line2
      >line3
      >line4
      >line5
      >
    """), textBuffer.getScreenLines())

    session.assertCursorPosition(2, 5)
    terminal.resize(TermSize(3, 5), RequestOrigin.User) // JediTerminal.MIN_WIDTH = 5

    assertEquals("""
      >line
      1
      >line
      2
      >line
      3
    """.trimIndent(), textBuffer.historyLinesStorage.getLinesAsString())
    assertEquals(paddedText(5, """
      >line
      4
      >line
      5
      >
    """), textBuffer.getScreenLines())

    session.assertCursorPosition(2, 5)

    terminal.resize(TermSize(6, 5), RequestOrigin.User)

    assertEquals(">line1", textBuffer.historyLinesStorage.getLinesAsString())
    assertEquals(paddedText(6, """
      >line2
      >line3
      >line4
      >line5
      >
    """), textBuffer.getScreenLines())

    session.assertCursorPosition(2, 5)
  }

  @Test
  fun `main buffer - selection after resize`() {
    val session = TestSession(6, 3)
    val terminal = session.terminal
    val textBuffer = session.terminalTextBuffer
    val display = session.display

    for (i in 1..5) {
      terminal.writeString(">line$i")
      terminal.crnl()
    }
    terminal.writeString(">")

    display.selection = TerminalSelection(Point(1, 0), Point(5, 1))

    var selectionText = SelectionUtil.getSelectionText(display.selection, textBuffer)

    assertEquals("""
      line4
      >line
    """.trimIndent(), selectionText)

    session.assertCursorPosition(2, 3)

    terminal.resize(TermSize(6, 5), RequestOrigin.User)

    assertEquals(selectionText, SelectionUtil.getSelectionText(display.selection, textBuffer))


    display.selection = TerminalSelection(Point(1, -2), Point(5, -1))

    selectionText = SelectionUtil.getSelectionText(display.selection, textBuffer)

    assertEquals("""
      line2
      >line
    """.trimIndent(), selectionText)

    session.assertCursorPosition(2, 3)

    terminal.resize(TermSize(6, 2), RequestOrigin.User)

    assertEquals(selectionText, SelectionUtil.getSelectionText(display.selection, textBuffer))

    session.assertCursorPosition(2, 2)
  }

  @Test
  fun `main buffer - clear and resize vertically`() {
    val session = TestSession(10, 4)
    val terminalTextBuffer = session.terminalTextBuffer
    val terminal = session.terminal

    terminal.writeText("""
      hi>
      hi2>
    """.trimIndent())

    terminal.clearScreen()

    terminal.cursorPosition(0, 0)
    terminal.writeString("hi3>")

    session.assertCursorPosition(5, 1)

    // smaller height
    terminal.resize(TermSize(10, 3), RequestOrigin.User)

    assertEquals("", terminalTextBuffer.historyLinesStorage.getLinesAsString())
    assertEquals(paddedText(10, """
      hi3>


    """), terminalTextBuffer.getScreenLines())

    session.assertCursorPosition(5, 1)
  }


  @Test
  fun `main buffer - initial resize`() {
    val session = TestSession(10, 24)
    val terminalTextBuffer = session.terminalTextBuffer
    val terminal = session.terminal

    terminal.writeString("hi>")

    session.assertCursorPosition(4, 1)
    // initial resize
    terminal.resize(TermSize(10, 3), RequestOrigin.User)

    assertEquals("", terminalTextBuffer.historyLinesStorage.getLinesAsString())
    assertEquals(paddedText(10, """
      hi>


    """), terminalTextBuffer.getScreenLines())

    session.assertCursorPosition(4, 1)
  }

  @Test
  fun `main buffer - resize width scenario 1`() {
    val session = TestSession(15, 24)
    val textBuffer = session.terminalTextBuffer
    val terminal = session.terminal

    terminal.writeString("$ cat long.txt")
    terminal.crnl()
    terminal.writeString("1_2_3_4_5_6_7_8")
    terminal.writeString("_9_10_11_12_13_")
    terminal.writeString("14_15_16_17_18_")
    terminal.writeString("19_20_21_22_23_")
    terminal.writeString("24_25_26")
    terminal.crnl()
    terminal.writeString("$ ")
    session.assertCursorPosition(3, 7)
    assertEquals("", textBuffer.historyLinesStorage.getLinesAsString())
    terminal.resize(TermSize(20, 7), RequestOrigin.User)

    assertEquals("", textBuffer.historyLinesStorage.getLinesAsString())
    assertEquals(paddedText(20, """
      $ cat long.txt
      1_2_3_4_5_6_7_8_9_10
      _11_12_13_14_15_16_1
      7_18_19_20_21_22_23_
      24_25_26
      $

    """), textBuffer.getScreenLines())
    session.assertCursorPosition(3, 6)
  }

  @Test
  fun `main buffer - resize width scenario 2`() {
    val session = TestSession(100, 5)
    val textBuffer = session.terminalTextBuffer
    val terminal = session.terminal

    terminal.writeString("$ cat long.txt")
    terminal.crnl()
    terminal.writeString("1_2_3_4_5_6_7_8_9_10_11_12_13_14_15_16_17_18_19_20_21_22_23_24_25_26_27_28_30")
    terminal.crnl()
    terminal.crnl()
    terminal.writeString("$ ")
    session.assertCursorPosition(3, 4)
    assertEquals("", textBuffer.historyLinesStorage.getLinesAsString())
    terminal.resize(TermSize(6, 4), RequestOrigin.User)

    assertEquals("""
      $ cat${" "}
      long.t
      xt
      1_2_3_
      4_5_6_
      7_8_9_
      10_11_
      12_13_
      14_15_
      16_17_
      18_19_
      20_21_
      22_23_
      24_25_
    """.trimIndent(), textBuffer.historyLinesStorage.getLinesAsString())
    assertEquals(paddedText(6, """
      26_27_
      28_30

      $
    """), textBuffer.getScreenLines())
    session.assertCursorPosition(3, 4)
  }

  @Test
  fun `main buffer - points tracking during resize`() {
    val session = TestSession(10, 4)
    val textBuffer = session.terminalTextBuffer
    val terminal = session.terminal

    terminal.writeText("""
      line1
      line2
      line3
      line4
    """.trimIndent())

    session.assertCursorPosition(6, 4)
    terminal.resize(TermSize(5, 4), RequestOrigin.User)

    val historyBuffer = textBuffer.historyLinesStorage
    assertEquals(1, historyBuffer.size)
    assertEquals("line1", historyBuffer[0].getText())

    assertEquals(paddedText(5, """
      line2
      line3
      line4

    """), textBuffer.getScreenLines())

    session.assertCursorPosition(1, 4)
  }

  @Test
  fun `main buffer - resize width increase`() {
    // Test: Increase width while keeping height constant
    // Expected: Content is reflowed to a new width, no history created
    val session = TestSession(5, 5)
    val textBuffer = session.terminalTextBuffer
    val terminal = session.terminal

    terminal.writeText("""
      lin1
      lin2
      lin
    """.trimIndent())
    session.assertCursorPosition(4, 3)

    // Increase width from 5 to 10
    terminal.resize(TermSize(10, 5), RequestOrigin.User)

    assertEquals(0, textBuffer.historyLinesCount)
    assertEquals(paddedText(10, """
      lin1
      lin2
      lin


    """), textBuffer.getScreenLines())
    session.assertCursorPosition(4, 3)
  }

  @Test
  fun `main buffer - resize width decrease`() {
    // Test: Decrease width causes line wrapping
    // Expected: Lines are split with soft wraps, may move some to history
    val session = TestSession(10, 5)
    val textBuffer = session.terminalTextBuffer
    val terminal = session.terminal

    terminal.writeText("""
      line_one
      line_two
      line_thre

    """.trimIndent())
    session.assertCursorPosition(1, 4)

    // Decrease width from 10 to 5
    terminal.resize(TermSize(5, 5), RequestOrigin.User)

    assertEquals("""
      line_
      one
    """.trimIndent(), textBuffer.historyLinesStorage.getLinesAsString())
    assertEquals(paddedText(5, """
      line_
      two
      line_
      thre

    """), textBuffer.getScreenLines())
    session.assertCursorPosition(1, 5)
  }

  @Test
  fun `main buffer - resize both dimensions increase`() {
    // Test: Increase both width and height
    // Expected: Content reflowed to new width, empty lines added at bottom
    val session = TestSession(5, 5)
    val textBuffer = session.terminalTextBuffer
    val terminal = session.terminal

    terminal.writeText("""
      lin1
      lin2
      lin3
      lin4
      lin
    """.trimIndent())
    session.assertCursorPosition(4, 5)

    // Increase both dimensions: 5x5 → 10x8
    terminal.resize(TermSize(10, 8), RequestOrigin.User)

    assertEquals(0, textBuffer.historyLinesCount)
    assertEquals(paddedText(10, """
      lin1
      lin2
      lin3
      lin4
      lin



    """), textBuffer.getScreenLines())
    session.assertCursorPosition(4, 5)
  }

  @Test
  fun `main buffer - resize both dimensions decrease`() {
    // Test: Decrease both width and height
    // Expected: Content reflowed, excess lines moved to history, cursor kept visible
    val session = TestSession(10, 8)
    val textBuffer = session.terminalTextBuffer
    val terminal = session.terminal

    terminal.writeText("""
      first_line
      second_lin
      third_line
      fourth_lin
      fifth_line
      sixth_line

    """.trimIndent())
    session.assertCursorPosition(1, 7)

    // Decrease both dimensions: 10x8 → 5x4
    terminal.resize(TermSize(5, 4), RequestOrigin.User)

    assertEquals("""
      first
      _line
      secon
      d_lin
      third
      _line
      fourt
      h_lin
      fifth
    """.trimIndent(), textBuffer.historyLinesStorage.getLinesAsString())
    assertEquals(paddedText(5, """
      _line
      sixth
      _line

    """), textBuffer.getScreenLines())
    session.assertCursorPosition(1, 4)
  }

  // ========== Alternate Buffer Tests ==========

  @Test
  fun `alt buffer - resize width increase`() {
    // Test: Alternate buffer width increase - content preserved, no reflow
    // Expected: Lines extended with spaces, no history created
    val session = TestSession(5, 5)
    val textBuffer = session.terminalTextBuffer
    val terminal = session.terminal

    terminal.useAlternateBuffer(true)
    terminal.writeText("""
      lin1
      lin2
      lin
    """.trimIndent())
    session.assertCursorPosition(4, 3)

    // Increase width from 5 to 10
    terminal.resize(TermSize(10, 5), RequestOrigin.User)

    assertEquals(0, textBuffer.historyLinesCount)
    assertEquals(paddedText(10, """
      lin1
      lin2
      lin


    """), textBuffer.getScreenLines())
    session.assertCursorPosition(4, 3)
  }

  @Test
  fun `alt buffer - resize width decrease`() {
    // Test: Alternate buffer width decrease - lines truncated, no wrapping
    // Expected: Lines truncated to new width, cursor clamped
    val session = TestSession(10, 5)
    val textBuffer = session.terminalTextBuffer
    val terminal = session.terminal

    terminal.useAlternateBuffer(true)
    terminal.writeText("""
      line_one_A
      line_two_B
      line_thre
    """.trimIndent())
    session.assertCursorPosition(10, 3)

    // Decrease width from 10 to 5
    terminal.resize(TermSize(5, 5), RequestOrigin.User)

    assertEquals(0, textBuffer.historyLinesCount)
    assertEquals(paddedText(5, """
      line_
      line_
      line_


    """), textBuffer.getScreenLines())
    session.assertCursorPosition(5, 3)
  }

  @Test
  fun `alt buffer - resize height increase`() {
    // Test: Alternate buffer height increase
    // Expected: Original lines preserved, empty lines at bottom
    val session = TestSession(5, 5)
    val textBuffer = session.terminalTextBuffer
    val terminal = session.terminal

    terminal.useAlternateBuffer(true)
    terminal.writeText("""
      lin1
      lin2
      lin3
      lin
    """.trimIndent())
    session.assertCursorPosition(4, 4)

    // Increase height from 5 to 8
    terminal.resize(TermSize(5, 8), RequestOrigin.User)

    assertEquals(0, textBuffer.historyLinesCount)
    assertEquals(paddedText(5, """
      lin1
      lin2
      lin3
      lin




    """), textBuffer.getScreenLines())
    session.assertCursorPosition(4, 4)
  }

  @Test
  fun `alt buffer - resize height decrease`() {
    // Test: Alternate buffer height decrease - lines truncated, NOT moved to history
    // Expected: Only first N lines visible, cursor clamped
    val session = TestSession(5, 8)
    val textBuffer = session.terminalTextBuffer
    val terminal = session.terminal

    terminal.useAlternateBuffer(true)
    terminal.writeText("""
      lin1
      lin2
      lin3
      lin4
      lin5
      lin
    """.trimIndent())
    session.assertCursorPosition(4, 6)

    // Decrease height from 8 to 4
    terminal.resize(TermSize(5, 4), RequestOrigin.User)

    assertEquals(0, textBuffer.historyLinesCount)
    assertEquals(paddedText(5, """
      lin1
      lin2
      lin3
      lin4
    """), textBuffer.getScreenLines())
    session.assertCursorPosition(4, 4)
  }

  @Test
  fun `alt buffer - resize both dimensions increase`() {
    // Test: Alternate buffer both dimensions increase
    // Expected: Content preserved in top-left, rest filled with spaces
    val session = TestSession(5, 5)
    val textBuffer = session.terminalTextBuffer
    val terminal = session.terminal

    terminal.useAlternateBuffer(true)
    terminal.writeText("""
      AAA
      BBB
      CC
    """.trimIndent())
    session.assertCursorPosition(3, 3)

    // Increase both dimensions: 5x5 → 10x8
    terminal.resize(TermSize(10, 8), RequestOrigin.User)

    assertEquals(0, textBuffer.historyLinesCount)
    assertEquals(paddedText(10, """
      AAA
      BBB
      CC





    """), textBuffer.getScreenLines())
    session.assertCursorPosition(3, 3)
  }

  @Test
  fun `alt buffer - resize both dimensions decrease`() {
    // Test: Alternate buffer both dimensions decrease
    // Expected: Content truncated to new bounds (top-left region only)
    val session = TestSession(10, 8)
    val textBuffer = session.terminalTextBuffer
    val terminal = session.terminal

    terminal.useAlternateBuffer(true)
    terminal.writeText("""
      0123456789
      1123456789
      2123456789
      3123456789
      4123456789
      512345678
    """.trimIndent())
    session.assertCursorPosition(10, 6)

    // Decrease both dimensions: 10x8 → 5x4
    terminal.resize(TermSize(5, 4), RequestOrigin.User)

    assertEquals(0, textBuffer.historyLinesCount)
    assertEquals(paddedText(5, """
      01234
      11234
      21234
      31234
    """), textBuffer.getScreenLines())
    session.assertCursorPosition(5, 4)
  }

  @Test
  fun `alt buffer - resize width increase and height decrease`() {
    // Test: Alternate buffer width increase, height decrease
    // Expected: Width extended, height truncated
    val session = TestSession(5, 8)
    val textBuffer = session.terminalTextBuffer
    val terminal = session.terminal

    terminal.useAlternateBuffer(true)
    terminal.writeText("""
      AAA
      BBB
      CCC
      DDD
      EEE
      FF
    """.trimIndent())
    session.assertCursorPosition(3, 6)

    // Resize: 5x8 → 10x4
    terminal.resize(TermSize(10, 4), RequestOrigin.User)

    assertEquals(0, textBuffer.historyLinesCount)
    assertEquals(paddedText(10, """
      AAA
      BBB
      CCC
      DDD
    """), textBuffer.getScreenLines())
    session.assertCursorPosition(3, 4)
  }

  @Test
  fun `alt buffer - resize width decrease and height increase`() {
    // Test: Alternate buffer width decrease, height increase
    // Expected: Width truncated, height extended
    val session = TestSession(10, 4)
    val textBuffer = session.terminalTextBuffer
    val terminal = session.terminal

    terminal.useAlternateBuffer(true)
    terminal.writeText("""
      0123456789
      1123456789
      212345678
    """.trimIndent())
    session.assertCursorPosition(10, 3)

    // Resize: 10x4 → 5x8
    terminal.resize(TermSize(5, 8), RequestOrigin.User)

    assertEquals(0, textBuffer.historyLinesCount)
    assertEquals(paddedText(5, """
      01234
      11234
      21234





    """), textBuffer.getScreenLines())
    session.assertCursorPosition(5, 3)
  }

  // ========== Main-Alternate-Main Switching Tests ==========

  @Test
  fun `alt-main switch - width change during alt buffer`() {
    // Test: Switch to alternate, resize width, switch back
    // Expected: Main buffer content is reflowed to a new width
    val session = TestSession(10, 5)
    val textBuffer = session.terminalTextBuffer
    val terminal = session.terminal

    // Write content to the main buffer
    terminal.writeText("""
      main_line1
      main_line2
      main_line
    """.trimIndent())
    session.assertCursorPosition(10, 3)

    // Switch to alternate buffer and write different content
    terminal.saveCursor()
    terminal.useAlternateBuffer(true)
    terminal.writeText("alt_content")

    // Resize width while in alternate buffer: 10 → 5
    terminal.resize(TermSize(5, 5), RequestOrigin.User)

    // Switch back to the main buffer
    terminal.restoreCursor()
    terminal.restoreCursor()
    terminal.useAlternateBuffer(false)

    // The main buffer should be reflowed to width=5
    // Some lines move to history due to height constraint
    assertEquals("""
      main_
    """.trimIndent(), textBuffer.historyLinesStorage.getLinesAsString())
    assertEquals(paddedText(5, """
      line1
      main_
      line2
      main_
      line
    """), textBuffer.getScreenLines())
    session.assertCursorPosition(5, 5)
  }

  @Test
  fun `alt-main switch - height change during alt buffer`() {
    // Test: Switch to alternate, resize height, switch back
    // Expected: Main buffer resized, excess moved to history
    val session = TestSession(10, 8)
    val textBuffer = session.terminalTextBuffer
    val terminal = session.terminal

    // Write content to the main buffer
    terminal.writeText("""
      line1
      line2
      line3
      line4
      line5
      line
    """.trimIndent())
    session.assertCursorPosition(5, 6)

    // Switch to an alternate buffer
    terminal.saveCursor()
    terminal.useAlternateBuffer(true)
    terminal.writeText("alt_data")

    // Resize height while in alternate buffer: 8 → 4
    terminal.resize(TermSize(10, 4), RequestOrigin.User)

    // Switch back to the main buffer
    terminal.restoreCursor()
    terminal.useAlternateBuffer(false)

    // The main buffer should be resized to 4 rows, excess in history
    assertEquals("""
      line1
      line2
    """.trimIndent(), textBuffer.historyLinesStorage.getLinesAsString())
    assertEquals(paddedText(10, """
      line3
      line4
      line5
      line
    """), textBuffer.getScreenLines())
    session.assertCursorPosition(5, 4)
  }

  @Test
  fun `alt-main switch - both dimensions change during alt buffer`() {
    // Test: Switch to alternate, resize both dimensions, switch back
    // Expected: Main buffer reflowed and resized to new dimensions
    val session = TestSession(10, 8)
    val textBuffer = session.terminalTextBuffer
    val terminal = session.terminal

    // Write content to the main buffer
    terminal.writeText("""
      first_line
      second_lin
      third_line
      fourth_lin
      fifth_line
      sixth_lin
    """.trimIndent())
    session.assertCursorPosition(10, 6)

    // Switch to an alternate buffer
    terminal.saveCursor()
    terminal.useAlternateBuffer(true)
    terminal.writeText("alternate")

    // Resize both dimensions while in alternate buffer: 10x8 → 5x4
    terminal.resize(TermSize(5, 4), RequestOrigin.User)

    // Switch back to the main buffer
    terminal.restoreCursor()
    terminal.useAlternateBuffer(false)

    // The main buffer should be reflowed to 5x4
    assertEquals("""
      first
      _line
      secon
      d_lin
      third
      _line
      fourt
      h_lin
    """.trimIndent(), textBuffer.historyLinesStorage.getLinesAsString())
    assertEquals(paddedText(5, """
      fifth
      _line
      sixth
      _lin
    """), textBuffer.getScreenLines())
    session.assertCursorPosition(5, 4)
  }

  @Test
  fun `alt-main switch - multiple resizes during alt buffer`() {
    // Test: Multiple resizes while in alternate buffer
    // Expected: Main buffer resized to the final size only
    val session = TestSession(10, 5)
    val textBuffer = session.terminalTextBuffer
    val terminal = session.terminal

    // Write content to the main buffer
    terminal.writeText("""
      main_lin1
      main_lin2
      main_lin3
    """.trimIndent())
    session.assertCursorPosition(10, 3)

    // Switch to an alternate buffer
    terminal.saveCursor()
    terminal.useAlternateBuffer(true)
    terminal.writeText("alt")

    // Perform multiple resizes: 10x5 → 8x3 → 6x6 → 5x4
    terminal.resize(TermSize(8, 3), RequestOrigin.User)
    terminal.resize(TermSize(6, 6), RequestOrigin.User)
    terminal.resize(TermSize(5, 4), RequestOrigin.User)

    // Switch back to the main buffer
    terminal.restoreCursor()
    terminal.useAlternateBuffer(false)

    // The main buffer should be reflowed to the final size 5x4
    // Some content may move to history due to a small height
    assertEquals("""
      main_
      lin1
    """.trimIndent(), textBuffer.historyLinesStorage.getLinesAsString())
    assertEquals(paddedText(5, """
      main_
      lin2
      main_
      lin3
    """), textBuffer.getScreenLines())
    session.assertCursorPosition(5, 4)
  }
}

/**
 * Each line of the original [content] is padded to [width] characters with spaces.
 */
private fun paddedText(width: Int, content: String): String {
  return content.trimIndent().lines().joinToString("\n") { line ->
    line.padEnd(width, ' ')
  } + "\n"
}

/**
 * Prints the multiline [text] to terminal with potential soft wraps.
 */
private fun JediTerminal.writeText(text: String) {
  val lines = text.split("\n")
  for (ind in lines.indices) {
    val line = lines[ind]

    var charInd = 0
    while (charInd < line.length) {
      val availableLen = min(line.length - charInd, terminalWidth)
      writeString(line.substring(charInd, charInd + availableLen))
      charInd += availableLen
    }

    if (ind != lines.lastIndex) {
      crnl()
    }
  }
}