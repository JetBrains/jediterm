package com.jediterm.terminal.debug;

import com.jediterm.terminal.LoggingTtyConnector;
import com.jediterm.terminal.LoggingTtyConnector.TerminalState;
import com.jediterm.terminal.ui.TerminalSession;

import java.util.List;

/**
 * @author traff
 */
public enum DebugBufferType {
  Back() {
    public String getValue(TerminalSession session, int stateIndex) {
      List<TerminalState> states = ((LoggingTtyConnector) session.getTtyConnector()).getStates();
      if (stateIndex == states.size()) {
        return session.getTerminalTextBuffer().getScreenLines();
      } else {
        return states.get(stateIndex).myScreenLines;
      }
    }
  },
  BackStyle() {
    public String getValue(TerminalSession session, int stateIndex) {
      List<TerminalState> states = ((LoggingTtyConnector) session.getTtyConnector()).getStates();
      if (stateIndex == states.size()) {
        return session.getTerminalTextBuffer().getStyleLines();
      } else {
        return states.get(stateIndex).myStyleLines;
      }
    }
  },
  Scroll() {
    public String getValue(TerminalSession session, int stateIndex) {
      List<TerminalState> states = ((LoggingTtyConnector) session.getTtyConnector()).getStates();
      if (stateIndex == states.size()) {
        return session.getTerminalTextBuffer().getHistoryBuffer().getLines();
      } else {
        return states.get(stateIndex).myHistoryLines;
      }
    }
  },

  ControlSequences() {
    private final ControlSequenceVisualizer myVisualizer = new ControlSequenceVisualizer();

    public String getValue(TerminalSession session, int stateIndex) {
      if (session.getTtyConnector() instanceof LoggingTtyConnector) {
        List<char[]> chunks = ((LoggingTtyConnector) session.getTtyConnector()).getChunks();
        return myVisualizer.getVisualizedString(chunks.subList(0, stateIndex));
      } else {
        return "Control sequences aren't logged";
      }
    }
  };


  public abstract String getValue(TerminalSession session, int stateIndex);
}
