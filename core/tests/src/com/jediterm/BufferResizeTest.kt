package com.jediterm

import com.jediterm.core.compatibility.Point
import com.jediterm.core.util.TermSize
import com.jediterm.terminal.RequestOrigin
import com.jediterm.terminal.model.*
import com.jediterm.util.TestSession
import junit.framework.TestCase

/**
 * @author traff
 */
class BufferResizeTest : TestCase() {
  fun testResizeToBiggerHeight() {
    val session = TestSession(5, 5)
    val textBuffer = session.terminalTextBuffer
    val terminal = session.terminal

    terminal.writeString("line")
    terminal.newLine()
    terminal.carriageReturn()
    terminal.writeString("line2")
    terminal.newLine()
    terminal.carriageReturn()
    terminal.writeString("line3")
    terminal.newLine()
    terminal.carriageReturn()
    terminal.writeString("li")

    session.assertCursorPosition(3, 4)
    terminal.resize(TermSize(10, 10), RequestOrigin.User)

    assertEquals(0, textBuffer.historyLinesCount)

    assertEquals(
      "line      \n" +
        "line2     \n" +
        "line3     \n" +
        "li        \n" +
        "          \n" +
        "          \n" +
        "          \n" +
        "          \n" +
        "          \n" +
        "          \n",
      textBuffer.getScreenLines()
    )

    session.assertCursorPosition(3, 4)
  }


  fun testResizeToSmallerHeight() {
    val session = TestSession(5, 5)
    val textBuffer = session.terminalTextBuffer
    val terminal = session.terminal

    terminal.writeString("line")
    terminal.crnl()
    terminal.writeString("line2")
    terminal.crnl()
    terminal.writeString("line3")
    terminal.crnl()
    terminal.writeString("li")

    session.assertCursorPosition(3, 4)

    terminal.resize(TermSize(10, 2), RequestOrigin.User)

    assertEquals("line\n" + "line2", textBuffer.historyLinesStorage.getLinesAsString())
    assertEquals("line3     \n" + "li        \n", textBuffer.getScreenLines())

    session.assertCursorPosition(3, 2)
  }


  fun testResizeToSmallerHeightAndBack() {
    val session = TestSession(5, 5)
    val textBuffer = session.terminalTextBuffer
    val terminal = session.terminal

    terminal.writeString("line")
    terminal.newLine()
    terminal.carriageReturn()
    terminal.writeString("line2")
    terminal.newLine()
    terminal.carriageReturn()
    terminal.writeString("line3")
    terminal.newLine()
    terminal.carriageReturn()
    terminal.writeString("line4")
    terminal.newLine()
    terminal.carriageReturn()
    terminal.writeString("li")

    session.assertCursorPosition(3, 5)

    terminal.resize(TermSize(10, 2), RequestOrigin.User)


    assertEquals(
      "line\n" +
        "line2\n" +
        "line3",
      textBuffer.historyLinesStorage.getLinesAsString()
    )

    assertEquals(
      "line4     \n" +
        "li        \n",
      textBuffer.getScreenLines()
    )

    session.assertCursorPosition(3, 2)

    terminal.resize(TermSize(5, 5), RequestOrigin.User)

    assertEquals(0, textBuffer.historyLinesCount)

    assertEquals(
      "line \n" +
        "line2\n" +
        "line3\n" +
        "line4\n" +
        "li   \n", textBuffer.getScreenLines()
    )

    session.assertCursorPosition(3, 5)
  }

