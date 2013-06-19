package com.jediterm;

import com.jediterm.terminal.display.BackBuffer;
import com.jediterm.terminal.display.BufferedDisplayTerminal;
import com.jediterm.terminal.display.LinesBuffer;
import com.jediterm.terminal.display.StyleState;
import com.jediterm.util.BackBufferDisplay;
import junit.framework.TestCase;

/**
 * @author traff
 */
public class ScrollingTest extends TestCase {

  public void testScrollOnNewLine() {
    StyleState state = new StyleState();

    LinesBuffer scrollBuffer = new LinesBuffer();

    BackBuffer backBuffer = new BackBuffer(5, 3, state, scrollBuffer);

    BufferedDisplayTerminal terminal = new BufferedDisplayTerminal(new BackBufferDisplay(backBuffer), backBuffer, state);

    terminal.writeString("line");
    terminal.newLine();
    terminal.carriageReturn();
    terminal.writeString("line2");
    terminal.newLine();
    terminal.carriageReturn();
    terminal.writeString("line3");
    terminal.newLine();
    terminal.carriageReturn();
    terminal.writeString("line4");

    assertEquals(1, scrollBuffer.getLineCount());

    assertEquals("line2\n" +
                 "line3\n" +
                 "line4\n", backBuffer.getLines());

    assertEquals(3, terminal.getCursorY());
  }



  public void testScrollOnTyping() {
    StyleState state = new StyleState();

    LinesBuffer scrollBuffer = new LinesBuffer();

    BackBuffer backBuffer = new BackBuffer(5, 3, state, scrollBuffer);

    BufferedDisplayTerminal writer = new BufferedDisplayTerminal(new BackBufferDisplay(backBuffer), backBuffer, state);

    writer.writeString("line");
    writer.newLine();
    writer.carriageReturn();
    writer.writeString("line2");
    writer.newLine();
    writer.carriageReturn();
    writer.writeString("line3");
    writer.newLine();
    writer.carriageReturn();
    writer.writeString("line4");
    writer.writeString("4");
    writer.writeString("4");

    assertEquals(2, scrollBuffer.getLineCount());

    assertEquals("line3\n" +
                 "line4\n" +
                 "44   \n", backBuffer.getLines());

    assertEquals(3, writer.getCursorY());
  }
}
