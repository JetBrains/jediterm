package com.jediterm.ghostty

import com.jediterm.terminal.util.CharUtils
import org.junit.Test

import org.junit.Assert.assertEquals

/**
 * A ghostty-backed counterpart of `com.jediterm.TerminalTextBufferTest`.
 *
 * Instead of constructing `JediEmulator` / `JediTerminal`, each test feeds raw VT
 * byte sequences to the ghostty engine (via [GhosttyTestTerminal] &rarr; [GhosttyVt])
 * and then mirrors ghostty's screen + scrollback into a real `TerminalTextBuffer` (via
 * [GhosttyTextBufferSync]). The assertions are the same as the originals, proving the JediTerm data
 * model can be populated entirely by the reused ghostty emulator.
 *
 * The two lone-UTF-16-surrogate cases from the original test
 * (`testWideBmpAfterUnpairedHighSurrogate`, `testLoneLowSurrogate`) are intentionally
 * omitted: those exercise JediTerm's internal UTF-16 handling, but a byte-stream emulator's input
 * is UTF-8, which cannot represent a lone surrogate.
 */
class GhosttyTerminalTextBufferTest {

  @Test
  fun testEmptyLineTextStyle() {
    GhosttyTestTerminal(15, 10).use { terminal ->
      terminal.writeString("  1. line1")
      terminal.newLine()
      terminal.carriageReturn()
      terminal.writeString("  2. line2")
      terminal.newLine()
      terminal.carriageReturn()
      terminal.newLine()
      terminal.carriageReturn()
      terminal.newLine()
      terminal.carriageReturn()
      terminal.writeString("  3. line3")
      terminal.newLine()
      terminal.carriageReturn()
      terminal.newLine()
      terminal.carriageReturn()
      terminal.writeString("  4.")

      assertEquals(
        "  1. line1     \n" +
          "  2. line2     \n" +
          "               \n" +
          "               \n" +
          "  3. line3     \n" +
          "               \n" +
          "  4.           \n" +
          "               \n" +
          "               \n" +
          "               \n", terminal.getScreenLines()
      )
    }
  }

  @Test
  fun testAlternateBuffer() {
    GhosttyTestTerminal(5, 3).use { terminal ->
      terminal.writeString("1.")
      terminal.newLine()
      terminal.carriageReturn()
      terminal.writeString("2.")
      terminal.newLine()
      terminal.carriageReturn()

      terminal.useAlternateBuffer(true)

      assertEquals("     \n" +
        "     \n" +
        "     \n", terminal.getScreenLines())

      terminal.writeString("xxxxx")
      terminal.newLine()
      terminal.carriageReturn()
      terminal.writeString("yyyyy")
      terminal.newLine()
      terminal.carriageReturn()

      terminal.useAlternateBuffer(false)

      assertEquals("1.   \n" +
        "2.   \n" +
        "     \n", terminal.getScreenLines())
    }
  }

  @Test
  fun testInsertLine() {
    GhosttyTestTerminal(5, 3).use { terminal ->
      terminal.writeString("1")
      terminal.newLine()
      terminal.carriageReturn()
      terminal.writeString("2")
      terminal.newLine()
      terminal.carriageReturn()
      terminal.writeString("3")

      terminal.cursorPosition(1, 2)

      terminal.insertLines(1)

      terminal.writeString("3")

      assertEquals("1    \n" +
        "3    \n" +
        "2    \n", terminal.getScreenLines())
    }
  }

  @Test
  fun testInsertLine2() {
    GhosttyTestTerminal(5, 3).use { terminal ->
      terminal.writeString("1")
      terminal.newLine()
      terminal.carriageReturn()
      terminal.writeString("2")
      terminal.newLine()
      terminal.carriageReturn()
      terminal.writeString("3")

      terminal.cursorPosition(1, 1)

      terminal.insertLines(2)

      terminal.writeString("3")
      terminal.newLine()
      terminal.carriageReturn()

      assertEquals("3    \n" +
        "     \n" +
        "1    \n", terminal.getScreenLines())

      terminal.insertLines(20)

      assertEquals("3    \n" +
        "     \n" +
        "     \n", terminal.getScreenLines())
    }
  }

