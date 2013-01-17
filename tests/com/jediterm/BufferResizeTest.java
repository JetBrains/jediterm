package com.jediterm;

import com.jediterm.util.BackBufferDisplay;
import com.jediterm.util.CharBufferUtil;
import junit.framework.TestCase;

import java.awt.*;

/**
 * @author traff
 */
public class BufferResizeTest extends TestCase {
  public void testResizeToBiggerHeight() {
    StyleState state = new StyleState();

    LinesBuffer scrollBuffer = new LinesBuffer();

    BackBuffer backBuffer = new BackBuffer(5, 5, state, scrollBuffer);

    BufferedTerminalWriter writer = new BufferedTerminalWriter(new BackBufferDisplay(backBuffer), backBuffer, state);


    writer.writeString("line");
    writer.newLine();
    writer.carriageReturn();
    writer.writeString("line2");
    writer.newLine();
    writer.carriageReturn();
    writer.writeString("line3");
    writer.newLine();
    writer.carriageReturn();
    writer.writeString("li");

    writer.resize(new Dimension(10, 10), RequestOrigin.User);



    assertEquals(0, scrollBuffer.getLineCount());

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

    assertEquals(4, writer.getCursorY());
  }


  public void testResizeToSmallerHeight() {
    StyleState state = new StyleState();

    LinesBuffer scrollBuffer = new LinesBuffer();

    BackBuffer backBuffer = new BackBuffer(5, 5, state, scrollBuffer);

    BufferedTerminalWriter writer = new BufferedTerminalWriter(new BackBufferDisplay(backBuffer), backBuffer, state);


    writer.writeString("line");
    writer.newLine();
    writer.carriageReturn();
    writer.writeString("line2");
    writer.newLine();
    writer.carriageReturn();
    writer.writeString("line3");
    writer.newLine();
    writer.carriageReturn();
    writer.writeString("li");

    assertEquals(4, writer.getCursorY());

    writer.resize(new Dimension(10, 2), RequestOrigin.User);


    assertEquals("\n" +
                 "line \n" +
                 "line2", scrollBuffer.getLines());

    assertEquals("line3     \n" +
                 "li        \n", backBuffer.getLines());

    assertEquals(2, writer.getCursorY());
  }


  public void testResizeToSmallerHeightAndBack() {
    StyleState state = new StyleState();

    LinesBuffer scrollBuffer = new LinesBuffer();

    BackBuffer backBuffer = new BackBuffer(5, 5, state, scrollBuffer);

    BufferedTerminalWriter writer = new BufferedTerminalWriter(new BackBufferDisplay(backBuffer), backBuffer, state);


    writer.writeString("line ");
    writer.writeString("line2");
    writer.newLine();
    writer.carriageReturn();
    writer.writeString("line3");
    writer.newLine();
    writer.carriageReturn();
    writer.writeString("line4");
    writer.newLine();
    writer.carriageReturn();
    writer.writeString("li");

    assertEquals(5, writer.getCursorY());

    writer.resize(new Dimension(10, 2), RequestOrigin.User);


    assertEquals("\n" +
                 "line \n" +
                 "line2\n" +
                 "line3", scrollBuffer.getLines());

    assertEquals("line4     \n" +
                 "li        \n", backBuffer.getLines());

    assertEquals(2, writer.getCursorY());

    writer.resize(new Dimension(5, 5), RequestOrigin.User);

    assertEquals(0, scrollBuffer.getLineCount());

    assertEquals("line \n" +
                 "line2\n" +
                 "line3\n" +
                 "line4\n" +
                 "li   \n", backBuffer.getLines());
  }


  public void testResizeInHeightWithScrolling() {
    StyleState state = new StyleState();

    LinesBuffer scrollBuffer = new LinesBuffer();

    BackBuffer backBuffer = new BackBuffer(5, 2, state, scrollBuffer);

    BufferedTerminalWriter writer = new BufferedTerminalWriter(new BackBufferDisplay(backBuffer), backBuffer, state);

    scrollBuffer.addToBuffer(state.getCurrent(), CharBufferUtil.create("line"), true);
    scrollBuffer.addToBuffer(state.getCurrent(), CharBufferUtil.create("line2"), true);

    writer.writeString("line3");
    writer.newLine();
    writer.carriageReturn();
    writer.writeString("li");

    writer.resize(new Dimension(10, 5), RequestOrigin.User);

    assertEquals(0, scrollBuffer.getLineCount());

    assertEquals("line      \n" +
                 "line2     \n" +
                 "line3     \n" +
                 "li        \n" +
                 "          \n", backBuffer.getLines());

    assertEquals(4, writer.getCursorY());
  }


  public void testTypeOnLastLineAndResizeWidth() {
    StyleState state = new StyleState();

    LinesBuffer scrollBuffer = new LinesBuffer();

    BackBuffer backBuffer = new BackBuffer(6, 5, state, scrollBuffer);

    BufferedTerminalWriter writer = new BufferedTerminalWriter(new BackBufferDisplay(backBuffer), backBuffer, state);

    writer.writeString(">line1");
    writer.newLine();
    writer.carriageReturn();
    writer.writeString(">line2");
    writer.newLine();
    writer.carriageReturn();
    writer.writeString(">line3");
    writer.newLine();
    writer.carriageReturn();
    writer.writeString(">line4");
    writer.newLine();
    writer.carriageReturn();
    writer.writeString(">line5");
    writer.newLine();
    writer.carriageReturn();
    writer.writeString(">");

    assertEquals(">line2\n" +
                 ">line3\n" +
                 ">line4\n" +
                 ">line5\n" +
                 ">     \n", backBuffer.getLines());

    writer.resize(new Dimension(3, 5), RequestOrigin.User);

    assertEquals(">line\n" +         //minimum width is 5
                 ">line\n" +
                 ">line\n" +
                 ">line\n" +
                 ">    \n", backBuffer.getLines());

    writer.resize(new Dimension(6, 5), RequestOrigin.User);

    assertEquals(">line2\n" +
                 ">line3\n" +
                 ">line4\n" +
                 ">line5\n" +
                 ">     \n", backBuffer.getLines());


  }
}
