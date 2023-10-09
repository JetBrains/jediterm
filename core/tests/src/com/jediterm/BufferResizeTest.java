package com.jediterm;

import com.jediterm.core.compatibility.Point;
import com.jediterm.core.util.TermSize;
import com.jediterm.terminal.RequestOrigin;
import com.jediterm.terminal.model.*;
import com.jediterm.util.BackBufferDisplay;
import com.jediterm.util.CharBufferUtil;
import com.jediterm.util.TestSession;
import junit.framework.TestCase;

import java.util.List;

/**
 * @author traff
 */
public class BufferResizeTest extends TestCase {
  public void testResizeToBiggerHeight() {
    TestSession session = new TestSession(5, 5);
    TerminalTextBuffer textBuffer = session.getTerminalTextBuffer();
    JediTerminal terminal = session.getTerminal();

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

    session.assertCursorPosition(3, 4);
    terminal.resize(new TermSize(10, 10), RequestOrigin.User);

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

    session.assertCursorPosition(3, 4);
  }


  public void testResizeToSmallerHeight() {
    TestSession session = new TestSession(5, 5);
    TerminalTextBuffer textBuffer = session.getTerminalTextBuffer();
    JediTerminal terminal = session.getTerminal();

    terminal.writeString("line");
    terminal.crnl();
    terminal.writeString("line2");
    terminal.crnl();
    terminal.writeString("line3");
    terminal.crnl();
    terminal.writeString("li");

    session.assertCursorPosition(3, 4);

    terminal.resize(new TermSize(10, 2), RequestOrigin.User);


    assertEquals("line\n" +
        "line2", textBuffer.getHistoryBuffer().getLines());

    assertEquals("line3     \n" +
        "li        \n", textBuffer.getScreenLines());

    session.assertCursorPosition(3, 2);
  }


  public void testResizeToSmallerHeightAndBack() {
    TestSession session = new TestSession(5, 5);
    TerminalTextBuffer textBuffer = session.getTerminalTextBuffer();
    JediTerminal terminal = session.getTerminal();

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

    session.assertCursorPosition(3, 5);

    terminal.resize(new TermSize(10, 2), RequestOrigin.User);


    assertEquals("line\n" +
      "line2\n" +
      "line3", textBuffer.getHistoryBuffer().getLines());

    assertEquals("line4     \n" +
      "li        \n", textBuffer.getScreenLines());

    session.assertCursorPosition(3, 2);

    terminal.resize(new TermSize(5, 5), RequestOrigin.User);

    assertEquals(0, textBuffer.getHistoryBuffer().getLineCount());

    assertEquals("line \n" +
        "line2\n" +
        "line3\n" +
        "line4\n" +
        "li   \n", textBuffer.getScreenLines());

    session.assertCursorPosition(3, 5);
  }

  public void testResizeToSmallerHeightAndKeepCursorVisible() {
    TestSession session = new TestSession(10, 4);
    TerminalTextBuffer textBuffer = session.getTerminalTextBuffer();
    JediTerminal terminal = session.getTerminal();

    terminal.writeString("line1");
    terminal.crnl();
    terminal.writeString("line2");
    terminal.crnl();
    terminal.writeString("line3");
    terminal.crnl();

    session.assertCursorPosition(1, 4);

    terminal.resize(new TermSize(10, 3), RequestOrigin.User);
    assertEquals(List.of("line1"), textBuffer.getHistoryBuffer().getLineTexts());
    assertEquals(List.of("line2", "line3"), textBuffer.getScreenBuffer().getLineTexts());
    session.assertCursorPosition(1, 3);
  }

