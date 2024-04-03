package com.jediterm.terminal.ui;

import com.jediterm.terminal.TtyConnector;

public interface JediTerminalWidget {
  JediTermWidget createTerminalSession(TtyConnector ttyConnector);

  void addListener(TerminalWidgetListener listener);

  void removeListener(TerminalWidgetListener listener);
}
