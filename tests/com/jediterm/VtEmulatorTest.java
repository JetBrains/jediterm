package com.jediterm;

import com.google.common.collect.Sets;
import com.jediterm.terminal.Terminal;
import com.jediterm.terminal.display.BackBuffer;
import com.jediterm.terminal.display.StyleState;
import com.jediterm.terminal.emulator.Emulator;
import com.jediterm.terminal.emulator.JediEmulator;
import com.jediterm.util.BackBufferTerminal;
import com.jediterm.util.FileLoadableTerminalDataStream;
import com.jediterm.util.FileUtil;
import com.jediterm.util.NullTerminalOutputStream;
import junit.framework.TestCase;
import testData.TestPathsManager;

import java.io.File;
import java.io.IOException;
import java.util.Set;

/**
 * @author traff
 */
public class VtEmulatorTest extends TestCase {
  /**
   * Test of screen features 
   */
  public void testTest2_Screen() throws IOException {
    doVtTest(7, Sets.newHashSet(1));
  }

  private void doVtTest(int count) throws IOException {
    doVtTest(count, Sets.<Integer>newHashSet());
  }

  private void doVtTest(int count, Set<Integer> toSkip) throws IOException {
    StyleState state = new StyleState();

    BackBuffer backBuffer = new BackBuffer(80, 24, state);

    Terminal terminal = new BackBufferTerminal(backBuffer, state);

    FileLoadableTerminalDataStream dataStream = new FileLoadableTerminalDataStream();

    Emulator emulator = new JediEmulator(dataStream, new NullTerminalOutputStream(), terminal);

    String testFolder = TestPathsManager.getTestDataPath() + "vttest/" + getName().substring(4);
    
    for (int i = 1; i<=count;i++) {
      if (toSkip.contains(i)) {
        continue;
      }
      File file = new File(testFolder + "/" + i + ".txt");
      dataStream.load(file);

      while (emulator.hasNext()) {
        emulator.next();
      }

      String expected = FileUtil.loadFileLines(new File(testFolder + "/" + i + ".after.txt"));
      
      assertEquals("Test " + i + " failed", expected, backBuffer.getLines());
    }
  }
}
