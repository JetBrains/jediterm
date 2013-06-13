package com.jediterm;

import com.jediterm.emulator.ArrayTerminalDataStream;
import com.jediterm.emulator.Emulator;
import com.jediterm.emulator.Terminal;
import com.jediterm.emulator.TerminalDataStream;
import com.jediterm.emulator.display.BackBuffer;
import com.jediterm.emulator.display.LinesBuffer;
import com.jediterm.emulator.display.StyleState;
import com.jediterm.util.BackBufferTerminal;
import com.jediterm.util.FileUtil;
import junit.framework.TestCase;
import testData.TestPathsManager;

import java.io.File;
import java.io.IOException;

/**
 * @author traff
 */
public class EmulatorTest extends TestCase {
  public void testSetCursorPosition() throws IOException {
    StyleState state = new StyleState();

    LinesBuffer scrollBuffer = new LinesBuffer();

    BackBuffer backBuffer = new BackBuffer(3, 4, state, scrollBuffer);

    Terminal terminal = new BackBufferTerminal(backBuffer, state);

    ArrayTerminalDataStream fileStream = new ArrayTerminalDataStream(FileUtil.loadFileText(new File(TestPathsManager.getTestDataPath() + getName() + ".txt"),
                                                                                           "UTF-8"));

    Emulator emulator = new Emulator(fileStream, terminal);

    while (true) {
      try {
        emulator.processNextChar();
      }
      catch (TerminalDataStream.DisconnectedException e) {
        break;
      }
    }

    assertEquals("X00\n" +  //X wins
                 "0X \n" +
                 "X X\n" +
                 "   \n", backBuffer.getLines());
  }
}
