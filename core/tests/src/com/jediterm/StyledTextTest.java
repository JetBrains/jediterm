package com.jediterm;

import com.jediterm.terminal.ArrayTerminalDataStream;
import com.jediterm.terminal.TerminalColor;
import com.jediterm.terminal.TextStyle;
import com.jediterm.terminal.emulator.Emulator;
import com.jediterm.terminal.emulator.JediEmulator;
import com.jediterm.terminal.model.JediTerminal;
import com.jediterm.terminal.model.StyleState;
import com.jediterm.terminal.model.TerminalTextBuffer;
import com.jediterm.util.BackBufferDisplay;
import com.jediterm.util.TestSession;
import junit.framework.TestCase;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;

import java.io.IOException;
import java.util.EnumSet;

/**
 * @author traff
 */
public class StyledTextTest extends TestCase {
  private static final String CSI = "" + ((char) 27) + "[";

  private static final TextStyle GREEN = new TextStyle(TerminalColor.index(2), null);
  private static final TextStyle BLACK = new TextStyle(TerminalColor.BLACK, null);

  public void testStyledTest1() {
//    final int width = 12;
//    final int height = 1;
//    final String colors = "00 00 00 00 01 01 01 00 00 00 00 02 \n";
//
//    StyleState state = new StyleState();
//
//    BackBuffer backBuffer = new BackBuffer(width, height, state);
//
//    JediTerminal terminal = new JediTerminal(new BackBufferDisplay(backBuffer), backBuffer, state);
//
//    BackBuffer backBuffer2 = new BackBuffer(width, height, state);
//
//    terminal.characterAttributes(BLACK);
//    terminal.writeString("def ");
//    terminal.characterAttributes(GREEN);
//    terminal.writeString("foo");
//
//    backBuffer.processDamagedCells(backBuffer2);
//    backBuffer.resetDamage();
//
//    terminal.characterAttributes(BLACK);
//    terminal.writeString("(x):");
//
//
//    assertEquals(colors, backBuffer.getStyleLines());
//
//    backBuffer.processDamagedCells(backBuffer2);
//
//    assertEquals(colors, backBuffer2.getStyleLines());
  }

  public void test24BitForegroundColourParsing() throws IOException {
    TerminalTextBuffer terminalTextBuffer = getBufferFor(12, 1, CSI + "38;2;0;128;0mHello");
    TextStyle style = terminalTextBuffer.getStyleAt(0, 0);
    assertEquals(new TerminalColor(0, 128, 0), style.getForeground());
  }

  public void test24BitBackgroundColourParsing() throws IOException {
    TerminalTextBuffer terminalTextBuffer = getBufferFor(12, 1, CSI + "48;2;0;128;0mHello");
    TextStyle style = terminalTextBuffer.getStyleAt(0, 0);
    assertEquals(new TerminalColor(0, 128, 0), style.getBackground());
  }

  public void test24BitCombinedColourParsing() throws IOException {
    TerminalTextBuffer terminalTextBuffer = getBufferFor(12, 1, CSI + "0;38;2;0;128;0;48;2;0;255;0;1mHello");
    TextStyle style = terminalTextBuffer.getStyleAt(0, 0);
    assertEquals(new TerminalColor(0, 128, 0), style.getForeground());
    assertEquals(new TerminalColor(0, 255, 0), style.getBackground());
    assertTrue(style.hasOption(TextStyle.Option.BOLD));
  }

  public void testIndexedForegroundColourParsing() throws IOException {
    TerminalTextBuffer terminalTextBuffer = getBufferFor(12, 1, CSI + "38;5;46mHello");
    TextStyle style = terminalTextBuffer.getStyleAt(0, 0);
    assertEquals(new TerminalColor(0, 255, 0), style.getForeground());
  }

  public void testIndexedBackgroundColourParsing() throws IOException {
    TerminalTextBuffer terminalTextBuffer = getBufferFor(12, 1, CSI + "48;5;46mHello");
    TextStyle style = terminalTextBuffer.getStyleAt(0, 0);
    assertEquals(new TerminalColor(0, 255, 0), style.getBackground());
  }

  public void testIndexedCombinedColourParsing() throws IOException {
    TerminalTextBuffer terminalTextBuffer = getBufferFor(12, 1, CSI + "0;38;5;46;48;5;196;1mHello");
    TextStyle style = terminalTextBuffer.getStyleAt(0, 0);
    assertEquals(new TerminalColor(0, 255, 0), style.getForeground());
    assertEquals(new TerminalColor(255, 0, 0), style.getBackground());
    assertTrue(style.hasOption(TextStyle.Option.BOLD));
  }

  public void testQueryKeyModifierNotChangingStyle() throws IOException {
    TestSession session = new TestSession(10, 3);
    TextStyle initialStyle = new TextStyle(null, null);
    session.process("foo\r\n");
    Assert.assertEquals(session.getCurrentStyle(), initialStyle);
    session.process("\u001B[?4m");
    Assert.assertEquals(session.getCurrentStyle(), initialStyle);
    session.process("bar\r\n");
    Assert.assertEquals(session.getCurrentStyle(), initialStyle);

    TextStyle boldStyle = new TextStyle(null, null, EnumSet.of(TextStyle.Option.BOLD));
    session.process("\u001B[1m");
    Assert.assertEquals(session.getCurrentStyle(), boldStyle);
    session.process("baz");
    Assert.assertEquals(session.getCurrentStyle(), boldStyle);

    TerminalTextBuffer textBuffer = session.getTerminalTextBuffer();
    Assert.assertEquals(initialStyle, textBuffer.getStyleAt(0, 0)); // foo
    Assert.assertEquals(initialStyle, textBuffer.getStyleAt(0, 1)); // bar
    Assert.assertEquals(boldStyle, textBuffer.getStyleAt(0, 2)); // baz
  }

  private @NotNull TerminalTextBuffer getBufferFor(int width, int height, String content) throws IOException {
    StyleState state = new StyleState();
    TerminalTextBuffer terminalTextBuffer = new TerminalTextBuffer(width, height, state);
    JediTerminal terminal = new JediTerminal(new BackBufferDisplay(terminalTextBuffer), terminalTextBuffer, state);
    Emulator emulator = new JediEmulator(new ArrayTerminalDataStream(content.toCharArray()), terminal);
    while (emulator.hasNext()) {
      emulator.next();
    }
    return terminalTextBuffer;
  }
}
