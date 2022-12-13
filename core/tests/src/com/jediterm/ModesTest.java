package com.jediterm;

import com.jediterm.terminal.TerminalMode;
import com.jediterm.terminal.model.JediTerminal;
import com.jediterm.terminal.model.StyleState;
import com.jediterm.terminal.model.TerminalTextBuffer;
import com.jediterm.util.BackBufferDisplay;
import junit.framework.TestCase;

public class ModesTest extends TestCase {

  public void testAutoWrap() {
    StyleState state = new StyleState();

    TerminalTextBuffer terminalTextBuffer = new TerminalTextBuffer(10, 3, state);

    JediTerminal terminal = new JediTerminal(new BackBufferDisplay(terminalTextBuffer), terminalTextBuffer, state);

    terminal.setModeEnabled(TerminalMode.AutoWrap, false);
    //                             1234567890123456789
    terminal.writeUnwrappedString("this is a long line");

    assertEquals("long line \n" +
                 "          \n" +
                 "          \n", terminalTextBuffer.getScreenLines());
    assertEquals(10, terminal.getCursorX());
    assertEquals(1, terminal.getCursorY());

    terminal.cursorPosition(1, 1);
    terminal.setModeEnabled(TerminalMode.AutoWrap, true);
    //                             1234567890123456789
    terminal.writeUnwrappedString("this is a long line");

    assertEquals("this is a \n" +
                 "long line \n" +
                 "          \n", terminalTextBuffer.getScreenLines());
    assertEquals(10, terminal.getCursorX());
    assertEquals(2, terminal.getCursorY());
  }

}
