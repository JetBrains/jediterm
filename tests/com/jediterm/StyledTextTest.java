package com.jediterm;

import com.jediterm.emulator.TextStyle;
import com.jediterm.emulator.display.BackBuffer;
import com.jediterm.emulator.display.BufferedTerminalWriter;
import com.jediterm.emulator.display.LinesBuffer;
import com.jediterm.emulator.display.StyleState;
import com.jediterm.util.BackBufferDisplay;
import com.jediterm.util.TestStyle;
import junit.framework.TestCase;

import java.awt.*;

/**
 * @author traff
 */
public class StyledTextTest extends TestCase {
  private static final TextStyle GREEN = new TestStyle(6, Color.GREEN, null);
  private static final TextStyle BLACK = new TestStyle(4, Color.BLACK, null);


  public void testStyledTest1() {
    final int width = 12;
    final int height = 1;
    final String colors = "004 004 004 004 006 006 006 004 004 004 004 001 \n";

    StyleState state = new StyleState();

    LinesBuffer scrollBuffer = new LinesBuffer();

    BackBuffer backBuffer = new BackBuffer(width, height, state, scrollBuffer);

    BufferedTerminalWriter writer = new BufferedTerminalWriter(new BackBufferDisplay(backBuffer), backBuffer, state);

    BackBuffer backBuffer2 = new BackBuffer(width, height, state, new LinesBuffer());


    writer.setCharacterAttributes(new StyleState(BLACK));
    writer.writeString("def ");
    writer.setCharacterAttributes(new StyleState(GREEN));
    writer.writeString("foo");

    backBuffer.processDamagedCells(backBuffer2);
    backBuffer.resetDamage();

    writer.setCharacterAttributes(new StyleState(BLACK));
    writer.writeString("(x):");


    assertEquals(colors, backBuffer.getStyleLines());

    backBuffer.processDamagedCells(backBuffer2);

    assertEquals(colors, backBuffer2.getStyleLines());
  }
}
