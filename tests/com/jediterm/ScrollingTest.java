package com.jediterm;

import com.jediterm.terminal.RequestOrigin;
import com.jediterm.terminal.display.BackBuffer;
import com.jediterm.terminal.display.JediTerminal;
import com.jediterm.terminal.display.StyleState;
import com.jediterm.util.BackBufferDisplay;
import junit.framework.TestCase;

import java.awt.*;

/**
 * @author traff
 */
public class ScrollingTest extends TestCase {

  public void testScrollOnNewLine() {
    StyleState state = new StyleState();

    BackBuffer backBuffer = new BackBuffer(5, 3, state);

    JediTerminal terminal = new JediTerminal(new BackBufferDisplay(backBuffer), backBuffer, state);

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

    assertEquals(1, backBuffer.getScrollBuffer().getLineCount());

    assertEquals("line2\n" +
        "line3\n" +
        "line4\n", backBuffer.getLines());

    assertEquals(3, terminal.getCursorY());
  }


  public void testScrollOnTyping() {
    StyleState state = new StyleState();

    BackBuffer backBuffer = new BackBuffer(5, 3, state);

    JediTerminal terminal = new JediTerminal(new BackBufferDisplay(backBuffer), backBuffer, state);

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
    terminal.writeString("4");
    terminal.writeString("4");

    assertEquals(2, backBuffer.getScrollBuffer().getLineCount());

    assertEquals("line3\n" +
        "line4\n" +
        "44   \n", backBuffer.getLines());

    assertEquals(3, terminal.getCursorY());
  }

  public void testScrollAndResize() {
    StyleState state = new StyleState();

    BackBuffer backBuffer = new BackBuffer(10, 3, state);

    JediTerminal terminal = new JediTerminal(new BackBufferDisplay(backBuffer), backBuffer, state);

    terminal.writeString("1234567890");
    terminal.newLine();
    terminal.carriageReturn();
    terminal.writeString("2345678901");
    terminal.newLine();
    terminal.carriageReturn();

    terminal.resize(new Dimension(7, 3), RequestOrigin.User);

    terminal.writeString("3456789");
    terminal.newLine();
    terminal.carriageReturn();

    assertEquals("2345678\n" +
        "3456789\n" +
        "       \n", backBuffer.getLines());

    assertEquals("2345678901\n" +
        "3456789", backBuffer.getTextBufferLines());

    assertEquals("1234567890", backBuffer.getScrollBuffer().getLines());
  }
}
