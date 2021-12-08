package com.jediterm.terminal.ui;

import com.jediterm.core.Terminal;
import com.jediterm.core.TtyConnector;
import com.jediterm.terminal.debug.DebugBufferType;
import com.jediterm.core.model.TerminalTextBuffer;

/**
 * @author traff
 */
public interface TerminalSession {
  void start();

  String getBufferText(DebugBufferType type, int stateIndex);

  TerminalTextBuffer getTerminalTextBuffer();

  Terminal getTerminal();

  TtyConnector getTtyConnector();

  String getSessionName();

  void close();
}