  @Test
  fun testInsertLineScrollingRegion() {
    GhosttyTestTerminal(5, 3).use { terminal ->
      terminal.writeString("1")
      terminal.newLine()
      terminal.carriageReturn()
      terminal.writeString("2")
      terminal.newLine()
      terminal.carriageReturn()
      terminal.writeString("=")

      terminal.setScrollingRegion(1, 2)

      terminal.cursorPosition(1, 1)

      terminal.insertLines(1)

      terminal.writeString("3")
      terminal.newLine()
      terminal.carriageReturn()

      assertEquals("3    \n" +
        "1    \n" +
        "=    \n", terminal.getScreenLines())
    }
  }

  @Test
  fun testInsertLineScrollingRegionManyLines() {
    GhosttyTestTerminal(5, 3).use { terminal ->
      terminal.writeString("1")
      terminal.newLine()
      terminal.carriageReturn()
      terminal.writeString("2")
      terminal.newLine()
      terminal.carriageReturn()
      terminal.writeString("=")

      terminal.setScrollingRegion(1, 2)

      terminal.cursorPosition(1, 1)

      terminal.insertLines(20)

      terminal.writeString("3")
      terminal.newLine()
      terminal.carriageReturn()

      assertEquals("3    \n" +
        "     \n" +
        "=    \n", terminal.getScreenLines())
    }
  }

  @Test
  fun testDeleteCharacters() {
    GhosttyTestTerminal(15, 3).use { terminal ->
      terminal.writeString("first line")
      terminal.newLine()
      terminal.carriageReturn()
      terminal.writeString("second line")
      terminal.newLine()
      terminal.carriageReturn()
      terminal.writeString("third line")

      assertEquals("first line     \n" +
        "second line    \n" +
        "third line     \n", terminal.getScreenLines())

      terminal.cursorPosition(1, 1)
      terminal.deleteCharacters(1)
      assertEquals("irst line      \n" +
        "second line    \n" +
        "third line     \n", terminal.getScreenLines())

      terminal.cursorPosition(6, 1)
      terminal.deleteCharacters(2)
      assertEquals("irst ne        \n" +
        "second line    \n" +
        "third line     \n", terminal.getScreenLines())

      terminal.cursorPosition(7, 2)
      terminal.deleteCharacters(42)
      assertEquals("irst ne        \n" +
        "second         \n" +
        "third line     \n", terminal.getScreenLines())

      terminal.cursorPosition(1, 3)
      terminal.deleteCharacters(6)
      assertEquals("irst ne        \n" +
        "second         \n" +
        "line           \n", terminal.getScreenLines())
    }
  }

  @Test
  fun testDeleteLines() {
    GhosttyTestTerminal(5, 5).use { terminal ->
      terminal.writeString("1")
      terminal.newLine()
      terminal.carriageReturn()
      terminal.writeString("2")
      terminal.newLine()
      terminal.carriageReturn()
      terminal.writeString("3")
      terminal.newLine()
      terminal.carriageReturn()
      terminal.writeString("4")
      terminal.newLine()
      terminal.carriageReturn()

      terminal.setScrollingRegion(1, 3)

      terminal.cursorPosition(1, 2)

      terminal.deleteLines(2)

      assertEquals("1    \n" +
        "     \n" +
        "     \n" +
        "4    \n" +
        "     \n", terminal.getScreenLines())
    }
  }

