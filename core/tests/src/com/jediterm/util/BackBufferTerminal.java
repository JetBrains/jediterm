package com.jediterm.util;

import com.jediterm.terminal.TerminalOutputStream;
import com.jediterm.terminal.model.JediTerminal;
import com.jediterm.terminal.model.StyleState;
import com.jediterm.terminal.model.TerminalTextBuffer;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * @author traff
 */
public class BackBufferTerminal extends JediTerminal {
  private final BackBufferDisplay myBufferDisplay;
  private final TerminalTextBuffer myTextBuffer;
  private ByteArrayOutputStream myOutputStream;

  public BackBufferTerminal(TerminalTextBuffer terminalTextBuffer,
                            StyleState initialStyleState) {
    this(new BackBufferDisplay(terminalTextBuffer), terminalTextBuffer, initialStyleState);
    myOutputStream = new ByteArrayOutputStream();
    setTerminalOutput(new TestOutputStream(myOutputStream));
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

  public @NotNull String getOutputAndClear() {
    String output = myOutputStream.toString(StandardCharsets.UTF_8);
    myOutputStream.reset();
    return output;
  }

  private static class TestOutputStream implements TerminalOutputStream {
    private final OutputStream myOutputStream;

    public TestOutputStream(@NotNull OutputStream outputStream) {
      myOutputStream = outputStream;
    }

    @Override
    public void sendBytes(byte @NotNull [] response, boolean userInput) {
      try {
        myOutputStream.write(response);
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }

    @Override
    public void sendString(@NotNull String string, boolean userInput) {
      try {
        myOutputStream.write(string.getBytes(StandardCharsets.UTF_8));
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
  }
}
