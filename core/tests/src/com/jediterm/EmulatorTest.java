package com.jediterm;

import com.jediterm.terminal.TerminalColor;
import com.jediterm.terminal.emulator.ColorPalette;
import com.jediterm.terminal.model.TerminalTextBuffer;
import com.jediterm.util.TestSession;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;

import java.io.IOException;
import java.nio.file.Path;

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
    session.getDisplay().setWindowForeground(TerminalColor.rgb(16, 15, 14));
    session.process("\u001B]10;?\7");
    Assert.assertEquals("\033]10;rgb:1010/0f0f/0e0e\7", session.getTerminal().getOutputAndClear());

    session.process("\u001B]10;?\u001B\\");
    Assert.assertEquals("\033]10;rgb:1010/0f0f/0e0e\u001B\\", session.getTerminal().getOutputAndClear());
  }

  public void testOsc11Query() throws IOException {
    TestSession session = new TestSession(10, 10);
    session.getDisplay().setWindowBackground(TerminalColor.rgb(16, 15, 14));
    session.process("\u001B]11;?\7");
    Assert.assertEquals("\033]11;rgb:1010/0f0f/0e0e\7", session.getTerminal().getOutputAndClear());

    session.process("\u001B]11;?\u001B\\");
    Assert.assertEquals("\033]11;rgb:1010/0f0f/0e0e\u001B\\", session.getTerminal().getOutputAndClear());
  }

  @Override
  protected @NotNull Path getPathToTest() {
    return TestPathsManager.getTestDataPath().resolve(getName());
  }
}
