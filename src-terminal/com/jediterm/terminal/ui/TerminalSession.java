package com.jediterm.terminal.ui;

import com.jediterm.terminal.Terminal;
import com.jediterm.terminal.TtyConnector;
import com.jediterm.terminal.debug.DebugBufferType;
import com.jediterm.terminal.display.BackBuffer;

/**
 * @author traff
 */
public interface TerminalSession {
  void start();

  String getBufferText(DebugBufferType type);

  BackBuffer getBackBuffer();

  Terminal getTerminal();

  void redraw();

  TtyConnector getTtyConnector();

  String getSessionName();
}
