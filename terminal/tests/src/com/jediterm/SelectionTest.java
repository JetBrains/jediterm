package com.jediterm;

import com.jediterm.terminal.RequestOrigin;
import com.jediterm.terminal.model.TerminalTextBuffer;
import com.jediterm.terminal.model.JediTerminal;
import com.jediterm.terminal.model.SelectionUtil;
import com.jediterm.terminal.model.StyleState;
import com.jediterm.util.BackBufferDisplay;
import junit.framework.TestCase;

import java.awt.*;

/**
 * @author traff
 */
public class SelectionTest extends TestCase {
  public void testMultilineSelection() {
    StyleState state = new StyleState();

    TerminalTextBuffer terminalTextBuffer = new TerminalTextBuffer(15, 5, state);

    JediTerminal terminal = new JediTerminal(new BackBufferDisplay(terminalTextBuffer), terminalTextBuffer, state);

    terminal.writeString("  1. line ");
    terminal.newLine();
    terminal.carriageReturn();
    terminal.writeString("  2. line2");
    terminal.newLine();
    terminal.carriageReturn();

    assertEquals("line \n" +
                 "  2. line", SelectionUtil.getSelectionText(new Point(5, 0), new Point(9, 1), terminalTextBuffer));
  }

  public void testSingleLineSelection() {
    StyleState state = new StyleState();

    TerminalTextBuffer terminalTextBuffer = new TerminalTextBuffer(15, 5, state);

    JediTerminal writer = new JediTerminal(new BackBufferDisplay(terminalTextBuffer), terminalTextBuffer, state);

    writer.writeString("   line   ");
    writer.newLine();
    writer.carriageReturn();

    assertEquals(" line  ", SelectionUtil.getSelectionText(new Point(2, 0), new Point(9, 0), terminalTextBuffer));
  }

  public void testSelectionOutOfTheScreen() {
    StyleState state = new StyleState();

    TerminalTextBuffer terminalTextBuffer = new TerminalTextBuffer(20, 5, state);

    JediTerminal writer = new JediTerminal(new BackBufferDisplay(terminalTextBuffer), terminalTextBuffer, state);

    writer.writeString("text to select ");
    writer.newLine();
    writer.carriageReturn();
    writer.writeString("and copy");
    writer.newLine();
    writer.carriageReturn();

    writer.resize(new Dimension(8, 10), RequestOrigin.User);

    //text to 
    //select 
    //and copy

    assertEquals("text to select \nand copy", SelectionUtil.getSelectionText(new Point(0, 0), new Point(8, 2), terminalTextBuffer));
  }

  public void testSelectionTheLastLine() {
    StyleState state = new StyleState();

    TerminalTextBuffer terminalTextBuffer = new TerminalTextBuffer(15, 5, state);

    JediTerminal writer = new JediTerminal(new BackBufferDisplay(terminalTextBuffer), terminalTextBuffer, state);

    writer.writeString("first line");
    writer.newLine();
    writer.carriageReturn();
    writer.writeString("last line");


    assertEquals("last line", SelectionUtil.getSelectionText(new Point(0, 1), new Point(9, 1), terminalTextBuffer));
  }

  public void testMultilineSelectionWithLastLine() {
    StyleState state = new StyleState();

    TerminalTextBuffer terminalTextBuffer = new TerminalTextBuffer(15, 5, state);

    JediTerminal writer = new JediTerminal(new BackBufferDisplay(terminalTextBuffer), terminalTextBuffer, state);

    writer.writeString("first line");
    writer.newLine();
    writer.carriageReturn();
    writer.writeString("second line");
    writer.newLine();
    writer.carriageReturn();
    writer.writeString("last line");

    assertEquals("second line\nlast line", SelectionUtil.getSelectionText(new Point(0, 1), new Point(9, 2), terminalTextBuffer));
  }

  public void testSelectionFromScrollBuffer() {
    StyleState state = new StyleState();

    TerminalTextBuffer terminalTextBuffer = new TerminalTextBuffer(5, 3, state);

    JediTerminal writer = new JediTerminal(new BackBufferDisplay(terminalTextBuffer), terminalTextBuffer, state);

    writer.writeString("12");
    writer.newLine();
    writer.carriageReturn();
    writer.writeString("34");
    writer.newLine();
    writer.carriageReturn();
    writer.writeString("56");
    writer.newLine();
    writer.carriageReturn();
    writer.writeString("78");
    writer.newLine();
    writer.carriageReturn();
    writer.writeString("90");


    assertEquals("12\n" +
                 "34\n" +
                 "56\n" +
                 "78", SelectionUtil.getSelectionText(new Point(0, -2), new Point(2, 1), terminalTextBuffer));
  }

  public void testDoubleWidth() {
    StyleState state = new StyleState();

    TerminalTextBuffer terminalTextBuffer = new TerminalTextBuffer(10, 2, state);

    JediTerminal terminal = new JediTerminal(new BackBufferDisplay(terminalTextBuffer), terminalTextBuffer, state);

    terminal.writeString("生活習慣病");

    assertEquals("生活習慣病", SelectionUtil.getSelectionText(new Point(0, 0), new Point(10, 0), terminalTextBuffer));
  }
}
