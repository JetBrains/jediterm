package com.jediterm;

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

    BufferedTerminalWriter writer = new BufferedTerminalWriter(new BackBufferDisplay(backBuffer), backBuffer, state);

    writer.writeString("  1. line");
    writer.newLine();
    writer.carriageReturn();
    writer.writeString("  2. line2");
    writer.newLine();
    writer.carriageReturn();

    assertEquals("line\n" +
                 "  2. line", SelectionUtil.getSelectionText(new Point(5, 0), new Point(9, 1), scrollBuffer, backBuffer));

  }

  public void testSingleLineSelection() {
    StyleState state = new StyleState();

    LinesBuffer scrollBuffer = new LinesBuffer();

    BackBuffer backBuffer = new BackBuffer(15, 5, state, scrollBuffer);

    BufferedTerminalWriter writer = new BufferedTerminalWriter(new BackBufferDisplay(backBuffer), backBuffer, state);

    writer.writeString("   line   ");
    writer.newLine();
    writer.carriageReturn();

    assertEquals(" line  ", SelectionUtil.getSelectionText(new Point(2, 0), new Point(9, 0), scrollBuffer, backBuffer));

  }
}
