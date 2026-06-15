package com.jediterm;

import com.jediterm.terminal.StyledTextConsumerAdapter;
import com.jediterm.terminal.TextStyle;
import com.jediterm.terminal.model.*;
import com.jediterm.util.BackBufferDisplay;
import com.jediterm.terminal.util.CharUtils;
import com.jediterm.util.TestSession;
import junit.framework.TestCase;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;

import java.util.List;

/**
 * @author traff
 */
public class TerminalTextBufferTest extends TestCase {
  public void testEmptyLineTextStyle() {
    StyleState state = new StyleState();

    TerminalTextBuffer terminalTextBuffer = new TerminalTextBuffer(15, 10, state);

    JediTerminal terminal = new JediTerminal(new BackBufferDisplay(terminalTextBuffer), terminalTextBuffer, state);

    terminal.writeString("  1. line1");
    terminal.newLine();
    terminal.carriageReturn();
    terminal.writeString("  2. line2");
    terminal.newLine();
    terminal.carriageReturn();
    terminal.newLine();
    terminal.carriageReturn();
    terminal.newLine();
    terminal.carriageReturn();
    terminal.writeString("  3. line3");
    terminal.newLine();
    terminal.carriageReturn();
    terminal.newLine();
    terminal.carriageReturn();
    terminal.writeString("  4.");

    terminalTextBuffer.processScreenLines(0, 10, new StyledTextConsumerAdapter() {
      @Override
      public void consume(int x, int y, @NotNull TextStyle style, @NotNull CharBuffer characters, int startRow) {
        assertNotNull(style);
      }
    });

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
        "               \n", terminalTextBuffer.getScreenLines()
    );
  }

  public void testAlternateBuffer() {
    StyleState state = new StyleState();

    TerminalTextBuffer terminalTextBuffer = new TerminalTextBuffer(5, 3, state);

    JediTerminal terminal = new JediTerminal(new BackBufferDisplay(terminalTextBuffer), terminalTextBuffer, state);

    terminal.writeString("1.");
    terminal.newLine();
    terminal.carriageReturn();
    terminal.writeString("2.");
    terminal.newLine();
    terminal.carriageReturn();

    terminal.useAlternateBuffer(true);

    assertEquals("     \n" +
      "     \n" +
      "     \n", terminalTextBuffer.getScreenLines());

    terminal.writeString("xxxxx");
    terminal.newLine();
    terminal.carriageReturn();
    terminal.writeString("yyyyy");
    terminal.newLine();
    terminal.carriageReturn();

    terminal.useAlternateBuffer(false);

    assertEquals("1.   \n" +
      "2.   \n" +
      "     \n", terminalTextBuffer.getScreenLines());
  }

  public void testInsertLine() {
    StyleState state = new StyleState();

    TerminalTextBuffer terminalTextBuffer = new TerminalTextBuffer(5, 3, state);

    JediTerminal terminal = new JediTerminal(new BackBufferDisplay(terminalTextBuffer), terminalTextBuffer, state);

    terminal.writeString("1");
    terminal.newLine();
    terminal.carriageReturn();
    terminal.writeString("2");
    terminal.newLine();
    terminal.carriageReturn();
    terminal.writeString("3");

    terminal.cursorPosition(1, 2);

    terminal.insertLines(1);

    terminal.writeString("3");

    assertEquals("1    \n" +
      "3    \n" +
      "2    \n", terminalTextBuffer.getScreenLines());

  }

  public void testInsertLine2() {
    StyleState state = new StyleState();

    TerminalTextBuffer terminalTextBuffer = new TerminalTextBuffer(5, 3, state);

    JediTerminal terminal = new JediTerminal(new BackBufferDisplay(terminalTextBuffer), terminalTextBuffer, state);

    terminal.writeString("1");
    terminal.newLine();
    terminal.carriageReturn();
    terminal.writeString("2");
    terminal.newLine();
    terminal.carriageReturn();
    terminal.writeString("3");

    terminal.cursorPosition(1, 1);

    terminal.insertLines(2);

    terminal.writeString("3");
    terminal.newLine();
    terminal.carriageReturn();

    assertEquals("3    \n" +
      "     \n" +
      "1    \n", terminalTextBuffer.getScreenLines());


    terminal.insertLines(20);


    assertEquals("3    \n" +
      "     \n" +
      "     \n", terminalTextBuffer.getScreenLines());

  }

  public void testInsertLineScrollingRegion() {
    StyleState state = new StyleState();

    TerminalTextBuffer terminalTextBuffer = new TerminalTextBuffer(5, 3, state);

    JediTerminal terminal = new JediTerminal(new BackBufferDisplay(terminalTextBuffer), terminalTextBuffer, state);

    terminal.writeString("1");
    terminal.newLine();
    terminal.carriageReturn();
    terminal.writeString("2");
    terminal.newLine();
    terminal.carriageReturn();
    terminal.writeString("=");

    terminal.setScrollingRegion(1, 2);

    terminal.cursorPosition(1, 1);

    terminal.insertLines(1);

    terminal.writeString("3");
    terminal.newLine();
    terminal.carriageReturn();

    assertEquals("3    \n" +
      "1    \n" +
      "=    \n", terminalTextBuffer.getScreenLines());
  }

  public void testInsertLineScrollingRegionManyLines() {
    StyleState state = new StyleState();

    TerminalTextBuffer terminalTextBuffer = new TerminalTextBuffer(5, 3, state);

    JediTerminal terminal = new JediTerminal(new BackBufferDisplay(terminalTextBuffer), terminalTextBuffer, state);

    terminal.writeString("1");
    terminal.newLine();
    terminal.carriageReturn();
    terminal.writeString("2");
    terminal.newLine();
    terminal.carriageReturn();
    terminal.writeString("=");

    terminal.setScrollingRegion(1, 2);

    terminal.cursorPosition(1, 1);

    terminal.insertLines(20);

    terminal.writeString("3");
    terminal.newLine();
    terminal.carriageReturn();

    assertEquals("3    \n" +
      "     \n" +
      "=    \n", terminalTextBuffer.getScreenLines());
  }


  public void testDeleteCharacters() {
    StyleState state = new StyleState();

    TerminalTextBuffer terminalTextBuffer = new TerminalTextBuffer(15, 3, state);

    JediTerminal terminal = new JediTerminal(new BackBufferDisplay(terminalTextBuffer), terminalTextBuffer, state);

    terminal.writeString("first line");
    terminal.newLine();
    terminal.carriageReturn();
    terminal.writeString("second line");
    terminal.newLine();
    terminal.carriageReturn();
    terminal.writeString("third line");

    assertEquals("first line     \n" +
      "second line    \n" +
      "third line     \n", terminalTextBuffer.getScreenLines());

    terminal.cursorPosition(1, 1);
    terminal.deleteCharacters(1);
    assertEquals("irst line      \n" +
      "second line    \n" +
      "third line     \n", terminalTextBuffer.getScreenLines());


    terminal.cursorPosition(6, 1);
    terminal.deleteCharacters(2);
    assertEquals("irst ne        \n" +
      "second line    \n" +
      "third line     \n", terminalTextBuffer.getScreenLines());

    terminal.cursorPosition(7, 2);
    terminal.deleteCharacters(42);
    assertEquals("irst ne        \n" +
      "second         \n" +
      "third line     \n", terminalTextBuffer.getScreenLines());

    terminal.cursorPosition(1, 3);
    terminal.deleteCharacters(6);
    assertEquals("irst ne        \n" +
      "second         \n" +
      "line           \n", terminalTextBuffer.getScreenLines());
  }


  public void testDeleteLines() {
    StyleState state = new StyleState();

    TerminalTextBuffer terminalTextBuffer = new TerminalTextBuffer(5, 5, state);

    JediTerminal terminal = new JediTerminal(new BackBufferDisplay(terminalTextBuffer), terminalTextBuffer, state);

    terminal.writeString("1");
    terminal.newLine();
    terminal.carriageReturn();
    terminal.writeString("2");
    terminal.newLine();
    terminal.carriageReturn();
    terminal.writeString("3");
    terminal.newLine();
    terminal.carriageReturn();
    terminal.writeString("4");
    terminal.newLine();
    terminal.carriageReturn();


    terminal.setScrollingRegion(1, 3);

    terminal.cursorPosition(1, 2);

    terminal.deleteLines(2);

    assertEquals("1    \n" +
      "     \n" +
      "     \n" +
      "4    \n" +
      "     \n", terminalTextBuffer.getScreenLines());
  }

  public void testDeleteManyLines() {
    StyleState state = new StyleState();

    TerminalTextBuffer terminalTextBuffer = new TerminalTextBuffer(5, 5, state);

    JediTerminal terminal = new JediTerminal(new BackBufferDisplay(terminalTextBuffer), terminalTextBuffer, state);

    terminal.writeString("1");
    terminal.newLine();
    terminal.carriageReturn();
    terminal.writeString("2");
    terminal.newLine();
    terminal.carriageReturn();
    terminal.writeString("3");
    terminal.newLine();
    terminal.carriageReturn();
    terminal.writeString("4");
    terminal.newLine();
    terminal.carriageReturn();


    terminal.setScrollingRegion(1, 3);

    terminal.cursorPosition(1, 2);

    terminal.deleteLines(20);

    assertEquals("1    \n" +
      "     \n" +
      "     \n" +
      "4    \n" +
      "     \n", terminalTextBuffer.getScreenLines());
  }

  public void testEraseCharacters() {
    StyleState state = new StyleState();

    TerminalTextBuffer terminalTextBuffer = new TerminalTextBuffer(5, 2, state);

    JediTerminal terminal = new JediTerminal(new BackBufferDisplay(terminalTextBuffer), terminalTextBuffer, state);

    terminal.writeString("11111");

    terminal.cursorPosition(2, 1);

    terminal.eraseCharacters(2);

    assertEquals("1  11\n" +
      "     \n", terminalTextBuffer.getScreenLines());

    terminal.eraseCharacters(10);

    assertEquals("1    \n" +
      "     \n", terminalTextBuffer.getScreenLines());

  }

  public void testInsertBlankCharacters() {
    StyleState state = new StyleState();

    TerminalTextBuffer terminalTextBuffer = new TerminalTextBuffer(10, 2, state);

    JediTerminal terminal = new JediTerminal(new BackBufferDisplay(terminalTextBuffer), terminalTextBuffer, state);

    terminal.writeString("11111");

    terminal.cursorPosition(2, 1);
    terminal.insertBlankCharacters(2);

    assertEquals("1  1111   \n" +
      "          \n", terminalTextBuffer.getScreenLines());
    terminal.cursorPosition(6, 1);
    terminal.insertBlankCharacters(4);

    assertEquals("1  11    1\n" +
      "          \n", terminalTextBuffer.getScreenLines());
  }

  public void testDoubleWidth() {
    TestSession session = new TestSession(10, 2);

    session.getTerminal().writeString("生活習慣病");

    assertScreenLines(session, List.of(
      "生" + CharUtils.DWC + "活" + CharUtils.DWC + "習" + CharUtils.DWC + "慣" + CharUtils.DWC + "病" + CharUtils.DWC));
  }

  public void testEmojiDoubleWidth() {
    TestSession session = new TestSession(10, 2);

    // Emoji-presentation characters (e.g. ✅ U+2705, ❌ U+274C) must occupy two cells,
    // otherwise text relying on that width (ASCII tables, progress bars, ...) gets misaligned.
    session.getTerminal().writeString("✅a❌b");

    assertScreenLines(session, List.of("✅" + CharUtils.DWC + "a❌" + CharUtils.DWC + "b"));
  }

  public void testSupplementaryEmojiDoubleWidth() {
    TestSession session = new TestSession(10, 2);

    // 😀 (U+1F600) is a supplementary-plane emoji stored as a UTF-16 surrogate pair. It must
    // occupy two cells with the pair kept contiguous and no DWC marker wedged between the halves (the
    // pair already spans two cells), otherwise tables relying on two-cell emoji get misaligned.
    session.getTerminal().writeString("a😀b");

    assertScreenLines(session, List.of("a😀b"));
  }

  public void testMixedBmpAndSupplementaryDoubleWidth() {
    TestSession session = new TestSession(10, 2);

    // BMP wide 生 gets a DWC placeholder; supplementary 😀 does not (its surrogate pair spans two cells).
    session.getTerminal().writeString("生😀");

    assertScreenLines(session, List.of("生" + CharUtils.DWC + "😀"));
  }

  public void testBmpDoubleWidthAfterSurrogatePair() {
    TestSession session = new TestSession(10, 2);

    // A BMP wide character (生) following a supplementary surrogate pair (😀) must still get
    // its DWC placeholder: 😀 = two cells (no DWC), 生 = two cells (DWC), four cells total.
    session.getTerminal().writeString("😀生");

    assertScreenLines(session, List.of("😀生" + CharUtils.DWC));
  }

  public void testConsecutiveSupplementaryEmoji() {
    TestSession session = new TestSession(10, 2);

    // Two supplementary emoji in a row: each surrogate pair spans two cells and gets no DWC; the
    // highSurrogateMet flag must reset between them so the second pair is handled correctly.
    session.getTerminal().writeString("😀🚀");

    assertScreenLines(session, List.of("😀🚀"));
  }

  public void testWideBmpAfterUnpairedHighSurrogate() {
    TestSession session = new TestSession(10, 2);

    // An unpaired high surrogate followed by a wide BMP char (生): because the next char is NOT a low
    // surrogate, 生 must still be treated normally and get its DWC (the lone high surrogate is one cell).
    session.getTerminal().writeString(((char) 0xD83D) + "生");

    assertScreenLines(session, List.of(((char) 0xD83D) + "生" + CharUtils.DWC));
  }

  public void testLoneLowSurrogate() {
    TestSession session = new TestSession(10, 2);

    // A lone low surrogate (no preceding high) is not double-width and gets no DWC.
    session.getTerminal().writeString("a" + ((char) 0xDE00) + "b");

    assertScreenLines(session, List.of("a" + ((char) 0xDE00) + "b"));
  }

  private void assertScreenLines(@NotNull TestSession session, @NotNull List<String> expectedScreenLines) {
    Assert.assertEquals(expectedScreenLines, TerminalLinesUtilKt.getLineTexts(session.getTerminalTextBuffer().getScreenLinesStorage()));
  }
}
