package com.jediterm.terminal.debug;

import com.jediterm.terminal.LoggingTtyConnector;
import com.jediterm.terminal.ui.SwingJediTerminal;

/**
 * @author traff
 */
public enum DebugBufferType {
  Back() {
    public String getValue(SwingJediTerminal term) {
      return term.getTerminalPanel().getBackBuffer().getLines();
    }
  },
  BackStyle() {
    public String getValue(SwingJediTerminal term) {
      return term.getTerminalPanel().getBackBuffer().getStyleLines();
    }
  },
  Damage() {
    public String getValue(SwingJediTerminal term) {
      return term.getTerminalPanel().getBackBuffer().getDamageLines();
    }
  },
  Scroll() {
    public String getValue(SwingJediTerminal term) {
      return term.getTerminalPanel().getBackBuffer().getScrollBuffer().getLines();
    }
  },
  Text() {
    public String getValue(SwingJediTerminal term) {
      return term.getTerminalPanel().getBackBuffer().getTextBufferLines();
    }
  },

  ControlSequences() {
    private ControlSequenceVisualizer myVisualizer = new ControlSequenceVisualizer();

    public String getValue(SwingJediTerminal term) {
      if (term.getTtyConnector() instanceof LoggingTtyConnector) {
        return myVisualizer.getVisualizedString(((LoggingTtyConnector)term.getTtyConnector()).getChunks());
      }
      else {
        return "Control sequences aren't logged";
      }
    }
  };


  public abstract String getValue(SwingJediTerminal term);
}
