package com.jediterm.util;

import com.jediterm.core.Color;
import com.jediterm.terminal.ArrayTerminalDataStream;
import com.jediterm.terminal.HyperlinkStyle;
import com.jediterm.terminal.TerminalColor;
import com.jediterm.terminal.TextStyle;
import com.jediterm.terminal.emulator.Emulator;
import com.jediterm.terminal.emulator.JediEmulator;
import com.jediterm.terminal.model.StyleState;
import com.jediterm.terminal.model.TerminalTextBuffer;
import com.jediterm.terminal.model.hyperlinks.TextProcessing;
import junit.framework.TestCase;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public class TestSession {

  public static final Color BLUE = new Color(0, 0, 255);

  private final BackBufferTerminal myTerminal;
  private final TextProcessing myTextProcessing;
  private final TerminalTextBuffer myTerminalTextBuffer;
  private final StyleState myStyleState;

  public TestSession(int width, int height) {
    myStyleState = new StyleState();
    TextStyle hyperlinkTextStyle = new TextStyle(TerminalColor.fromColor(BLUE), TerminalColor.WHITE);
    myTextProcessing = new TextProcessing(hyperlinkTextStyle, HyperlinkStyle.HighlightMode.ALWAYS);
    myTerminalTextBuffer = new TerminalTextBuffer(width, height, myStyleState, myTextProcessing);
    myTextProcessing.setTerminalTextBuffer(myTerminalTextBuffer);
    myTerminal = new BackBufferTerminal(myTerminalTextBuffer, myStyleState);
  }

  public @NotNull BackBufferTerminal getTerminal() {
    return myTerminal;
  }

  public @NotNull BackBufferDisplay getDisplay() {
    return myTerminal.getDisplay();
  }

  public @NotNull TerminalTextBuffer getTerminalTextBuffer() {
    return myTerminalTextBuffer;
  }

  public @NotNull TextProcessing getTextProcessing() {
    return myTextProcessing;
  }

  public @NotNull TextStyle getCurrentStyle() {
    return myStyleState.getCurrent();
  }

  public void process(@NotNull String data) throws IOException {
    ArrayTerminalDataStream fileStream = new ArrayTerminalDataStream(data.toCharArray());
    Emulator emulator = new JediEmulator(fileStream, myTerminal);

    while (emulator.hasNext()) {
      emulator.next();
    }
  }

  public void assertCursorPosition(int expectedOneBasedCursorX, int expectedOneBasedCursorY) {
    TestCase.assertEquals(stringifyCursor(expectedOneBasedCursorX, expectedOneBasedCursorY),
      stringifyCursor(myTerminal.getCursorX(), myTerminal.getCursorY()));
  }

  private static @NotNull String stringifyCursor(int cursorX, int cursorY) {
    return "cursorX=" + cursorX + ", cursorY=" + cursorY;
  }
}
