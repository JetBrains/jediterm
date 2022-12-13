package com.jediterm;

import com.jediterm.terminal.TerminalColor;
import com.jediterm.terminal.TextStyle;
import com.jediterm.terminal.model.TerminalTextBuffer;
import com.jediterm.util.TestSession;
import junit.framework.TestCase;
import org.jetbrains.annotations.NotNull;
import org.junit.Ignore;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * @author traff
 */
@Ignore
public abstract class EmulatorTestAbstract extends TestCase {
  protected static void assertColor(TextStyle style, TerminalColor foreground, TerminalColor background) {
    assertEquals(foreground, style.getForeground());
    assertEquals(background, style.getBackground());
  }

  protected TerminalTextBuffer doTest() throws IOException {
    return doTest(80, 24);
  }

  protected TerminalTextBuffer doTest(int width, int height) throws IOException {
    String content = Files.readString(Path.of(getPathToTest() + ".after.txt"), StandardCharsets.UTF_8);
    // CRLF in test data files should be handled as LF
    return doTest(width, height, content.replaceAll("\r\n", "\n").replaceAll("\r", "\n"));
  }

  protected TerminalTextBuffer doTest(int width, int height, String expected) throws IOException {
    TestSession testSession = new TestSession(width, height);
    String text = Files.readString(Path.of(getPathToTest() + ".txt"), StandardCharsets.UTF_8);
    // LF in test data files should be handled as CRLF to emulate a tty stream
    String crlfText = text.replaceAll("\r?\n", "\r\n");
    testSession.process(crlfText);
    TerminalTextBuffer terminalTextBuffer = testSession.getTerminal().getTextBuffer();
    assertEquals(expected, terminalTextBuffer.getScreenLines());
    return terminalTextBuffer;
  }


  protected abstract @NotNull Path getPathToTest();
}
