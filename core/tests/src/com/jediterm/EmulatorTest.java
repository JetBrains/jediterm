package com.jediterm;

import com.jediterm.core.Color;
import com.jediterm.core.util.CellPosition;
import com.jediterm.terminal.CursorShape;
import com.jediterm.terminal.emulator.ColorPalette;
import com.jediterm.terminal.model.TerminalLinesUtilKt;
import com.jediterm.terminal.model.TerminalTextBuffer;
import com.jediterm.util.TestSession;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * @author traff
 */
public class EmulatorTest extends EmulatorTestAbstract {
  public void testSetCursorPosition() throws IOException {
    doTest(3, 4, "X00\n" +  //X wins
            "0X \n" +
            "X X\n" +
            "   \n");
  }

  //public void testTooLargeScrollRegion() throws IOException { TODO: this test fails on Travis somehow
  //  doTest(80, 24);
  //}

  public void testMidnightCommanderOnVT100() throws IOException {
    doTest();
  }

  public void testMidnightCommanderOnXTerm() throws IOException {
    TerminalTextBuffer terminalTextBuffer = doTest();

    assertColor(terminalTextBuffer.getStyleAt(8, 2), ColorPalette.getIndexedTerminalColor(3), ColorPalette.getIndexedTerminalColor(4));
    assertColor(terminalTextBuffer.getStyleAt(23, 4), ColorPalette.getIndexedTerminalColor(7), ColorPalette.getIndexedTerminalColor(4));
    assertColor(terminalTextBuffer.getStyleAt(2, 0), ColorPalette.getIndexedTerminalColor(0), ColorPalette.getIndexedTerminalColor(6));
  }

  public void testEraseBeyondTerminalWidth() throws IOException {
    doTest();
  }

  public void testSystemCommands() throws IOException {
    doTest(30, 3);
  }

  public void testOscSetTitle() throws IOException {
    TestSession session = new TestSession(30, 3);
    session.process("\u001B]0;Title A\u001B\\Done1 ");
    Assert.assertEquals("Title A", session.getDisplay().getWindowTitle());
    session.process("\u001B]1;Title B\u001B\\Done2 ");
    Assert.assertEquals("Title B", session.getDisplay().getWindowTitle());
    session.process("\u001B]2;Title C\u001B\\Done3");
    Assert.assertEquals("Title C", session.getDisplay().getWindowTitle());
    Assert.assertEquals("Done1 Done2 Done3", session.getTerminal().getTextBuffer().getScreenLines().trim());
  }

  public void testOsc10Query() throws IOException {
    TestSession session = new TestSession(10, 10);
    session.getDisplay().setWindowForeground(new Color(16, 15, 14));
    session.process("\u001B]10;?\7");
    Assert.assertEquals("\033]10;rgb:1010/0f0f/0e0e\7", session.getTerminal().getOutputAndClear());

    session.process("\u001B]10;?\u001B\\");
    Assert.assertEquals("\033]10;rgb:1010/0f0f/0e0e\u001B\\", session.getTerminal().getOutputAndClear());
  }

  public void testOsc11Query() throws IOException {
    TestSession session = new TestSession(10, 10);
    session.getDisplay().setWindowBackground(new Color(16, 15, 14));
    session.process("\u001B]11;?\7");
    Assert.assertEquals("\033]11;rgb:1010/0f0f/0e0e\7", session.getTerminal().getOutputAndClear());

    session.process("\u001B]11;?\u001B\\");
    Assert.assertEquals("\033]11;rgb:1010/0f0f/0e0e\u001B\\", session.getTerminal().getOutputAndClear());
  }

  public void testResetToInitialState() throws IOException {
    TestSession session = new TestSession(20, 4);
    for (int i = 1; i <= 9; i++) {
      if (i > 1) {
        session.process("\r\n");
      }
      session.process("foo " + i);
    }

    assertScreenLines(session, List.of("foo 6", "foo 7", "foo 8", "foo 9"));
    assertHistoryLines(session, List.of("foo 1", "foo 2", "foo 3", "foo 4", "foo 5"));

    session.process(esc("c"));

    assertScreenLines(session, List.of(""));
    assertHistoryLines(session, List.of());
  }

