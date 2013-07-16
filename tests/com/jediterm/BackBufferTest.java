package com.jediterm;

import com.jediterm.terminal.StyledTextConsumer;
import com.jediterm.terminal.TextStyle;
import com.jediterm.terminal.display.BackBuffer;
import com.jediterm.terminal.display.CharBuffer;
import com.jediterm.terminal.display.JediTerminal;
import com.jediterm.terminal.display.StyleState;
import com.jediterm.util.BackBufferDisplay;
import junit.framework.TestCase;
import org.jetbrains.annotations.NotNull;

/**
 * @author traff
 */
public class BackBufferTest extends TestCase {
  public void testEmptyLineTextStyle() {
    StyleState state = new StyleState();

    BackBuffer backBuffer = new BackBuffer(15, 10, state);

    JediTerminal terminal = new JediTerminal(new BackBufferDisplay(backBuffer), backBuffer, state);

    terminal.writeString("  1. line1");
    terminal.newLine();
    terminal.carriageReturn();
    terminal.writeString("  2. line2");
    terminal.newLine();
    terminal.carriageReturn();
    terminal.newLine();
    terminal.carriageReturn();
    terminal.newLine();
    terminal.carriageReturn();
    terminal.writeString("  3. line3");
    terminal.newLine();
    terminal.carriageReturn();
    terminal.newLine();
    terminal.carriageReturn();
    terminal.writeString("  4.");

    backBuffer.processTextBufferLines(0, 10, new StyledTextConsumer() {
      @Override
      public void consume(int x, int y, @NotNull TextStyle style, @NotNull CharBuffer characters, int startRow) {
        assertNotNull(style);
      }
    });

    assertEquals("  1. line1\n" +
        "  2. line2\n" +
        "\n" +
        "\n" +
        "  3. line3\n" +
        "\n" +
        "  4.", backBuffer.getTextBufferLines());
  }

  public void testAlternateBuffer() {
    StyleState state = new StyleState();

    BackBuffer backBuffer = new BackBuffer(5, 3, state);

    JediTerminal terminal = new JediTerminal(new BackBufferDisplay(backBuffer), backBuffer, state);

    terminal.writeString("1.");
    terminal.newLine();
    terminal.carriageReturn();
    terminal.writeString("2.");
    terminal.newLine();
    terminal.carriageReturn();

    terminal.useAlternateBuffer(true);

    assertEquals("     \n" +
        "     \n" +
        "     \n", backBuffer.getLines());

    terminal.writeString("xxxxx");
    terminal.newLine();
    terminal.carriageReturn();
    terminal.writeString("yyyyy");
    terminal.newLine();
    terminal.carriageReturn();

    terminal.useAlternateBuffer(false);

    assertEquals("1.   \n" +
        "2.   \n" +
        "     \n", backBuffer.getLines());
  }

  public void testInsertLine() {
    StyleState state = new StyleState();

    BackBuffer backBuffer = new BackBuffer(5, 3, state);

    JediTerminal terminal = new JediTerminal(new BackBufferDisplay(backBuffer), backBuffer, state);

    terminal.writeString("1");
    terminal.newLine();
    terminal.carriageReturn();
    terminal.writeString("2");
    terminal.newLine();
    terminal.carriageReturn();
    terminal.writeString("3");

    terminal.cursorPosition(1, 2);

    terminal.insertLines(1);

    terminal.writeString("3");

    assertEquals("1    \n" +
        "3    \n" +
        "2    \n", backBuffer.getLines());

    assertEquals("1\n" +
        "3\n" +
        "2", backBuffer.getTextBufferLines());

  }

  public void testInsertLine2() {
    StyleState state = new StyleState();

    BackBuffer backBuffer = new BackBuffer(5, 3, state);

    JediTerminal terminal = new JediTerminal(new BackBufferDisplay(backBuffer), backBuffer, state);

    terminal.writeString("1");
    terminal.newLine();
    terminal.carriageReturn();
    terminal.writeString("2");
    terminal.newLine();
    terminal.carriageReturn();
    terminal.writeString("3");

    terminal.cursorPosition(1, 1);

    terminal.insertLines(2);

    terminal.writeString("3");
    terminal.newLine();
    terminal.carriageReturn();

    assertEquals("3    \n" +
        "     \n" +
        "1    \n", backBuffer.getLines());


    assertEquals("3\n" +
        "\n" +
        "1", backBuffer.getTextBufferLines());

    terminal.insertLines(20);


    assertEquals("3    \n" +
        "     \n" +
        "     \n", backBuffer.getLines());


    assertEquals("3\n" +
        "\n" +
        "", backBuffer.getTextBufferLines());
  }

  public void testInsertLineScrollingRegion() {
    StyleState state = new StyleState();

    BackBuffer backBuffer = new BackBuffer(5, 3, state);

    JediTerminal terminal = new JediTerminal(new BackBufferDisplay(backBuffer), backBuffer, state);

    terminal.writeString("1");
    terminal.newLine();
    terminal.carriageReturn();
    terminal.writeString("2");
    terminal.newLine();
    terminal.carriageReturn();
    terminal.writeString("=");

    terminal.setScrollingRegion(1, 2);

    terminal.cursorPosition(1, 1);

    terminal.insertLines(1);

    terminal.writeString("3");
    terminal.newLine();
    terminal.carriageReturn();

    assertEquals("3    \n" +
        "1    \n" +
        "=    \n", backBuffer.getLines());

    assertEquals("3\n" +
        "1\n" +
        "=", backBuffer.getTextBufferLines());
  }

