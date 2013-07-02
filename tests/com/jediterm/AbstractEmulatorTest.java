package com.jediterm;

import com.jediterm.terminal.ArrayTerminalDataStream;
import com.jediterm.terminal.Terminal;
import com.jediterm.terminal.TextStyle;
import com.jediterm.terminal.display.BackBuffer;
import com.jediterm.terminal.display.StyleState;
import com.jediterm.terminal.emulator.Emulator;
import com.jediterm.terminal.emulator.JediEmulator;
import com.jediterm.util.BackBufferTerminal;
import com.jediterm.util.FileUtil;
import com.jediterm.util.NullTerminalOutputStream;
import junit.framework.TestCase;

import java.awt.*;
import java.io.File;
import java.io.IOException;

/**
 * @author traff
 */
public abstract class AbstractEmulatorTest extends TestCase {
  protected static void assertColor(TextStyle style, Color foreground, Color background) {
    assertEquals(foreground, style.getForeground());
    assertEquals(background, style.getBackground());
  }

  protected BackBuffer doTest() throws IOException {
    return doTest(80, 24);
  }

  private BackBuffer doTest(int width, int height) throws IOException {
    return doTest(width, height, FileUtil.loadFileLines(new File(getPathToTest() + ".after.txt")));
  }

  protected BackBuffer doTest(int width, int height, String expected) throws IOException {
    StyleState state = new StyleState();

    BackBuffer backBuffer = new BackBuffer(width, height, state);

    Terminal terminal = new BackBufferTerminal(backBuffer, state);

    ArrayTerminalDataStream
      fileStream = new ArrayTerminalDataStream(FileUtil.loadFileText(new File(getPathToTest() + ".txt"),
                                                                     "UTF-8"));

    Emulator emulator = new JediEmulator(fileStream, new NullTerminalOutputStream(), terminal);

    while (emulator.hasNext()) {
      emulator.next();
    }

    assertEquals(expected, backBuffer.getLines());
    
    return backBuffer;
  }

  protected abstract String getPathToTest();
}