  public void testSoftReset() throws IOException {
    TestSession session = new TestSession(20, 4);
    for (int i = 1; i <= 9; i++) {
      if (i > 1) {
        session.process("\r\n");
      }
      session.process("foo " + i);
    }

    assertScreenLines(session, List.of("foo 6", "foo 7", "foo 8", "foo 9"));
    assertHistoryLines(session, List.of("foo 1", "foo 2", "foo 3", "foo 4", "foo 5"));

    session.process(csi("!p"));

    assertScreenLines(session, List.of(""));
    assertHistoryLines(session, List.of("foo 1", "foo 2", "foo 3", "foo 4", "foo 5"));
  }

  public void testEraseInDisplay3() throws IOException {
    TestSession session = new TestSession(20, 2);
    for (int i = 1; i <= 5; i++) {
      if (i > 1) {
        session.process("\r\n");
      }
      session.process("foo " + i);
    }

    assertScreenLines(session, List.of("foo 4", "foo 5"));
    assertHistoryLines(session, List.of("foo 1", "foo 2", "foo 3"));

    session.process(csi("3J"));

    assertScreenLines(session, List.of("", ""));
    assertHistoryLines(session, List.of());
  }

  public void testSplitSurrogatePair() throws IOException {
    TestSession session = new TestSession(6, 3);
    // \uD83D (high surrogate) + \uDE00 (low surrogate) = 😀
    session.process("Hello\uD83D\uDE00\b, World!");
    // Hello<high surrogate>
    // , Worl
    // d!
    assertScreenLines(session, List.of(
      "Hello\uD83D",
      ", Worl",
      "d!"
    ));
  }

  public void testClear() throws IOException {
    TestSession session = new TestSession(10, 5);
    session.process(String.join("", List.of(
      // position the cursor at the bottom of the screen
      "\u001b[" + session.getTerminalTextBuffer().getHeight() + ";1H",

      "foo\r\nbar\r\nbaz",

      // move the cursor up
      "\u001b[A",

      // move the cursor to the beginning of the line
      "\r",

      // clear the screen from the cursor position to the end of the screen
      "\u001b[0J"
    )));
    assertScreenLines(session, List.of(
      "",
      "",
      "foo",
      "",
      ""
    ));
  }

  public void testCsiWithSpaceIntermediate() throws IOException {
    TestSession session = new TestSession(10, 2);
    session.process(String.join("", List.of(
      "0123456789",
      "\u001b[D" /* move the cursor left */,
      "\u001b[6 q" /* set the cursor shape to steady bar */
    )));
    assertScreenLines(session, List.of(
      "0123456789"
    ));
    assertEquals(CursorShape.STEADY_VERTICAL_BAR, session.getDisplay().getCursorShape());
    assertEquals(new CellPosition(10, 1), session.getTerminal().getCursorPosition());
  }

  public void testCharactersFromUnsupportedCsiAreNotPrinted() throws IOException {
    TestSession session = new TestSession(20, 2);
    session.process(String.join("", List.of(
      "foo",
      "\u001b[=5u", // https://sw.kovidgoyal.net/kitty/keyboard-protocol/#progressive-enhancement
      " bar",
      "\u001b[=0u",
      " baz",
      "\u001b[<u"  // https://sw.kovidgoyal.net/kitty/keyboard-protocol/#quickstart
    )));
    assertScreenLines(session, List.of(
      "foo bar baz"
    ));
  }

  private void assertScreenLines(@NotNull TestSession session, @NotNull List<String> expectedScreenLines) {
    Assert.assertEquals(expectedScreenLines, TerminalLinesUtilKt.getLineTexts(session.getTerminalTextBuffer().getScreenLinesStorage()));
  }

  private void assertHistoryLines(@NotNull TestSession session, @NotNull List<String> expectedHistoryLines) {
    Assert.assertEquals(expectedHistoryLines, TerminalLinesUtilKt.getLineTexts(session.getTerminalTextBuffer().getHistoryLinesStorage()));
  }

  @SuppressWarnings("SameParameterValue")
  private static @NotNull String esc(@NotNull String string) {
    return "\u001B" + string;
  }

  @SuppressWarnings("SameParameterValue")
  private static @NotNull String csi(@NotNull String string) {
    return "\u001B[" + string;
  }

  @Override
  protected @NotNull Path getPathToTest() {
    return TestPathsManager.getTestDataPath().resolve(getName());
  }
}
