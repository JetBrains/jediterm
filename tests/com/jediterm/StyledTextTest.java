package com.jediterm;

import com.jediterm.terminal.TerminalColor;
import com.jediterm.terminal.TextStyle;
import com.jediterm.terminal.display.BackBuffer;
import com.jediterm.terminal.display.JediTerminal;
import com.jediterm.terminal.display.StyleState;
import com.jediterm.util.BackBufferDisplay;
import junit.framework.TestCase;

import java.awt.*;

/**
 * @author traff
 */
public class StyledTextTest extends TestCase {
  private static final TextStyle GREEN = new TextStyle(TerminalColor.index(2), null);
  private static final TextStyle BLACK = new TextStyle(TerminalColor.BLACK, null);


  public void testStyledTest1() {
    final int width = 12;
    final int height = 1;
    final String colors = "00 00 00 00 01 01 01 00 00 00 00 02 \n";

    StyleState state = new StyleState();

    BackBuffer backBuffer = new BackBuffer(width, height, state);

    JediTerminal terminal = new JediTerminal(new BackBufferDisplay(backBuffer), backBuffer, state);

    BackBuffer backBuffer2 = new BackBuffer(width, height, state);

    terminal.characterAttributes(BLACK);
    terminal.writeString("def ");
    terminal.characterAttributes(GREEN);
    terminal.writeString("foo");

    backBuffer.processDamagedCells(backBuffer2);
    backBuffer.resetDamage();

    terminal.characterAttributes(BLACK);
    terminal.writeString("(x):");


    assertEquals(colors, backBuffer.getStyleLines());

    backBuffer.processDamagedCells(backBuffer2);

    assertEquals(colors, backBuffer2.getStyleLines());
  }
}
