package com.jediterm;

import com.jediterm.emulator.display.BackBuffer;
import com.jediterm.emulator.display.BufferedTerminalWriter;
import com.jediterm.emulator.display.LinesBuffer;
import com.jediterm.emulator.display.StyleState;
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

    BufferedTerminalWriter writer = new BufferedTerminalWriter(new BackBufferDisplay(backBuffer), backBuffer, state);

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

    assertEquals(1, scrollBuffer.getLineCount());

    assertEquals("line2\n" +
                 "line3\n" +
                 "line4\n", backBuffer.getLines());

    assertEquals(3, writer.getCursorY());
  }



  public void testScrollOnTyping() {
    StyleState state = new StyleState();

    LinesBuffer scrollBuffer = new LinesBuffer();

    BackBuffer backBuffer = new BackBuffer(5, 3, state, scrollBuffer);

    BufferedTerminalWriter writer = new BufferedTerminalWriter(new BackBufferDisplay(backBuffer), backBuffer, state);

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