  @Test
  fun testDeleteManyLines() {
    GhosttyTestTerminal(5, 5).use { terminal ->
      terminal.writeString("1")
      terminal.newLine()
      terminal.carriageReturn()
      terminal.writeString("2")
      terminal.newLine()
      terminal.carriageReturn()
      terminal.writeString("3")
      terminal.newLine()
      terminal.carriageReturn()
      terminal.writeString("4")
      terminal.newLine()
      terminal.carriageReturn()

      terminal.setScrollingRegion(1, 3)

      terminal.cursorPosition(1, 2)

      terminal.deleteLines(20)

      assertEquals("1    \n" +
        "     \n" +
        "     \n" +
        "4    \n" +
        "     \n", terminal.getScreenLines())
    }
  }

  @Test
  fun testEraseCharacters() {
    GhosttyTestTerminal(5, 2).use { terminal ->
      terminal.writeString("11111")

      terminal.cursorPosition(2, 1)

      terminal.eraseCharacters(2)

      assertEquals("1  11\n" +
        "     \n", terminal.getScreenLines())

      terminal.eraseCharacters(10)

      assertEquals("1    \n" +
        "     \n", terminal.getScreenLines())
    }
  }

  @Test
  fun testInsertBlankCharacters() {
    GhosttyTestTerminal(10, 2).use { terminal ->
      terminal.writeString("11111")

      terminal.cursorPosition(2, 1)
      terminal.insertBlankCharacters(2)

      assertEquals("1  1111   \n" +
        "          \n", terminal.getScreenLines())
      terminal.cursorPosition(6, 1)
      terminal.insertBlankCharacters(4)

      assertEquals("1  11    1\n" +
        "          \n", terminal.getScreenLines())
    }
  }

  @Test
  fun testDoubleWidth() {
    GhosttyTestTerminal(10, 2).use { terminal ->
      terminal.writeString("生活習慣病")

      assertEquals(listOf(
        "生" + CharUtils.DWC + "活" + CharUtils.DWC + "習" + CharUtils.DWC + "慣" + CharUtils.DWC + "病" + CharUtils.DWC),
        terminal.getScreenLineTexts())
    }
  }

  @Test
  fun testEmojiDoubleWidth() {
    GhosttyTestTerminal(10, 2).use { terminal ->
      // Emoji-presentation characters (e.g. ✅ U+2705, ❌ U+274C) occupy two cells.
      terminal.writeString("✅a❌b")

      assertEquals(listOf("✅" + CharUtils.DWC + "a❌" + CharUtils.DWC + "b"),
        terminal.getScreenLineTexts())
    }
  }

  @Test
  fun testSupplementaryEmojiDoubleWidth() {
    GhosttyTestTerminal(10, 2).use { terminal ->
      // 😀 (U+1F600) is a supplementary-plane emoji: a surrogate pair spanning two cells, with no
      // DWC marker wedged between the halves (the pair already spans two cells).
      terminal.writeString("a😀b")

      assertEquals(listOf("a😀b"), terminal.getScreenLineTexts())
    }
  }

  @Test
  fun testMixedBmpAndSupplementaryDoubleWidth() {
    GhosttyTestTerminal(10, 2).use { terminal ->
      // BMP wide 生 gets a DWC placeholder; supplementary 😀 does not.
      terminal.writeString("生😀")

      assertEquals(listOf("生" + CharUtils.DWC + "😀"), terminal.getScreenLineTexts())
    }
  }

  @Test
  fun testBmpDoubleWidthAfterSurrogatePair() {
    GhosttyTestTerminal(10, 2).use { terminal ->
      // 😀 = two cells (no DWC), 生 = two cells (DWC), four cells total.
      terminal.writeString("😀生")

      assertEquals(listOf("😀生" + CharUtils.DWC), terminal.getScreenLineTexts())
    }
  }

  @Test
  fun testConsecutiveSupplementaryEmoji() {
    GhosttyTestTerminal(10, 2).use { terminal ->
      // Two supplementary emoji in a row: each surrogate pair spans two cells and gets no DWC.
      terminal.writeString("😀🚀")

      assertEquals(listOf("😀🚀"), terminal.getScreenLineTexts())
    }
  }
}
