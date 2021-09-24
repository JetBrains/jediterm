package com.jediterm.util;

import com.jediterm.terminal.ArrayTerminalDataStream;
import com.jediterm.terminal.HyperlinkStyle;
import com.jediterm.terminal.TerminalColor;
import com.jediterm.terminal.TextStyle;
import com.jediterm.terminal.emulator.Emulator;
import com.jediterm.terminal.emulator.JediEmulator;
import com.jediterm.terminal.model.StyleState;
import com.jediterm.terminal.model.TerminalTextBuffer;
import com.jediterm.terminal.model.hyperlinks.TextProcessing;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.io.IOException;

public class TestSession {

  private final BackBufferTerminal myTerminal;
  private final TextProcessing myTextProcessing;
  private final TerminalTextBuffer myTerminalTextBuffer;
  private final TextStyle myDefaultStyle;

  public TestSession(int width, int height) {
    StyleState state = new StyleState();
    myDefaultStyle = state.getCurrent();
    TextStyle hyperlinkTextStyle = new TextStyle(TerminalColor.awt(Color.BLUE), TerminalColor.WHITE);
    myTextProcessing = new TextProcessing(hyperlinkTextStyle, HyperlinkStyle.HighlightMode.ALWAYS);
    myTerminalTextBuffer = new TerminalTextBuffer(width, height, state, myTextProcessing);
    myTextProcessing.setTerminalTextBuffer(myTerminalTextBuffer);
    myTerminal = new BackBufferTerminal(myTerminalTextBuffer, state);
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

  public @NotNull TextStyle getDefaultStyle() {
    return myDefaultStyle;
  }

  public void process(@NotNull String data) throws IOException {
    ArrayTerminalDataStream fileStream = new ArrayTerminalDataStream(data.toCharArray());
    Emulator emulator = new JediEmulator(fileStream, myTerminal);

    while (emulator.hasNext()) {
      emulator.next();
    }
  }
}
