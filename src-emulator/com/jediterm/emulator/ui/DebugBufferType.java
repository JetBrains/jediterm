package com.jediterm.emulator.ui;

import com.jediterm.emulator.ControlSequenceVisualiser;
import com.jediterm.pty.PtyMain;

/**
 * @author traff
 */
public enum DebugBufferType {
  Back() {
    String getValue(SwingJediTerminal term) {
      return term.getTerminalPanel().getBackBuffer().getLines();
    }
  },
  BackStyle() {
    String getValue(SwingJediTerminal term) {
      return term.getTerminalPanel().getBackBuffer().getStyleLines();
    }
  },
  Damage() {
    String getValue(SwingJediTerminal term) {
      return term.getTerminalPanel().getBackBuffer().getDamageLines();
    }
  },
  Scroll() {
    String getValue(SwingJediTerminal term) {
      return term.getTerminalPanel().getScrollBuffer().getLines();
    }
  },
  Text() {
    String getValue(SwingJediTerminal term) {
      return term.getTerminalPanel().getBackBuffer().getTextBufferLines();
    }
  },

  ControlSequences() {
    private ControlSequenceVisualiser myVisualiser = new ControlSequenceVisualiser();

    String getValue(SwingJediTerminal term) {
      if (term.getTtyConnector() instanceof PtyMain.LoggingPtyProcessTtyConnector) {
        return myVisualiser.getVisualizedString(((PtyMain.LoggingPtyProcessTtyConnector)term.getTtyConnector()).getChunks());
      }
      else {
        return "Control sequences aren't logged";
      }
    }
  };


  abstract String getValue(SwingJediTerminal term);
}
