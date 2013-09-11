package com.jediterm;

import com.jediterm.terminal.TerminalMode;
import com.jediterm.terminal.display.BackBuffer;
import com.jediterm.terminal.display.JediTerminal;
import com.jediterm.terminal.display.StyleState;
import com.jediterm.util.BackBufferDisplay;
import junit.framework.TestCase;

public class ModesTest extends TestCase {

  public void testAutoWrap() {
    StyleState state = new StyleState();

    BackBuffer backBuffer = new BackBuffer(10, 3, state);

    JediTerminal terminal = new JediTerminal(new BackBufferDisplay(backBuffer), backBuffer, state);

    terminal.setModeEnabled(TerminalMode.AutoWrap, false);
    //                             1234567890123456789
    terminal.writeUnwrappedString("this is a long line");

    assertEquals(1, backBuffer.getTextBufferLinesCount());
    assertEquals("long line ", backBuffer.getTextBufferLines());
    assertEquals("long line \n" +
                 "          \n" +
                 "          \n", backBuffer.getLines());
    assertEquals(10, terminal.getCursorX());
    assertEquals(1, terminal.getCursorY());

    terminal.cursorPosition(1, 1);
    terminal.setModeEnabled(TerminalMode.AutoWrap, true);
    //                             1234567890123456789
    terminal.writeUnwrappedString("this is a long line");

    assertEquals(2, backBuffer.getTextBufferLinesCount());
    assertEquals("this is a \nlong line", backBuffer.getTextBufferLines());
    assertEquals("this is a \n" +
                 "long line \n" +
                 "          \n", backBuffer.getLines());
    assertEquals(10, terminal.getCursorX());
    assertEquals(2, terminal.getCursorY());
  }

}
