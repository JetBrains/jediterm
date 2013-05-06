package com.jediterm;

import com.jediterm.emulator.TextStyle;
import com.jediterm.emulator.display.LinesBuffer;
import com.jediterm.util.CharBufferUtil;
import junit.framework.TestCase;

/**
 * @author traff
 */
public class LinesBufferTest extends TestCase {

  public static final String LINE_1 = "Line 1";
  public static final String LINE_2 = "Line 2";
  public static final String LINE_3 = "Line 3";
  public static final String LINE_4 = "Line 4";

  public void testMoveTopLines() {
    TextStyle style = new TextStyle();
    LinesBuffer scroll = new LinesBuffer();
    scroll.addToBuffer(style, CharBufferUtil.create(LINE_1), true);
    scroll.addToBuffer(style, CharBufferUtil.create(LINE_2), true);
    LinesBuffer text = new LinesBuffer();
    text.addToBuffer(style, CharBufferUtil.create(LINE_3), true);
    text.addToBuffer(style, CharBufferUtil.create(LINE_4), true);

    text.moveTopLinesTo(1, scroll);

    assertEquals(1, text.getLineCount());
    assertEquals(3, scroll.getLineCount());

    assertEquals("\n" +
                 "Line 1\n" +
                 "Line 2\n" +
                 "Line 3", scroll.getLines());

    assertEquals("\n" +
                 "Line 4"
      , text.getLines());
  }

  public void testMoveBottomLines() {
    TextStyle style = new TextStyle();
    LinesBuffer scroll = new LinesBuffer();
    scroll.addToBuffer(style, CharBufferUtil.create(LINE_1), true);
    scroll.addToBuffer(style, CharBufferUtil.create(LINE_2), true);
    LinesBuffer text = new LinesBuffer();
    text.addToBuffer(style, CharBufferUtil.create(LINE_3), true);
    text.addToBuffer(style, CharBufferUtil.create(LINE_4), true);

    scroll.moveBottomLinesTo(1, text);

    assertEquals(3, text.getLineCount());
    assertEquals(1, scroll.getLineCount());


    assertEquals("\n" +
                 "Line 1"
      , scroll.getLines());

    assertEquals("\n" +
                 "Line 2\n" +
                 "Line 3\n" +
                 "Line 4", text.getLines());
  }


  public void testRemoveFirstLines() {
    TextStyle style = new TextStyle();
    LinesBuffer text = new LinesBuffer();
    text.addToBuffer(style, CharBufferUtil.create(LINE_1), true);
    text.addToBuffer(style, CharBufferUtil.create(LINE_2), true);
    text.addToBuffer(style, CharBufferUtil.create(LINE_3), true);
    text.addToBuffer(style, CharBufferUtil.create(LINE_4), true);

    text.removeTopLines(3);

    assertEquals(1, text.getLineCount());

    assertEquals("\n" +
                 "Line 4"
      , text.getLines());
  }

}
