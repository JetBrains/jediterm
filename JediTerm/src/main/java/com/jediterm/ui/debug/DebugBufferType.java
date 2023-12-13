package com.jediterm.ui.debug;

import com.jediterm.terminal.LoggingTtyConnector;
import com.jediterm.terminal.LoggingTtyConnector.TerminalState;
import com.jediterm.terminal.ui.TerminalSession;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author traff
 */
public enum DebugBufferType {
  Screen() {
    public @NotNull String getValue(TerminalSession session, int stateIndex) {
      List<TerminalState> states = ((LoggingTtyConnector) session.getTtyConnector()).getStates();
      if (stateIndex == states.size()) {
        return session.getTerminalTextBuffer().getScreenLines();
      } else {
        return states.get(stateIndex).myScreenLines;
      }
    }
  },
  BackStyle() {
    public @NotNull String getValue(TerminalSession session, int stateIndex) {
      List<TerminalState> states = ((LoggingTtyConnector) session.getTtyConnector()).getStates();
      if (stateIndex == states.size()) {
        return TerminalDebugUtil.getStyleLines(session.getTerminalTextBuffer());
      } else {
        return states.get(stateIndex).myStyleLines;
      }
    }
  },
  History() {
    public @NotNull String getValue(TerminalSession session, int stateIndex) {
      List<TerminalState> states = ((LoggingTtyConnector) session.getTtyConnector()).getStates();
      if (stateIndex == states.size()) {
        return session.getTerminalTextBuffer().getHistoryBuffer().getLines();
      } else {
        return states.get(stateIndex).myHistoryLines;
      }
    }
  };

  public abstract @NotNull String getValue(TerminalSession session, int stateIndex);
}
