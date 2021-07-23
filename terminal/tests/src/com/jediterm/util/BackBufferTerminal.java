package com.jediterm.util;

import com.jediterm.terminal.model.TerminalTextBuffer;
import com.jediterm.terminal.model.JediTerminal;
import com.jediterm.terminal.model.StyleState;
import org.jetbrains.annotations.NotNull;

/**
 * @author traff
 */
public class BackBufferTerminal extends JediTerminal {
  private final BackBufferDisplay myBufferDisplay;
  private final TerminalTextBuffer myTextBuffer;

  public BackBufferTerminal(TerminalTextBuffer terminalTextBuffer,
                            StyleState initialStyleState) {
    this(new BackBufferDisplay(terminalTextBuffer), terminalTextBuffer, initialStyleState);
  }

  private BackBufferTerminal(@NotNull BackBufferDisplay bufferDisplay,
                             @NotNull TerminalTextBuffer terminalTextBuffer,
                             @NotNull StyleState initialStyleState) {
    super(bufferDisplay, terminalTextBuffer, initialStyleState);
    myBufferDisplay = bufferDisplay;
    myTextBuffer = terminalTextBuffer;
  }

  public @NotNull BackBufferDisplay getDisplay() {
    return myBufferDisplay;
  }

  public @NotNull TerminalTextBuffer getTextBuffer() {
    return myTextBuffer;
  }
}
