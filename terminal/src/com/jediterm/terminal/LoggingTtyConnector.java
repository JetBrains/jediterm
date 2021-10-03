package com.jediterm.terminal;

import java.util.List;

/**
 * @author traff
 */
public interface LoggingTtyConnector {
  List<char[]> getChunks();

  List<TerminalState> getStates();

  int getLogStart();

  class TerminalState {
    public final String myScreenLines;
    public final String myStyleLines;
    public final String myHistoryLines;

    public TerminalState(String screenLines, String styleLines, String historyLines) {
      myScreenLines = screenLines;
      myStyleLines = styleLines;
      myHistoryLines = historyLines;
    }
  }
}
