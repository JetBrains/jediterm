package com.jediterm.terminal.debug;

import com.jediterm.terminal.LoggingTtyConnector;
import com.jediterm.terminal.ui.TerminalSession;

/**
 * @author traff
 */
public enum DebugBufferType {
  Back() {
    public String getValue(TerminalSession session) {
      return session.getBackBuffer().getLines();
    }
  },
  BackStyle() {
    public String getValue(TerminalSession session) {
      return session.getBackBuffer().getStyleLines();
    }
  },
  Damage() {
    public String getValue(TerminalSession session) {
      return session.getBackBuffer().getDamageLines();
    }
  },
  Scroll() {
    public String getValue(TerminalSession session) {
      return session.getBackBuffer().getScrollBuffer().getLines();
    }
  },
  Text() {
    public String getValue(TerminalSession session) {
      return session.getBackBuffer().getTextBufferLines();
    }
  },

  ControlSequences() {
    private ControlSequenceVisualizer myVisualizer = new ControlSequenceVisualizer();

    public String getValue(TerminalSession session) {
      if (session.getTtyConnector() instanceof LoggingTtyConnector) {
        return myVisualizer.getVisualizedString(((LoggingTtyConnector) session.getTtyConnector()).getChunks());
      } else {
        return "Control sequences aren't logged";
      }
    }
  };


  public abstract String getValue(TerminalSession session);
}
