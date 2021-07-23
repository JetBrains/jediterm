package com.jediterm.util;

import com.jediterm.terminal.ArrayTerminalDataStream;
import com.jediterm.terminal.emulator.Emulator;
import com.jediterm.terminal.emulator.JediEmulator;
import com.jediterm.terminal.model.StyleState;
import com.jediterm.terminal.model.TerminalTextBuffer;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public class TestSession {

  private final BackBufferTerminal myTerminal;

  public TestSession(int width, int height) {
    StyleState state = new StyleState();
    TerminalTextBuffer terminalTextBuffer = new TerminalTextBuffer(width, height, state);
    myTerminal = new BackBufferTerminal(terminalTextBuffer, state);
  }

  public @NotNull BackBufferTerminal getTerminal() {
    return myTerminal;
  }

  public @NotNull BackBufferDisplay getDisplay() {
    return myTerminal.getDisplay();
  }

  public void process(@NotNull String data) throws IOException {
    ArrayTerminalDataStream fileStream = new ArrayTerminalDataStream(data.toCharArray());
    Emulator emulator = new JediEmulator(fileStream, myTerminal);

    while (emulator.hasNext()) {
      emulator.next();
    }
  }
}
