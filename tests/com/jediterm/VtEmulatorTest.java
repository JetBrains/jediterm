package com.jediterm;

import com.google.common.collect.Sets;
import com.jediterm.terminal.ArrayTerminalDataStream;
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
public class VtEmulatorTest extends AbstractEmulatorTest {
  /**
   * Test of screen features
   */
  public void testTest2_Screen_1() throws IOException {
    doVtTest();
  }

  public void testTest2_Screen_2() throws IOException {
    doVtTest();
  }

  public void testTest2_Screen_3() throws IOException {
    doVtTest();
  }

  public void testTest2_Screen_4() throws IOException {
    doVtTest();
  }

  public void testTest2_Screen_5() throws IOException {
    doVtTest();
  }

  public void testTest2_Screen_6() throws IOException {
    doVtTest();
  }

  public void testTest2_Screen_7() throws IOException {
    doVtTest();
  }

  public void testTest2_Screen_8() throws IOException {
    doVtTest();
  }

  public void testTest2_Screen_9() throws IOException {
    doVtTest();
  }

  public void testTest2_Screen_10() throws IOException {
    doVtTest();
  }

  public void testTest2_Screen_11() throws IOException {
    doVtTest();
  }

  private void doVtTest() throws IOException {
    doTest();
  }

  @Override
  protected String getPathToTest() {
    String name = getName().substring(4);
    int ind = name.lastIndexOf("_");
    return TestPathsManager.getTestDataPath() + "vttest/" + name.substring(0, ind) + "/" + name.substring(ind+1);
  }
}
