package com.jediterm.terminal;

import java.util.logging.Logger;

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
      // Skip resizing as it would require to resize parent container.
      // Other terminal emulators (iTerm2, Terminal.app, GNOME Terminal) ignore it too.
      terminal.clearScreen();
      terminal.resetScrollRegions();
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
  ReverseVideo,
  OriginMode {
    @Override
    public void setEnabled(Terminal terminal, boolean enabled) {
    }
  },
  AutoWrap {
    @Override
    public void setEnabled(Terminal terminal, boolean enabled) {
      //we do nothing just switching the mode
    }
  },
  AutoRepeatKeys,
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
        terminal.saveCursor();
      }
      else {
        terminal.restoreCursor();
      }
    }
  }, 
  CursorBlinking {
    @Override
    public void setEnabled(Terminal terminal, boolean enabled) {
      terminal.setBlinkingCursor(enabled);
    }
  }, 
  AllowWideColumn, 
  ReverseWrapAround, 
  AutoNewLine {
    @Override
    public void setEnabled(Terminal terminal, boolean enabled) {
      terminal.setAutoNewLine(enabled);
    }
  }, 
  KeyboardAction, 
  InsertMode,
  SendReceive,
  EightBitInput, //Interpret "meta" key, sets eighth bit. (enables the eightBitInput resource).
               // http://www.leonerd.org.uk/hacks/hints/xterm-8bit.html

  AltSendsEscape //See section Alt and Meta Keys in http://invisible-island.net/xterm/ctlseqs/ctlseqs.html
          {
            @Override
            public void setEnabled(Terminal terminal, boolean enabled) {
              terminal.setAltSendsEscape(enabled);
            }
          }
  ;

  private static final Logger LOG = Logger.getLogger(TerminalMode.class.getName());
  
  public void setEnabled(Terminal terminal, boolean enabled) {
    LOG.severe("Mode " + name() + " is not implemented, setting to " + enabled);
  }
}