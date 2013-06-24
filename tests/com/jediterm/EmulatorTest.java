package com.jediterm;

import com.jediterm.terminal.ArrayTerminalDataStream;
import com.jediterm.terminal.Terminal;
import com.jediterm.terminal.TextStyle;
import com.jediterm.terminal.display.BackBuffer;
import com.jediterm.terminal.display.LinesBuffer;
import com.jediterm.terminal.display.StyleState;
import com.jediterm.terminal.emulator.ColorPalette;
import com.jediterm.terminal.emulator.Emulator;
import com.jediterm.terminal.emulator.JediEmulator;
import com.jediterm.util.BackBufferTerminal;
import com.jediterm.util.FileUtil;
import com.jediterm.util.NullTerminalOutputStream;
import junit.framework.TestCase;
import testData.TestPathsManager;

import java.awt.*;
import java.io.File;
import java.io.IOException;

/**
 * @author traff
 */
public class EmulatorTest extends TestCase {
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
    BackBuffer backBuffer = doTest();

    assertColor(backBuffer.getStyleAt(8, 2), ColorPalette.getIndexedColor(3), ColorPalette.getIndexedColor(4));
    assertColor(backBuffer.getStyleAt(23, 4), ColorPalette.getIndexedColor(7), ColorPalette.getIndexedColor(4));
    assertColor(backBuffer.getStyleAt(2, 0), ColorPalette.getIndexedColor(0), ColorPalette.getIndexedColor(6));
  }

  private static void assertColor(TextStyle style, Color foreground, Color background) {
    assertEquals(foreground, style.getForeground());
    assertEquals(background, style.getBackground());
  }

  private BackBuffer doTest() throws IOException {
    return doTest(80, 24);
  }

  private BackBuffer doTest(int width, int height) throws IOException {
    return doTest(width, height, FileUtil.loadFileLines(new File(TestPathsManager.getTestDataPath() + getName() + ".after.txt")));
  }

  private BackBuffer doTest(int width, int height, String expected) throws IOException {
    StyleState state = new StyleState();

    LinesBuffer scrollBuffer = new LinesBuffer();

    BackBuffer backBuffer = new BackBuffer(width, height, state);

    Terminal terminal = new BackBufferTerminal(backBuffer, state);

    ArrayTerminalDataStream
      fileStream = new ArrayTerminalDataStream(FileUtil.loadFileText(new File(TestPathsManager.getTestDataPath() + getName() + ".txt"),
                                                                     "UTF-8"));

    Emulator emulator = new JediEmulator(fileStream, new NullTerminalOutputStream(), terminal);

    while (emulator.hasNext()) {
      emulator.next();
    }

    assertEquals(expected, backBuffer.getLines());
    
    return backBuffer;
  }
}