  public void testInsertLineScrollingRegionManyLines() {
    StyleState state = new StyleState();

    BackBuffer backBuffer = new BackBuffer(5, 3, state);

    JediTerminal terminal = new JediTerminal(new BackBufferDisplay(backBuffer), backBuffer, state);

    terminal.writeString("1");
    terminal.newLine();
    terminal.carriageReturn();
    terminal.writeString("2");
    terminal.newLine();
    terminal.carriageReturn();
    terminal.writeString("=");

    terminal.setScrollingRegion(1, 2);

    terminal.cursorPosition(1, 1);

    terminal.insertLines(20);

    terminal.writeString("3");
    terminal.newLine();
    terminal.carriageReturn();

    assertEquals("3    \n" +
        "     \n" +
        "=    \n", backBuffer.getLines());

    assertEquals("3\n" +
        "\n" +
        "=", backBuffer.getTextBufferLines());
  }


  public void testDeleteCharacters() {
    StyleState state = new StyleState();

    BackBuffer backBuffer = new BackBuffer(15, 3, state);

    JediTerminal terminal = new JediTerminal(new BackBufferDisplay(backBuffer), backBuffer, state);

    terminal.writeString("first line");
    terminal.newLine();
    terminal.carriageReturn();
    terminal.writeString("second line");
    terminal.newLine();
    terminal.carriageReturn();
    terminal.writeString("third line");

    assertEquals("first line     \n" +
        "second line    \n" +
        "third line     \n", backBuffer.getLines());

    terminal.cursorPosition(1, 1);
    terminal.deleteCharacters(1);
    assertEquals("irst line      \n" +
        "second line    \n" +
        "third line     \n", backBuffer.getLines());


    assertEquals("irst line\n" +
        "second line\n" +
        "third line", backBuffer.getTextBufferLines());

    terminal.cursorPosition(6, 1);
    terminal.deleteCharacters(2);
    assertEquals("irst ne        \n" +
        "second line    \n" +
        "third line     \n", backBuffer.getLines());

    assertEquals("irst ne\n" +
        "second line\n" +
        "third line", backBuffer.getTextBufferLines());

    terminal.cursorPosition(7, 2);
    terminal.deleteCharacters(42);
    assertEquals("irst ne        \n" +
        "second         \n" +
        "third line     \n", backBuffer.getLines());

    assertEquals("irst ne\n" +
        "second\n" +
        "third line", backBuffer.getTextBufferLines());

    terminal.cursorPosition(1, 3);
    terminal.deleteCharacters(6);
    assertEquals("irst ne        \n" +
        "second         \n" +
        "line           \n", backBuffer.getLines());

    assertEquals("irst ne\n" +
        "second\n" +
        "line", backBuffer.getTextBufferLines());
  }


  public void testDeleteLines() {
    StyleState state = new StyleState();

    BackBuffer backBuffer = new BackBuffer(5, 5, state);

    JediTerminal terminal = new JediTerminal(new BackBufferDisplay(backBuffer), backBuffer, state);

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


    terminal.setScrollingRegion(1, 3);

    terminal.cursorPosition(1, 2);

    terminal.deleteLines(2);

    assertEquals("1    \n" +
        "     \n" +
        "     \n" +
        "4    \n" +
        "     \n", backBuffer.getLines());


    assertEquals("1\n" +
        "\n" +
        "\n" +
        "4", backBuffer.getTextBufferLines());


  }

  public void testDeleteManyLines() {
    StyleState state = new StyleState();

    BackBuffer backBuffer = new BackBuffer(5, 5, state);

    JediTerminal terminal = new JediTerminal(new BackBufferDisplay(backBuffer), backBuffer, state);

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


    terminal.setScrollingRegion(1, 3);

    terminal.cursorPosition(1, 2);

    terminal.deleteLines(20);

    assertEquals("1    \n" +
        "     \n" +
        "     \n" +
        "4    \n" +
        "     \n", backBuffer.getLines());


    assertEquals("1\n" +
        "\n" +
        "\n" +
        "4", backBuffer.getTextBufferLines());


  }

  public void testEraseCharacters() {
    StyleState state = new StyleState();

    BackBuffer backBuffer = new BackBuffer(5, 2, state);

    JediTerminal terminal = new JediTerminal(new BackBufferDisplay(backBuffer), backBuffer, state);

    terminal.writeString("11111");

    terminal.cursorPosition(2, 1);

    terminal.eraseCharacters(2);

    assertEquals("1  11\n" +
        "     \n", backBuffer.getLines());


    assertEquals("1  11", backBuffer.getTextBufferLines());

    terminal.eraseCharacters(10);

    assertEquals("1    \n" +
        "     \n", backBuffer.getLines());

    assertEquals("1    ", backBuffer.getTextBufferLines());

  }

  public void testInsertBlankCharacters() {
    StyleState state = new StyleState();

    BackBuffer backBuffer = new BackBuffer(10, 2, state);

    JediTerminal terminal = new JediTerminal(new BackBufferDisplay(backBuffer), backBuffer, state);

    terminal.writeString("11111");

    terminal.cursorPosition(2, 1);
    terminal.insertBlankCharacters(2);

    assertEquals("1  1111   \n" +
        "          \n", backBuffer.getLines());
    assertEquals("1  1111", backBuffer.getTextBufferLines());

    terminal.cursorPosition(6, 1);
    terminal.insertBlankCharacters(4);

    assertEquals("1  11    1\n" +
        "          \n", backBuffer.getLines());
    assertEquals("1  11    1", backBuffer.getTextBufferLines());
  }
}
