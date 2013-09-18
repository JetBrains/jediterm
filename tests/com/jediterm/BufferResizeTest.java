package com.jediterm;

import com.jediterm.terminal.RequestOrigin;
import com.jediterm.terminal.display.*;
import com.jediterm.util.BackBufferDisplay;
import com.jediterm.util.BackBufferTerminal;
import com.jediterm.util.CharBufferUtil;
import junit.framework.TestCase;

import java.awt.*;

/**
 * @author traff
 */
public class BufferResizeTest extends TestCase {
  public void testResizeToBiggerHeight() {
    StyleState state = new StyleState();


    BackBuffer backBuffer = new BackBuffer(5, 5, state);

    JediTerminal terminal = new BackBufferTerminal(backBuffer, state);


    terminal.writeString("line");
    terminal.newLine();
    terminal.carriageReturn();
    terminal.writeString("line2");
    terminal.newLine();
    terminal.carriageReturn();
    terminal.writeString("line3");
    terminal.newLine();
    terminal.carriageReturn();
    terminal.writeString("li");

    terminal.resize(new Dimension(10, 10), RequestOrigin.User);


    assertEquals(0, backBuffer.getScrollBuffer().getLineCount());

    assertEquals("line      \n" +
        "line2     \n" +
        "line3     \n" +
        "li        \n" +
        "          \n" +
        "          \n" +
        "          \n" +
        "          \n" +
        "          \n" +
        "          \n", backBuffer.getLines());

    assertEquals(4, terminal.getCursorY());
  }


  public void testResizeToSmallerHeight() {
    StyleState state = new StyleState();

    BackBuffer backBuffer = new BackBuffer(5, 5, state);

    JediTerminal terminal = new BackBufferTerminal(backBuffer, state);


    terminal.writeString("line");
    terminal.newLine();
    terminal.carriageReturn();
    terminal.writeString("line2");
    terminal.newLine();
    terminal.carriageReturn();
    terminal.writeString("line3");
    terminal.newLine();
    terminal.carriageReturn();
    terminal.writeString("li");

    assertEquals(4, terminal.getCursorY());

    terminal.resize(new Dimension(10, 2), RequestOrigin.User);


    assertEquals("line\n" +
        "line2", backBuffer.getScrollBuffer().getLines());

    assertEquals("line3     \n" +
        "li        \n", backBuffer.getLines());

    assertEquals(2, terminal.getCursorY());
  }


  public void testResizeToSmallerHeightAndBack() {
    StyleState state = new StyleState();

    BackBuffer backBuffer = new BackBuffer(5, 5, state);

    JediTerminal terminal = new BackBufferTerminal(backBuffer, state);


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
    terminal.newLine();
    terminal.carriageReturn();
    terminal.writeString("li");

    assertEquals(5, terminal.getCursorY());

    terminal.resize(new Dimension(10, 2), RequestOrigin.User);


    assertEquals("line\n" +
        "line2\n" +
        "line3", backBuffer.getScrollBuffer().getLines());

    assertEquals("line4     \n" +
        "li        \n", backBuffer.getLines());

    assertEquals(2, terminal.getCursorY());

    terminal.resize(new Dimension(5, 5), RequestOrigin.User);

    assertEquals(0, backBuffer.getScrollBuffer().getLineCount());

    assertEquals("line \n" +
        "line2\n" +
        "line3\n" +
        "line4\n" +
        "li   \n", backBuffer.getLines());
  }


  public void testResizeInHeightWithScrolling() {
    StyleState state = new StyleState();

    BackBuffer backBuffer = new BackBuffer(5, 2, state);

    JediTerminal terminal = new BackBufferTerminal(backBuffer, state);

    LinesBuffer scrollBuffer = backBuffer.getScrollBuffer();

    scrollBuffer.addNewLine(state.getCurrent(), CharBufferUtil.create("line"));
    scrollBuffer.addNewLine(state.getCurrent(), CharBufferUtil.create("line2"));

    terminal.writeString("line3");
    terminal.newLine();
    terminal.carriageReturn();
    terminal.writeString("li");

    terminal.resize(new Dimension(10, 5), RequestOrigin.User);

    assertEquals(0, scrollBuffer.getLineCount());

    assertEquals("line      \n" +
        "line2     \n" +
        "line3     \n" +
        "li        \n" +
        "          \n", backBuffer.getLines());

    assertEquals(4, terminal.getCursorY());
  }


  public void testTypeOnLastLineAndResizeWidth() {
    StyleState state = new StyleState();

    BackBuffer backBuffer = new BackBuffer(6, 5, state);

    JediTerminal terminal = new BackBufferTerminal(backBuffer, state);

    terminal.writeString(">line1");
    terminal.newLine();
    terminal.carriageReturn();
    terminal.writeString(">line2");
    terminal.newLine();
    terminal.carriageReturn();
    terminal.writeString(">line3");
    terminal.newLine();
    terminal.carriageReturn();
    terminal.writeString(">line4");
    terminal.newLine();
    terminal.carriageReturn();
    terminal.writeString(">line5");
    terminal.newLine();
    terminal.carriageReturn();
    terminal.writeString(">");

    assertEquals(">line2\n" +
        ">line3\n" +
        ">line4\n" +
        ">line5\n" +
        ">     \n", backBuffer.getLines());

    terminal.resize(new Dimension(3, 5), RequestOrigin.User);

    assertEquals(">line\n" +         //minimum width is 5
        ">line\n" +
        ">line\n" +
        ">line\n" +
        ">    \n", backBuffer.getLines());

    terminal.resize(new Dimension(6, 5), RequestOrigin.User);

    assertEquals(">line2\n" +
        ">line3\n" +
        ">line4\n" +
        ">line5\n" +
        ">     \n", backBuffer.getLines());
  }

  public void testSelectionAfterResize() {
    StyleState state = new StyleState();

    BackBuffer backBuffer = new BackBuffer(6, 3, state);

    BackBufferDisplay display = new BackBufferDisplay(backBuffer);
    JediTerminal terminal = new JediTerminal(display, backBuffer, state);

    terminal.writeString(">line1");
    terminal.newLine();
    terminal.carriageReturn();
    terminal.writeString(">line2");
    terminal.newLine();
    terminal.carriageReturn();
    terminal.writeString(">line3");
    terminal.newLine();
    terminal.carriageReturn();
    terminal.writeString(">line4");
    terminal.newLine();
    terminal.carriageReturn();
    terminal.writeString(">line5");
    terminal.newLine();
    terminal.carriageReturn();
    terminal.writeString(">");

    display.setSelection(new TerminalSelection(new Point(1, 0), new Point(5, 1)));

    String selectionText = SelectionUtil.getSelectionText(display.getSelection(), backBuffer);

    assertEquals("line4\n" +
        ">line", selectionText);


    terminal.resize(new Dimension(6, 5), RequestOrigin.User);

    assertEquals(selectionText, SelectionUtil.getSelectionText(display.getSelection(), backBuffer));


    display.setSelection(new TerminalSelection(new Point(1, 0), new Point(5, 1)));

    selectionText = SelectionUtil.getSelectionText(display.getSelection(), backBuffer);

    assertEquals("line2\n" +
        ">line", selectionText);


    terminal.resize(new Dimension(6, 2), RequestOrigin.User);

    assertEquals(selectionText, SelectionUtil.getSelectionText(display.getSelection(), backBuffer));
  }
}