  public void testResizeInHeightWithScrolling() {
    TestSession session = new TestSession(5, 2);
    JediTerminal terminal = session.getTerminal();
    TerminalTextBuffer textBuffer = session.getTerminalTextBuffer();
    LinesBuffer scrollBuffer = textBuffer.getHistoryBuffer();

    scrollBuffer.addNewLine(session.getCurrentStyle(), CharBufferUtil.create("line"));
    scrollBuffer.addNewLine(session.getCurrentStyle(), CharBufferUtil.create("line2"));

    terminal.writeString("line3");
    terminal.newLine();
    terminal.carriageReturn();
    terminal.writeString("li");

    session.assertCursorPosition(3, 2);
    terminal.resize(new TermSize(10, 5), RequestOrigin.User);

    assertEquals(0, scrollBuffer.getLineCount());

    assertEquals("line      \n" +
                 "line2     \n" +
                 "line3     \n" +
                 "li        \n" +
                 "          \n", textBuffer.getScreenLines());

    session.assertCursorPosition(3, 4);
  }


  public void testTypeOnLastLineAndResizeWidth() {
    TestSession session = new TestSession(6, 5);
    JediTerminal terminal = session.getTerminal();
    TerminalTextBuffer textBuffer = session.getTerminalTextBuffer();

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

    session.assertCursorPosition(2, 5);
    terminal.resize(new TermSize(3, 5), RequestOrigin.User); // JediTerminal.MIN_WIDTH = 5

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

    session.assertCursorPosition(2, 5);

    terminal.resize(new TermSize(6, 5), RequestOrigin.User);

    assertEquals(">line1", textBuffer.getHistoryBuffer().getLines());
    assertEquals(
        ">line2\n" +
        ">line3\n" +
        ">line4\n" +
        ">line5\n" +
        ">     \n", textBuffer.getScreenLines());

    session.assertCursorPosition(2, 5);
  }

  public void testSelectionAfterResize() {
    TestSession session = new TestSession(6, 3);
    JediTerminal terminal = session.getTerminal();
    TerminalTextBuffer textBuffer = session.getTerminalTextBuffer();
    BackBufferDisplay display = session.getDisplay();

    for (int i = 1; i <= 5; i++) {
      terminal.writeString(">line" + i);
      terminal.newLine();
      terminal.carriageReturn();
    }
    terminal.writeString(">");

    display.setSelection(new TerminalSelection(new Point(1, 0), new Point(5, 1)));

    String selectionText = SelectionUtil.getSelectionText(display.getSelection(), textBuffer);

    assertEquals("line4\n" +
        ">line", selectionText);

    session.assertCursorPosition(2, 3);

    terminal.resize(new TermSize(6, 5), RequestOrigin.User);

    assertEquals(selectionText, SelectionUtil.getSelectionText(display.getSelection(), textBuffer));


    display.setSelection(new TerminalSelection(new Point(1, -2), new Point(5, -1)));

    selectionText = SelectionUtil.getSelectionText(display.getSelection(), textBuffer);

    assertEquals("line2\n" +
                 ">line", selectionText);

    session.assertCursorPosition(2, 3);

    terminal.resize(new TermSize(6, 2), RequestOrigin.User);

    assertEquals(selectionText, SelectionUtil.getSelectionText(display.getSelection(), textBuffer));

    session.assertCursorPosition(2, 2);
  }

  public void testClearAndResizeVertically() {
    TestSession session = new TestSession(10, 4);
    TerminalTextBuffer terminalTextBuffer = session.getTerminalTextBuffer();
    JediTerminal terminal = session.getTerminal();

    terminal.writeString("hi>");
    terminal.crnl();
    terminal.writeString("hi2>");

    terminal.clearScreen();

    terminal.cursorPosition(0, 0);
    terminal.writeString("hi3>");

    session.assertCursorPosition(5, 1);

    // smaller height
    terminal.resize(new TermSize(10, 3), RequestOrigin.User);


    assertEquals("", terminalTextBuffer.getHistoryBuffer().getLines());
    assertEquals("hi3>      \n" +
                 "          \n" +
                 "          \n", terminalTextBuffer.getScreenLines());

    session.assertCursorPosition(5, 1);
  }