  fun testResizeToSmallerHeightAndKeepCursorVisible() {
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

  fun testResizeInHeightWithScrolling() {
    val session = TestSession(5, 2)
    val terminal = session.terminal
    val textBuffer = session.terminalTextBuffer
    val scrollBuffer = textBuffer.historyLinesStorage

    scrollBuffer.addToBottom(terminalLine("line", session.currentStyle))
    scrollBuffer.addToBottom(terminalLine("line2", session.currentStyle))

    terminal.writeString("line3")
    terminal.newLine()
    terminal.carriageReturn()
    terminal.writeString("li")

    session.assertCursorPosition(3, 2)
    terminal.resize(TermSize(10, 5), RequestOrigin.User)

    assertEquals(0, scrollBuffer.size)

    assertEquals(
      "line      \n" +
        "line2     \n" +
        "line3     \n" +
        "li        \n" +
        "          \n", textBuffer.getScreenLines()
    )

    session.assertCursorPosition(3, 4)
  }


  fun testTypeOnLastLineAndResizeWidth() {
    val session = TestSession(6, 5)
    val terminal = session.terminal
    val textBuffer = session.terminalTextBuffer

    terminal.writeString(">line1")
    terminal.newLine()
    terminal.carriageReturn()
    terminal.writeString(">line2")
    terminal.newLine()
    terminal.carriageReturn()
    terminal.writeString(">line3")
    terminal.newLine()
    terminal.carriageReturn()
    terminal.writeString(">line4")
    terminal.newLine()
    terminal.carriageReturn()
    terminal.writeString(">line5")
    terminal.newLine()
    terminal.carriageReturn()
    terminal.writeString(">")

    assertEquals(">line1", textBuffer.historyLinesStorage.getLinesAsString())
    assertEquals(
      ">line2\n" +
        ">line3\n" +
        ">line4\n" +
        ">line5\n" +
        ">     \n", textBuffer.getScreenLines()
    )

    session.assertCursorPosition(2, 5)
    terminal.resize(TermSize(3, 5), RequestOrigin.User) // JediTerminal.MIN_WIDTH = 5

    assertEquals(
      ">line\n" +
        "1\n" +
        ">line\n" +
        "2\n" +
        ">line\n" +
        "3", textBuffer.historyLinesStorage.getLinesAsString()
    )
    assertEquals(
      ">line\n" +
        "4    \n" +
        ">line\n" +
        "5    \n" +
        ">    \n", textBuffer.getScreenLines()
    )

    session.assertCursorPosition(2, 5)

    terminal.resize(TermSize(6, 5), RequestOrigin.User)

    assertEquals(">line1", textBuffer.historyLinesStorage.getLinesAsString())
    assertEquals(
      ">line2\n" +
        ">line3\n" +
        ">line4\n" +
        ">line5\n" +
        ">     \n", textBuffer.getScreenLines()
    )

    session.assertCursorPosition(2, 5)
  }

  fun testSelectionAfterResize() {
    val session = TestSession(6, 3)
    val terminal = session.terminal
    val textBuffer = session.terminalTextBuffer
    val display = session.getDisplay()

    for (i in 1..5) {
      terminal.writeString(">line" + i)
      terminal.newLine()
      terminal.carriageReturn()
    }
    terminal.writeString(">")

    display.setSelection(TerminalSelection(Point(1, 0), Point(5, 1)))

    var selectionText = SelectionUtil.getSelectionText(display.selection, textBuffer)

    assertEquals(
      "line4\n" +
        ">line", selectionText
    )

    session.assertCursorPosition(2, 3)

    terminal.resize(TermSize(6, 5), RequestOrigin.User)

    assertEquals(selectionText, SelectionUtil.getSelectionText(display.selection, textBuffer))


    display.selection = TerminalSelection(Point(1, -2), Point(5, -1))

    selectionText = SelectionUtil.getSelectionText(display.selection, textBuffer)

    assertEquals(
      "line2\n" +
        ">line", selectionText
    )

    session.assertCursorPosition(2, 3)

    terminal.resize(TermSize(6, 2), RequestOrigin.User)

    assertEquals(selectionText, SelectionUtil.getSelectionText(display.selection, textBuffer))

    session.assertCursorPosition(2, 2)
  }

  fun testClearAndResizeVertically() {
    val session = TestSession(10, 4)
    val terminalTextBuffer = session.terminalTextBuffer
    val terminal = session.terminal

    terminal.writeString("hi>")
    terminal.crnl()
    terminal.writeString("hi2>")

    terminal.clearScreen()

    terminal.cursorPosition(0, 0)
    terminal.writeString("hi3>")

    session.assertCursorPosition(5, 1)

    // smaller height
    terminal.resize(TermSize(10, 3), RequestOrigin.User)


    assertEquals("", terminalTextBuffer.historyLinesStorage.getLinesAsString())
    assertEquals(
      "hi3>      \n" +
        "          \n" +
        "          \n", terminalTextBuffer.getScreenLines()
    )

    session.assertCursorPosition(5, 1)
  }


  fun testInitialResize() {
    val session = TestSession(10, 24)
    val terminalTextBuffer = session.terminalTextBuffer
    val terminal = session.terminal

    terminal.writeString("hi>")

    session.assertCursorPosition(4, 1)
    // initial resize
    terminal.resize(TermSize(10, 3), RequestOrigin.User)


    assertEquals("", terminalTextBuffer.historyLinesStorage.getLinesAsString())
    assertEquals(
      "hi>       \n" +
        "          \n" +
        "          \n", terminalTextBuffer.getScreenLines()
    )

    session.assertCursorPosition(4, 1)
  }

  fun testResizeWidth1() {
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
    assertEquals(
      "$ cat long.txt      \n" +
        "1_2_3_4_5_6_7_8_9_10\n" +
        "_11_12_13_14_15_16_1\n" +
        "7_18_19_20_21_22_23_\n" +
        "24_25_26            \n" +
        "$                   \n" +
        "                    \n", textBuffer.getScreenLines()
    )
    session.assertCursorPosition(3, 6)
  }

  fun testResizeWidth2() {
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

    assertEquals(
      "$ cat \n" +
        "long.t\n" +
        "xt\n" +
        "1_2_3_\n" +
        "4_5_6_\n" +
        "7_8_9_\n" +
        "10_11_\n" +
        "12_13_\n" +
        "14_15_\n" +
        "16_17_\n" +
        "18_19_\n" +
        "20_21_\n" +
        "22_23_\n" +
        "24_25_", textBuffer.historyLinesStorage.getLinesAsString()
    )
    assertEquals(
      "26_27_\n" +
        "28_30 \n" +
        "      \n" +
        "$     \n", textBuffer.getScreenLines()
    )
    session.assertCursorPosition(3, 4)
  }

  fun testPointsTracking() {
    val session = TestSession(10, 4)
    val textBuffer = session.terminalTextBuffer
    val terminal = session.terminal

    terminal.writeString("line1")
    terminal.newLine()
    terminal.carriageReturn()
    terminal.writeString("line2")
    terminal.newLine()
    terminal.carriageReturn()
    terminal.writeString("line3")
    terminal.newLine()
    terminal.carriageReturn()
    terminal.writeString("line4")

    session.assertCursorPosition(6, 4)
    terminal.resize(TermSize(5, 4), RequestOrigin.User)

    val historyBuffer = textBuffer.historyLinesStorage
    assertEquals(1, historyBuffer.size)
    assertEquals("line1", historyBuffer.get(0).getText())

    assertEquals(
      "line2\n" +
        "line3\n" +
        "line4\n" +
        "     \n", textBuffer.getScreenLines()
    )

    session.assertCursorPosition(1, 4)
  }
}