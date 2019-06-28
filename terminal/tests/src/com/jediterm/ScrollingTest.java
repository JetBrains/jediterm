package com.jediterm;

import com.jediterm.terminal.RequestOrigin;
import com.jediterm.terminal.StyledTextConsumer;
import com.jediterm.terminal.TextStyle;
import com.jediterm.terminal.model.CharBuffer;
import com.jediterm.terminal.model.TerminalTextBuffer;
import com.jediterm.terminal.model.JediTerminal;
import com.jediterm.terminal.model.StyleState;
import com.jediterm.util.ArrayBasedTextConsumer;
import com.jediterm.util.BackBufferDisplay;
import junit.framework.TestCase;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

/**
 * @author traff
 */
public class ScrollingTest extends TestCase {

  public void testScrollOnNewLine() {
    StyleState state = new StyleState();

    TerminalTextBuffer terminalTextBuffer = new TerminalTextBuffer(5, 3, state);

    JediTerminal terminal = new JediTerminal(new BackBufferDisplay(terminalTextBuffer), terminalTextBuffer, state);

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

    assertEquals(1, terminalTextBuffer.getHistoryBuffer().getLineCount());

    assertEquals("line2\n" +
            "line3\n" +
            "line4\n", terminalTextBuffer.getScreenLines());

    assertEquals(3, terminal.getCursorY());
  }


  public void testScrollOnTyping() {
    StyleState state = new StyleState();

    TerminalTextBuffer terminalTextBuffer = new TerminalTextBuffer(5, 3, state);

    JediTerminal terminal = new JediTerminal(new BackBufferDisplay(terminalTextBuffer), terminalTextBuffer, state);

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

    assertEquals(2, terminalTextBuffer.getHistoryBuffer().getLineCount());

    assertEquals("line3\n" +
            "line4\n" +
            "44   \n", terminalTextBuffer.getScreenLines());

    assertEquals(3, terminal.getCursorY());
  }

  public void testScrollAndResize() {
    StyleState state = new StyleState();

    TerminalTextBuffer terminalTextBuffer = new TerminalTextBuffer(10, 4, state);

    JediTerminal terminal = new JediTerminal(new BackBufferDisplay(terminalTextBuffer), terminalTextBuffer, state);

    terminal.writeString("1234567890");
    terminal.newLine();
    terminal.carriageReturn();
    terminal.writeString("2345678901");
    terminal.newLine();
    terminal.carriageReturn();

    terminal.resize(new Dimension(7, 4), RequestOrigin.User);

    terminal.writeString("3456789");
    terminal.newLine();
    terminal.carriageReturn();

    assertEquals(
            "2345678\n" +
            "901    \n" +
            "3456789\n" +
            "       \n"
        , terminalTextBuffer.getScreenLines());

    assertEquals(
        "1234567\n" +
        "890", terminalTextBuffer.getHistoryBuffer().getLines());
  }

  public void testScrollingOrigin() {
    StyleState state = new StyleState();

    TerminalTextBuffer terminalTextBuffer = new TerminalTextBuffer(2, 3, state);

    JediTerminal terminal = new JediTerminal(new BackBufferDisplay(terminalTextBuffer), terminalTextBuffer, state);

    terminal.writeString("1");
    terminal.newLine();
    terminal.carriageReturn();

    terminal.writeString("2");
    terminal.newLine();
    terminal.carriageReturn();

    terminal.writeString("3");
    terminal.newLine();
    terminal.carriageReturn();

    terminal.writeString("4");
    terminal.newLine();
    terminal.carriageReturn();

    assertEquals("3 \n" +
            "4 \n" +
            "  \n", terminalTextBuffer.getScreenLines());

    assertEquals("1\n2", terminalTextBuffer.getHistoryBuffer().getLines());

    ArrayBasedTextConsumer textConsumer = new ArrayBasedTextConsumer(terminalTextBuffer.getHeight(), terminalTextBuffer.getWidth());
    terminalTextBuffer.processHistoryAndScreenLines(0, terminalTextBuffer.getHeight(), textConsumer);

    assertEquals("3 \n" +
            "4 \n" +
            "  \n", textConsumer.getLines());


    textConsumer = new ArrayBasedTextConsumer(terminalTextBuffer.getHeight(), terminalTextBuffer.getWidth());
    terminalTextBuffer.processHistoryAndScreenLines(-1, terminalTextBuffer.getHeight(), textConsumer);

    assertEquals("2 \n" +
            "3 \n" +
            "4 \n", textConsumer.getLines());


    textConsumer = new ArrayBasedTextConsumer(terminalTextBuffer.getHeight(), terminalTextBuffer.getWidth());
    terminalTextBuffer.processHistoryAndScreenLines(-2, terminalTextBuffer.getHeight(), textConsumer);

    assertEquals("1 \n" +
            "2 \n" +
            "3 \n", textConsumer.getLines());
  }
}
