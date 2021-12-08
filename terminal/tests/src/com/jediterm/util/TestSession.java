package com.jediterm.util;

import com.jediterm.core.ArrayTerminalDataStream;
import com.jediterm.core.awtCompat.Color;
import com.jediterm.core.HyperlinkStyle;
import com.jediterm.core.TerminalColor;
import com.jediterm.core.TextStyle;
import com.jediterm.core.emulator.Emulator;
import com.jediterm.core.emulator.JediEmulator;
import com.jediterm.core.model.StyleState;
import com.jediterm.core.model.TerminalTextBuffer;
import com.jediterm.core.model.hyperlinks.TextProcessing;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public class TestSession {

  private final BackBufferTerminal myTerminal;
  private final TextProcessing myTextProcessing;
  private final TerminalTextBuffer myTerminalTextBuffer;
  private final TextStyle myDefaultStyle;

  public TestSession(int width, int height) {
    StyleState state = new StyleState();
    myDefaultStyle = state.getCurrent();
    java.awt.Color blueColor = java.awt.Color.BLUE;
    TextStyle hyperlinkTextStyle = new TextStyle(TerminalColor.fromColor(new Color(blueColor.getRGB())), TerminalColor.WHITE);
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
