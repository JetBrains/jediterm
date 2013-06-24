package com.jediterm;

import com.jediterm.terminal.StyledTextConsumer;
import com.jediterm.terminal.TextStyle;
import com.jediterm.terminal.display.BackBuffer;
import com.jediterm.terminal.display.BufferedDisplayTerminal;
import com.jediterm.terminal.display.CharBuffer;
import com.jediterm.terminal.display.StyleState;
import com.jediterm.util.BackBufferDisplay;
import junit.framework.TestCase;

/**
 * @author traff
 */
public class BackBufferTest extends TestCase {
  public void testEmptyLineTextStyle() {
    StyleState state = new StyleState();

    BackBuffer backBuffer = new BackBuffer(15, 10, state);
    
    BufferedDisplayTerminal terminal = new BufferedDisplayTerminal(new BackBufferDisplay(backBuffer), backBuffer, state);

    terminal.writeString("  1. line1");
    terminal.newLine();
    terminal.carriageReturn();
    terminal.writeString("  2. line2");
    terminal.newLine();
    terminal.carriageReturn();
    terminal.newLine();
    terminal.carriageReturn();
    terminal.newLine();
    terminal.carriageReturn();
    terminal.writeString("  3. line3");
    terminal.newLine();
    terminal.carriageReturn();
    terminal.newLine();
    terminal.carriageReturn();

    final int[] lines = {0};
    backBuffer.processTextBuffer(0, 10, new StyledTextConsumer() {
      @Override
      public void consume(int x, int y, TextStyle style, CharBuffer characters, int startRow) {
        assertNotNull(style);
        lines[0]++;
      }
    });

    assertEquals(lines[0], 6);
  }
}
