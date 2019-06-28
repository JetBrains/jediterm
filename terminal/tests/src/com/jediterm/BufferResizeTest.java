package com.jediterm;

import com.jediterm.terminal.RequestOrigin;
import com.jediterm.terminal.model.*;
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


    TerminalTextBuffer textBuffer = new TerminalTextBuffer(5, 5, state);

    JediTerminal terminal = new BackBufferTerminal(textBuffer, state);


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


    assertEquals(0, textBuffer.getHistoryBuffer().getLineCount());

    assertEquals("line      \n" +
        "line2     \n" +
        "line3     \n" +
        "li        \n" +
        "          \n" +
        "          \n" +
        "          \n" +
        "          \n" +
        "          \n" +
        "          \n", textBuffer.getScreenLines());

    assertEquals(4, terminal.getCursorY());
  }


  public void testResizeToSmallerHeight() {
    StyleState state = new StyleState();

    TerminalTextBuffer textBuffer = new TerminalTextBuffer(5, 5, state);

    JediTerminal terminal = new BackBufferTerminal(textBuffer, state);


    terminal.writeString("line");
    terminal.crnl();
    terminal.writeString("line2");
    terminal.crnl();
    terminal.writeString("line3");
    terminal.crnl();
    terminal.writeString("li");

    assertEquals(4, terminal.getCursorY());

    terminal.resize(new Dimension(10, 2), RequestOrigin.User);


    assertEquals("line\n" +
        "line2", textBuffer.getHistoryBuffer().getLines());

    assertEquals("line3     \n" +
        "li        \n", textBuffer.getScreenLines());

    assertEquals(2, terminal.getCursorY());
  }


  public void testResizeToSmallerHeightAndBack() {
    StyleState state = new StyleState();

    TerminalTextBuffer textBuffer = new TerminalTextBuffer(5, 5, state);

    JediTerminal terminal = new BackBufferTerminal(textBuffer, state);


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
        "line3", textBuffer.getHistoryBuffer().getLines());

    assertEquals("line4     \n" +
        "li        \n", textBuffer.getScreenLines());

    assertEquals(2, terminal.getCursorY());

    terminal.resize(new Dimension(5, 5), RequestOrigin.User);

    assertEquals(0, textBuffer.getHistoryBuffer().getLineCount());

    assertEquals("line \n" +
        "line2\n" +
        "line3\n" +
        "line4\n" +
        "li   \n", textBuffer.getScreenLines());
  }


  public void testResizeInHeightWithScrolling() {
    StyleState state = new StyleState();

    TerminalTextBuffer textBuffer = new TerminalTextBuffer(5, 2, state);

    JediTerminal terminal = new BackBufferTerminal(textBuffer, state);

    LinesBuffer scrollBuffer = textBuffer.getHistoryBuffer();

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
        "          \n", textBuffer.getScreenLines());

    assertEquals(4, terminal.getCursorY());
  }


  public void testTypeOnLastLineAndResizeWidth() {
    StyleState state = new StyleState();

    TerminalTextBuffer textBuffer = new TerminalTextBuffer(6, 5, state);

    JediTerminal terminal = new BackBufferTerminal(textBuffer, state);

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

    assertEquals(">line1", textBuffer.getHistoryBuffer().getLines());
    assertEquals(
        ">line2\n" +
        ">line3\n" +
        ">line4\n" +
        ">line5\n" +
        ">     \n", textBuffer.getScreenLines());

    terminal.resize(new Dimension(3, 5), RequestOrigin.User); // JediTerminal.MIN_WIDTH = 5

    assertEquals(
        ">line\n" +
        "1\n" +
        ">line\n" +
        "2\n" +
        ">line\n" +
        "3", textBuffer.getHistoryBuffer().getLines());
    assertEquals(
        ">line\n" +
        "4    \n" +
        ">line\n" +
        "5    \n" + 
        ">    \n", textBuffer.getScreenLines());

    terminal.resize(new Dimension(6, 5), RequestOrigin.User);

    assertEquals(">line1", textBuffer.getHistoryBuffer().getLines());
    assertEquals(
        ">line2\n" +
            ">line3\n" +
            ">line4\n" +
            ">line5\n" +
            ">     \n", textBuffer.getScreenLines());
  }

  public void testSelectionAfterResize() {
    StyleState state = new StyleState();

    TerminalTextBuffer textBuffer = new TerminalTextBuffer(6, 3, state);

    BackBufferDisplay display = new BackBufferDisplay(textBuffer);
    JediTerminal terminal = new JediTerminal(display, textBuffer, state);

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

    String selectionText = SelectionUtil.getSelectionText(display.getSelection(), textBuffer);

    assertEquals("line4\n" +
        ">line", selectionText);


    terminal.resize(new Dimension(6, 5), RequestOrigin.User);

    assertEquals(selectionText, SelectionUtil.getSelectionText(display.getSelection(), textBuffer));


    display.setSelection(new TerminalSelection(new Point(1, 0), new Point(5, 1)));

    selectionText = SelectionUtil.getSelectionText(display.getSelection(), textBuffer);

    assertEquals("line2\n" +
        ">line", selectionText);


    terminal.resize(new Dimension(6, 2), RequestOrigin.User);

    assertEquals(selectionText, SelectionUtil.getSelectionText(display.getSelection(), textBuffer));
  }

  public void testClearAndResizeVertically() {
    StyleState state = new StyleState();

    TerminalTextBuffer terminalTextBuffer = new TerminalTextBuffer(10, 4, state);

    JediTerminal terminal = new JediTerminal(new BackBufferDisplay(terminalTextBuffer), terminalTextBuffer, state);

    terminal.writeString("hi>");
    terminal.crnl();
    terminal.writeString("hi2>");

    terminal.clearScreen();

    terminal.cursorPosition(0, 0);
    terminal.writeString("hi3>");


    // smaller height
    terminal.resize(new Dimension(10, 3), RequestOrigin.User);


    assertEquals("", terminalTextBuffer.getHistoryBuffer().getLines());
    assertEquals("hi3>      \n" +
            "          \n" +
            "          \n", terminalTextBuffer.getScreenLines());
  }


  public void testInitialResize() {
    StyleState state = new StyleState();

    TerminalTextBuffer terminalTextBuffer = new TerminalTextBuffer(10, 24, state);

    JediTerminal terminal = new JediTerminal(new BackBufferDisplay(terminalTextBuffer), terminalTextBuffer, state);

    terminal.writeString("hi>");

    // initial resize
    terminal.resize(new Dimension(10, 3), RequestOrigin.User);


    assertEquals("", terminalTextBuffer.getHistoryBuffer().getLines());
    assertEquals("hi>       \n" +
            "          \n" +
            "          \n", terminalTextBuffer.getScreenLines());
  }

  public void testResizeWidth1() {
    StyleState state = new StyleState();
    TerminalTextBuffer terminalTextBuffer = new TerminalTextBuffer(15, 24, state);
    JediTerminal terminal = new JediTerminal(new BackBufferDisplay(terminalTextBuffer), terminalTextBuffer, state);
    terminal.writeString("$ cat long.txt");
    terminal.crnl();
    terminal.writeString("1_2_3_4_5_6_7_8");
    terminal.writeString("_9_10_11_12_13_");
    terminal.writeString("14_15_16_17_18_");
    terminal.writeString("19_20_21_22_23_");
    terminal.writeString("24_25_26");
    terminal.crnl();
    terminal.writeString("$ ");
    assertEquals(3, terminal.getCursorX());
    assertEquals(7, terminal.getCursorY());
    assertEquals("", terminalTextBuffer.getHistoryBuffer().getLines());
    terminal.resize(new Dimension(20, 7), RequestOrigin.User);

    assertEquals("", terminalTextBuffer.getHistoryBuffer().getLines());
    assertEquals("$ cat long.txt      \n" +
                 "1_2_3_4_5_6_7_8_9_10\n" +
                 "_11_12_13_14_15_16_1\n" +
                 "7_18_19_20_21_22_23_\n" +
                 "24_25_26            \n" +
                 "$                   \n" +
                 "                    \n", terminalTextBuffer.getScreenLines());
  }

  public void testResizeWidth2() {
    StyleState state = new StyleState();
    TerminalTextBuffer terminalTextBuffer = new TerminalTextBuffer(100, 5, state);
    JediTerminal terminal = new JediTerminal(new BackBufferDisplay(terminalTextBuffer), terminalTextBuffer, state);
    terminal.writeString("$ cat long.txt");
    terminal.crnl();
    terminal.writeString("1_2_3_4_5_6_7_8_9_10_11_12_13_14_15_16_17_18_19_20_21_22_23_24_25_26_27_28_30");
    terminal.crnl();
    terminal.crnl();
    terminal.writeString("$ ");
    assertEquals(3, terminal.getCursorX());
    assertEquals(4, terminal.getCursorY());
    assertEquals("", terminalTextBuffer.getHistoryBuffer().getLines());
    terminal.resize(new Dimension(6, 4), RequestOrigin.User);

    assertEquals(
        "$ cat \n" +
        "long.t\n" +
        "xt\n" +
        "1_2_3_\n" +
        "4_5_6_\n" +
        "7_8_9_\n" +
        "10_11_\n" +
        "12_13_\n" +
        "14_15_\n" +
        "16_17_\n" +
        "18_19_\n" +
        "20_21_\n" +
        "22_23_\n" +
        "24_25_", terminalTextBuffer.getHistoryBuffer().getLines());
    assertEquals(
        "26_27_\n" +
        "28_30 \n" +
        "      \n" +
        "$     \n", terminalTextBuffer.getScreenLines());
  }
}
