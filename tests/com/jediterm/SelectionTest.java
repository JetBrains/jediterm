package com.jediterm;

import com.jediterm.emulator.RequestOrigin;
import com.jediterm.emulator.display.*;
import com.jediterm.util.BackBufferDisplay;
import junit.framework.TestCase;

import java.awt.*;

/**
 * @author traff
 */
public class SelectionTest extends TestCase {
  public void testMultilineSelection() {
    StyleState state = new StyleState();

    LinesBuffer scrollBuffer = new LinesBuffer();

    BackBuffer backBuffer = new BackBuffer(15, 5, state, scrollBuffer);

    BufferedDisplayTerminal terminal = new BufferedDisplayTerminal(new BackBufferDisplay(backBuffer), backBuffer, state);

    terminal.writeString("  1. line ");
    terminal.newLine();
    terminal.carriageReturn();
    terminal.writeString("  2. line2");
    terminal.newLine();
    terminal.carriageReturn();

    assertEquals("line \n" +
                 "  2. line", SelectionUtil.getSelectionText(new Point(5, 0), new Point(9, 1), scrollBuffer, backBuffer));

  }

  public void testSingleLineSelection() {
    StyleState state = new StyleState();

    LinesBuffer scrollBuffer = new LinesBuffer();

    BackBuffer backBuffer = new BackBuffer(15, 5, state, scrollBuffer);

    BufferedDisplayTerminal writer = new BufferedDisplayTerminal(new BackBufferDisplay(backBuffer), backBuffer, state);

    writer.writeString("   line   ");
    writer.newLine();
    writer.carriageReturn();

    assertEquals(" line  ", SelectionUtil.getSelectionText(new Point(2, 0), new Point(9, 0), scrollBuffer, backBuffer));
  }

  public void testSelectionOutOfTheScreen() {
    StyleState state = new StyleState();

    LinesBuffer scrollBuffer = new LinesBuffer();

    BackBuffer backBuffer = new BackBuffer(20, 5, state, scrollBuffer);

    BufferedDisplayTerminal writer = new BufferedDisplayTerminal(new BackBufferDisplay(backBuffer), backBuffer, state);

    writer.writeString("text to select ");
    writer.newLine();
    writer.carriageReturn();
    writer.writeString("and copy");
    writer.newLine();
    writer.carriageReturn();

    writer.resize(new Dimension(8, 10), RequestOrigin.User);

    assertEquals("text to select \nand copy", SelectionUtil.getSelectionText(new Point(0, 0), new Point(8, 1), scrollBuffer, backBuffer));
  }

  public void testSelectionTheLastLine() {
    StyleState state = new StyleState();

    LinesBuffer scrollBuffer = new LinesBuffer();

    BackBuffer backBuffer = new BackBuffer(15, 5, state, scrollBuffer);

    BufferedDisplayTerminal writer = new BufferedDisplayTerminal(new BackBufferDisplay(backBuffer), backBuffer, state);

    writer.writeString("first line");
    writer.newLine();
    writer.carriageReturn();
    writer.writeString("last line");


    assertEquals("last line", SelectionUtil.getSelectionText(new Point(0, 1), new Point(9, 1), scrollBuffer, backBuffer));
  }

  public void testMultilineSelectionWithLastLine() {
    StyleState state = new StyleState();

    LinesBuffer scrollBuffer = new LinesBuffer();

    BackBuffer backBuffer = new BackBuffer(15, 5, state, scrollBuffer);

    BufferedDisplayTerminal writer = new BufferedDisplayTerminal(new BackBufferDisplay(backBuffer), backBuffer, state);

    writer.writeString("first line");
    writer.newLine();
    writer.carriageReturn();
    writer.writeString("second line");
    writer.newLine();
    writer.carriageReturn();
    writer.writeString("last line");

    assertEquals("second line\nlast line", SelectionUtil.getSelectionText(new Point(0, 1), new Point(9, 2), scrollBuffer, backBuffer));
  }

}
