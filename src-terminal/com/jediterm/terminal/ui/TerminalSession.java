package com.jediterm.terminal.ui;

import com.jediterm.terminal.TtyConnector;
import com.jediterm.terminal.debug.DebugBufferType;
import com.jediterm.terminal.display.BackBuffer;
import org.jetbrains.annotations.NotNull;

/**
 * @author traff
 */
public interface TerminalSession {
  void start();

  String getBufferText(DebugBufferType type);

  BackBuffer getBackBuffer();

  void redraw();

  TtyConnector getTtyConnector();

  String getSessionName();
}
