package com.jediterm;

import com.jediterm.terminal.model.TerminalTextBuffer;
import com.jediterm.terminal.emulator.ColorPalette;
import com.jediterm.util.TestSession;
import org.junit.Assert;

import java.io.IOException;

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

  @Override
  protected String getPathToTest() {
    return TestPathsManager.getTestDataPath() + getName();
  }
}
