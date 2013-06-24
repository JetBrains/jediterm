/**
 *
 */
package com.jediterm.terminal;

import java.awt.*;

public enum TerminalMode {
  Null,
  CursorKey {
    @Override
    public void setEnabled(Terminal terminal, boolean enabled) {
      terminal.setApplicationArrowKeys(enabled);
    }
  },
  ANSI,
  WideColumn {
    @Override
    public void setEnabled(Terminal terminal, boolean enabled) {
      Dimension d = enabled ? new Dimension(132, 24) : new Dimension(80, 24);

      terminal.resize(d, RequestOrigin.Remote);
      terminal.clearScreen();
      terminal.restoreCursor();
    }
  },
  CursorVisible {
    @Override
    public void setEnabled(Terminal terminal, boolean enabled) {
      terminal.setCursorVisible(enabled);
    }
  },
  AlternateBuffer {
    @Override
    public void setEnabled(Terminal terminal, boolean enabled) {
      terminal.useAlternateBuffer(enabled);
    }
  },
  SmoothScroll,
  ReverseScreen,
  RelativeOrigin,
  WrapAround,
  AutoRepeat,
  Interlace,
  Keypad {
    @Override
    public void setEnabled(Terminal terminal, boolean enabled) {
      terminal.setApplicationKeypad(enabled);
    }
  },
  StoreCursor {
    @Override
    public void setEnabled(Terminal terminal, boolean enabled) {
      if (enabled) {
        terminal.storeCursor();
      }
      else {
        terminal.restoreCursor();
      }
    }
  };


  public void setEnabled(Terminal terminal, boolean enabled) {
    //pass
  }
}