  public void testInitialResize() {
    TestSession session = new TestSession(10, 24);
    TerminalTextBuffer terminalTextBuffer = session.getTerminalTextBuffer();
    JediTerminal terminal = session.getTerminal();

    terminal.writeString("hi>");

    session.assertCursorPosition(4, 1);
    // initial resize
    terminal.resize(new TermSize(10, 3), RequestOrigin.User);


    assertEquals("", terminalTextBuffer.getHistoryBuffer().getLines());
    assertEquals("hi>       \n" +
                 "          \n" +
                 "          \n", terminalTextBuffer.getScreenLines());

    session.assertCursorPosition(4, 1);
  }

  public void testResizeWidth1() {
    TestSession session = new TestSession(15, 24);
    TerminalTextBuffer textBuffer = session.getTerminalTextBuffer();
    JediTerminal terminal = session.getTerminal();

    terminal.writeString("$ cat long.txt");
    terminal.crnl();
    terminal.writeString("1_2_3_4_5_6_7_8");
    terminal.writeString("_9_10_11_12_13_");
    terminal.writeString("14_15_16_17_18_");
    terminal.writeString("19_20_21_22_23_");
    terminal.writeString("24_25_26");
    terminal.crnl();
    terminal.writeString("$ ");
    session.assertCursorPosition(3, 7);
    assertEquals("", textBuffer.getHistoryBuffer().getLines());
    terminal.resize(new TermSize(20, 7), RequestOrigin.User);

    assertEquals("", textBuffer.getHistoryBuffer().getLines());
    assertEquals("$ cat long.txt      \n" +
                 "1_2_3_4_5_6_7_8_9_10\n" +
                 "_11_12_13_14_15_16_1\n" +
                 "7_18_19_20_21_22_23_\n" +
                 "24_25_26            \n" +
                 "$                   \n" +
                 "                    \n", textBuffer.getScreenLines());
    session.assertCursorPosition(3, 6);
  }

  public void testResizeWidth2() {
    TestSession session = new TestSession(100, 5);
    TerminalTextBuffer textBuffer = session.getTerminalTextBuffer();
    JediTerminal terminal = session.getTerminal();

    terminal.writeString("$ cat long.txt");
    terminal.crnl();
    terminal.writeString("1_2_3_4_5_6_7_8_9_10_11_12_13_14_15_16_17_18_19_20_21_22_23_24_25_26_27_28_30");
    terminal.crnl();
    terminal.crnl();
    terminal.writeString("$ ");
    session.assertCursorPosition(3, 4);
    assertEquals("", textBuffer.getHistoryBuffer().getLines());
    terminal.resize(new TermSize(6, 4), RequestOrigin.User);

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
        "24_25_", textBuffer.getHistoryBuffer().getLines());
    assertEquals(
      "26_27_\n" +
        "28_30 \n" +
        "      \n" +
        "$     \n", textBuffer.getScreenLines());
    session.assertCursorPosition(3, 4);
  }

  public void testPointsTracking() {
    TestSession session = new TestSession(10, 4);
    TerminalTextBuffer textBuffer = session.getTerminalTextBuffer();
    JediTerminal terminal = session.getTerminal();

    terminal.writeString("line1");
    terminal.newLine();
    terminal.carriageReturn();
    terminal.writeString("line2");
    terminal.newLine();
    terminal.carriageReturn();
    terminal.writeString("line3");
    terminal.newLine();
    terminal.carriageReturn();
    terminal.writeString("line4");

    session.assertCursorPosition(6, 4);
    terminal.resize(new TermSize(5, 4), RequestOrigin.User);

    LinesBuffer historyBuffer = textBuffer.getHistoryBuffer();
    assertEquals(1, historyBuffer.getLineCount());
    assertEquals("line1", historyBuffer.getLine(0).getText());

    assertEquals(
      "line2\n" +
      "line3\n" +
      "line4\n" +
      "     \n", textBuffer.getScreenLines());

    session.assertCursorPosition(1, 4);
  }
}
